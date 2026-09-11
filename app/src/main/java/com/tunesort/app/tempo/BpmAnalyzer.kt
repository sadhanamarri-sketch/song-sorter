package com.tunesort.app.tempo

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Basic on-device tempo estimate. Not studio-grade beat tracking — it's an
 * energy-flux onset envelope + autocorrelation, which is the standard
 * "good enough" approach for bucketing songs into slow/mid/fast without
 * a cloud API. Expect it to be right within ~±15% most of the time.
 */
object BpmAnalyzer {

    private const val ANALYZE_SECONDS = 60 // analyze up to first 60s, skip a short intro
    private const val SKIP_INTRO_SECONDS = 5
    private const val TARGET_SAMPLE_RATE = 11025 // downsample target, plenty for tempo

    fun estimateBpm(context: Context, uri: Uri): Double? {
        val pcm = decodeToMonoPcm(context, uri) ?: return null
        if (pcm.size < TARGET_SAMPLE_RATE * 5) return null // too short to analyze
        val envelope = onsetEnvelope(pcm)
        return autocorrelationBpm(envelope, framesPerSecond = TARGET_SAMPLE_RATE / HOP)
    }

    fun bucket(bpm: Double?): String = when {
        bpm == null -> "unknown"
        bpm < 90 -> "slow"
        bpm < 130 -> "mid"
        else -> "fast"
    }

    private const val HOP = 256 // samples per envelope frame at TARGET_SAMPLE_RATE

    /** Decodes audio to mono PCM16 at TARGET_SAMPLE_RATE, capped to ANALYZE_SECONDS. */
    private fun decodeToMonoPcm(context: Context, uri: Uri): ShortArray? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { pfdSafe ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(pfdSafe.fileDescriptor)
            } catch (e: Exception) {
                return null
            }
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { trackIndex = i; format = f; break }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val inSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            val maxOutSamples = TARGET_SAMPLE_RATE * (ANALYZE_SECONDS + SKIP_INTRO_SECONDS)
            val outBuffer = ShortArray(maxOutSamples)
            var outCount = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val downsampleStride = max(1, inSampleRate / TARGET_SAMPLE_RATE)

            while (!outputDone && outCount < maxOutSamples) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)!!
                    val shortBuf = outBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frameCount = bufferInfo.size / 2 / channelCount
                    var i = 0
                    while (i < frameCount && outCount < maxOutSamples) {
                        // downmix to mono
                        var sum = 0
                        for (c in 0 until channelCount) sum += shortBuf.get(i * channelCount + c)
                        val mono = (sum / channelCount).toShort()
                        // crude downsample by stride picking
                        if (i % downsampleStride == 0) {
                            outBuffer[outCount++] = mono
                        }
                        i++
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // ignore, we already read channel/sample rate from the input format
                }
            }
            codec.stop()
            codec.release()
            extractor.release()

            if (outCount <= 0) return null
            val skip = min(SKIP_INTRO_SECONDS * TARGET_SAMPLE_RATE, outCount / 4)
            return outBuffer.copyOfRange(skip, outCount)
        }
    }

    /** Simple energy-based onset strength envelope, one value per HOP-sample frame. */
    private fun onsetEnvelope(pcm: ShortArray): DoubleArray {
        val frameCount = pcm.size / HOP
        val energy = DoubleArray(frameCount)
        for (f in 0 until frameCount) {
            var e = 0.0
            val start = f * HOP
            for (i in start until min(start + HOP, pcm.size)) {
                val v = pcm[i] / 32768.0
                e += v * v
            }
            energy[f] = e
        }
        // positive spectral-ish flux: half-wave rectified difference
        val flux = DoubleArray(frameCount)
        for (f in 1 until frameCount) {
            flux[f] = max(0.0, energy[f] - energy[f - 1])
        }
        return flux
    }

    // A slower candidate lag "wins" over the raw peak if it retains at least this
    // fraction of the peak's autocorrelation score — see the subdivision comment below.
    private const val SLOWER_CANDIDATE_THRESHOLD = 0.6

    /** Autocorrelate the onset envelope over plausible tempo lags (60-200 BPM). */
    private fun autocorrelationBpm(envelope: DoubleArray, framesPerSecond: Int): Double? {
        if (envelope.size < framesPerSecond * 2) return null
        val minBpm = 60.0
        val maxBpm = 200.0
        val minLag = (60.0 / maxBpm * framesPerSecond).toInt().coerceAtLeast(1)
        val maxLag = (60.0 / minBpm * framesPerSecond).toInt().coerceAtMost(envelope.size - 1)
        if (minLag >= maxLag) return null

        fun scoreAtLag(lag: Int): Double {
            if (lag <= 0 || lag >= envelope.size) return -1.0
            var score = 0.0
            var n = 0
            var i = 0
            while (i + lag < envelope.size) {
                score += envelope[i] * envelope[i + lag]
                n++
                i++
            }
            return if (n > 0) score / n else -1.0
        }

        var bestLag = -1
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            val score = scoreAtLag(lag)
            if (score > bestScore) { bestScore = score; bestLag = lag }
        }
        if (bestLag <= 0) return null

        // Melodious/slow songs often autocorrelate most strongly at a fine rhythmic
        // subdivision (gentle strumming, vocal syllables) rather than the actual felt
        // beat, which reads as a falsely fast tempo. The true beat's own periodicity
        // is still there in that case — just at a weaker score — so if a slower
        // candidate (half, a third, or a quarter of the raw peak's BPM) is still
        // nearly as strong, it's almost always the real tempo; prefer the slowest
        // one that clears the bar.
        var lag = bestLag
        for (divisor in intArrayOf(2, 3, 4)) {
            val slowerLag = bestLag * divisor
            if (scoreAtLag(slowerLag) >= bestScore * SLOWER_CANDIDATE_THRESHOLD) {
                lag = slowerLag
            }
        }

        var bpm = 60.0 * framesPerSecond / lag
        // fold into a musically common 60-200 range (halve/double octave errors)
        while (bpm > 200) bpm /= 2
        while (bpm < 60) bpm *= 2
        return (bpm * 10).let { kotlin.math.round(it) / 10 }
    }
}
