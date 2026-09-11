package com.tunesort.app.tempo

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.max
import kotlin.math.min

/**
 * Basic on-device tempo estimate. Not studio-grade beat tracking — it's a
 * high-pass-filtered onset envelope + autocorrelation, sampled from several
 * points across the track and combined by confidence-weighted median. This is
 * the standard "good enough" approach for bucketing songs into slow/mid/fast
 * without a cloud API — expect it to land close, not frame-accurate.
 */
object BpmAnalyzer {

    private const val SKIP_INTRO_SECONDS = 5
    private const val TARGET_SAMPLE_RATE = 11025 // downsample target, plenty for tempo
    private const val WINDOW_SECONDS = 20 // length of each analyzed region

    private data class TempoEstimate(val bpm: Double, val confidence: Double)

    /**
     * Estimates BPM from up to three ~20s regions spread across the track (just past
     * the intro, ~40%, and ~70% of the way through) rather than only the first minute —
     * a song's real groove often isn't established until the chorus. Each region's
     * estimate carries a confidence score (how sharply its autocorrelation peak stood
     * out), and the final result is the confidence-weighted median across regions, so a
     * murky/ambiguous region doesn't outvote a clear one.
     */
    fun estimateBpm(context: Context, uri: Uri): Double? {
        val durationUs = probeDurationUs(context, uri)
        val windowSamples = TARGET_SAMPLE_RATE * WINDOW_SECONDS
        val estimates = mutableListOf<Pair<Double, Double>>() // (bpm, confidence)

        for (startUs in regionStartsUs(durationUs)) {
            val pcm = decodeRegion(context, uri, startUs, windowSamples) ?: continue
            if (pcm.size < TARGET_SAMPLE_RATE * 5) continue // too short a region to trust
            val envelope = onsetEnvelope(pcm)
            val estimate = autocorrelationBpm(envelope, framesPerSecond = TARGET_SAMPLE_RATE / HOP) ?: continue
            estimates.add(estimate.bpm to estimate.confidence)
        }

        if (estimates.isEmpty()) return null
        return weightedMedian(estimates)
    }

    fun bucket(bpm: Double?): String = when {
        bpm == null -> "unknown"
        bpm < 90 -> "slow"
        bpm < 130 -> "mid"
        else -> "fast"
    }

    private const val HOP = 256 // samples per envelope frame at TARGET_SAMPLE_RATE

    /** Reads just the audio track's duration (no decoding), to pick region start times. */
    private fun probeDurationUs(context: Context, uri: Uri): Long? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { pfdSafe ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(pfdSafe.fileDescriptor)
            } catch (e: Exception) {
                return null
            }
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    return if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else null
                }
            }
            return null
        }
    }

    /** Just past the intro, ~40%, and ~70% through the track — clamped so each region still fits. */
    private fun regionStartsUs(durationUs: Long?): List<Long> {
        val introSkipUs = SKIP_INTRO_SECONDS * 1_000_000L
        if (durationUs == null || durationUs <= 0) return listOf(introSkipUs)

        val windowUs = WINDOW_SECONDS * 1_000_000L
        val latestStart = (durationUs - windowUs).coerceAtLeast(0)
        return listOf(introSkipUs, (durationUs * 0.4).toLong(), (durationUs * 0.7).toLong())
            .map { it.coerceIn(0, latestStart) }
            .distinct()
    }

    /** Decodes mono PCM16 at TARGET_SAMPLE_RATE starting at [startUs], up to [targetSamples]. */
    private fun decodeRegion(context: Context, uri: Uri, startUs: Long, targetSamples: Int): ShortArray? {
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
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val inSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            val outBuffer = ShortArray(targetSamples)
            var outCount = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val downsampleStride = max(1, inSampleRate / TARGET_SAMPLE_RATE)

            while (!outputDone && outCount < targetSamples) {
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
                    while (i < frameCount && outCount < targetSamples) {
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
            return outBuffer.copyOfRange(0, outCount)
        }
    }

    /**
     * One-pole high-pass filter: y[n] = alpha*(y[n-1] + x[n] - x[n-1]). Strips out
     * slow-moving content (sustained bass, pads, held vocal notes) so the energy
     * computed from it responds to sharp attacks instead of overall loudness — the
     * difference between "this frame is loud" and "something just hit."
     */
    private fun highPass(pcm: ShortArray, alpha: Double = 0.97): DoubleArray {
        val out = DoubleArray(pcm.size)
        var prevIn = 0.0
        var prevOut = 0.0
        for (i in pcm.indices) {
            val x = pcm[i] / 32768.0
            val y = alpha * (prevOut + x - prevIn)
            out[i] = y
            prevIn = x
            prevOut = y
        }
        return out
    }

    /** Onset strength envelope, one value per HOP-sample frame, from the high-passed signal. */
    private fun onsetEnvelope(pcm: ShortArray): DoubleArray {
        val filtered = highPass(pcm)
        val frameCount = filtered.size / HOP
        val energy = DoubleArray(frameCount)
        for (f in 0 until frameCount) {
            var e = 0.0
            val start = f * HOP
            for (i in start until min(start + HOP, filtered.size)) {
                val v = filtered[i]
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
    private fun autocorrelationBpm(envelope: DoubleArray, framesPerSecond: Int): TempoEstimate? {
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
        var scoreSum = 0.0
        var scoreCount = 0
        for (lag in minLag..maxLag) {
            val score = scoreAtLag(lag)
            scoreSum += score
            scoreCount++
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

        // How far the winning periodicity stands out above the average correlation
        // across the whole search range: a sharp, confident peak vs. a flat, noisy one.
        val meanScore = if (scoreCount > 0) scoreSum / scoreCount else 0.0
        val confidence = if (meanScore > 0) ((bestScore - meanScore) / meanScore).coerceAtLeast(0.0) else 0.0

        return TempoEstimate((bpm * 10).let { kotlin.math.round(it) / 10 }, confidence)
    }

    /** Median of (bpm, confidence) readings weighted by confidence, so a clear region
     *  outweighs a murky one instead of every region counting equally. */
    private fun weightedMedian(readings: List<Pair<Double, Double>>): Double {
        val sorted = readings.sortedBy { it.first }
        val totalWeight = sorted.sumOf { it.second }
        if (totalWeight <= 0.0) return sorted[sorted.size / 2].first // no region stood out — plain median position

        var cumulative = 0.0
        for ((bpm, weight) in sorted) {
            cumulative += weight
            if (cumulative >= totalWeight / 2.0) return bpm
        }
        return sorted.last().first
    }
}
