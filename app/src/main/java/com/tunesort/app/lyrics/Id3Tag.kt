package com.tunesort.app.lyrics

import java.io.ByteArrayOutputStream

/**
 * Minimal, self-contained ID3v2.2/2.3/2.4 frame reader, plus a v2.3 writer that
 * preserves every frame it doesn't understand (title, artist, album art, etc.)
 * and only replaces/inserts the ones we care about (TCON genre, USLT lyrics).
 *
 * Deliberately does NOT try to be a full tag-library replacement. If a file
 * uses an ID3v2 extended header or ID3v2.2 (3-char frame ids), reading still
 * works for the frames we care about; writing falls back to "append a fresh
 * v2.3 tag with just TCON" rather than risk corrupting an unusual layout.
 */
object Id3Tag {

    data class RawFrame(val id: String, val bytes: ByteArray) // full frame incl. its own header

    data class ParsedTag(
        val version: Int,          // 2, 3, or 4
        val headerSize: Int,       // bytes ID3 header + body occupies at start of file
        val frames: List<RawFrame>
    )

    fun parse(bytes: ByteArray): ParsedTag? {
        if (bytes.size < 10 || bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return null
        }
        val version = bytes[3].toInt()
        val flags = bytes[5].toInt()
        val size = synchsafeToInt(bytes, 6)
        val headerSize = 10 + size
        if (headerSize > bytes.size) return null

        var pos = 10
        if (flags and 0x40 != 0) {
            // extended header present; skip it (size field format differs by version, handle both)
            val extSize = if (version == 4) synchsafeToInt(bytes, pos) else be32(bytes, pos)
            pos += if (extSize > 0) extSize else 4
        }

        val frames = mutableListOf<RawFrame>()
        val idLen = if (version == 2) 3 else 4
        while (pos + idLen + (if (version == 2) 3 else 6) <= headerSize) {
            val id = String(bytes, pos, idLen, Charsets.US_ASCII)
            if (id[0].code == 0) break // padding reached
            val frameSize: Int
            val frameHeaderLen: Int
            if (version == 2) {
                frameSize = be24(bytes, pos + 3)
                frameHeaderLen = 6
            } else {
                frameSize = if (version == 4) synchsafeToInt(bytes, pos + 4) else be32(bytes, pos + 4)
                frameHeaderLen = 10
            }
            val total = frameHeaderLen + frameSize
            if (frameSize < 0 || pos + total > headerSize) break
            frames.add(RawFrame(id, bytes.copyOfRange(pos, pos + total)))
            pos += total
        }
        return ParsedTag(version, headerSize, frames)
    }

    /**
     * Bytes occupied by a frame's own header before its body: id + size for v2.2
     * (3 + 3 = 6), or id + size + flags for v2.3/v2.4 (4 + 4 + 2 = 10).
     */
    private fun frameHeaderLen(idLen: Int) = if (idLen == 3) 6 else 10

    /** Decodes a text-information frame body (TCON, TIT2, TLAN, ...) honoring the encoding byte. */
    fun frameText(frame: RawFrame, idLen: Int = 4): String {
        val bodyStart = frameHeaderLen(idLen)
        if (frame.bytes.size <= bodyStart) return ""
        val encoding = frame.bytes[bodyStart].toInt()
        val textBytes = frame.bytes.copyOfRange(bodyStart + 1, frame.bytes.size)
        return decodeText(textBytes, encoding).trim('\u0000', ' ')
    }

    /** Decodes a USLT (unsynchronized lyrics) frame body: encoding + lang(3) + desc + text. */
    fun usltText(frame: RawFrame, idLen: Int = 4): String {
        var p = frameHeaderLen(idLen)
        if (frame.bytes.size <= p) return ""
        val encoding = frame.bytes[p].toInt(); p += 1
        p += 3 // language
        // skip null-terminated description (1 or 2-byte null depending on encoding)
        val wide = encoding == 1 || encoding == 2
        while (p < frame.bytes.size) {
            if (wide) {
                if (p + 1 < frame.bytes.size && frame.bytes[p] == 0.toByte() && frame.bytes[p + 1] == 0.toByte()) { p += 2; break }
                p += 2
            } else {
                if (frame.bytes[p] == 0.toByte()) { p += 1; break }
                p += 1
            }
        }
        if (p >= frame.bytes.size) return ""
        return decodeText(frame.bytes.copyOfRange(p, frame.bytes.size), encoding)
    }

    /** Reads the 3-letter ISO-639-2 language code a USLT frame carries in its own header. */
    fun usltLanguage(frame: RawFrame, idLen: Int = 4): String? {
        val langStart = frameHeaderLen(idLen) + 1 // skip the encoding byte
        if (frame.bytes.size < langStart + 3) return null
        return String(frame.bytes, langStart, 3, Charsets.US_ASCII)
    }

    private fun decodeText(bytes: ByteArray, encoding: Int): String = when (encoding) {
        0 -> String(bytes, Charsets.ISO_8859_1)
        1 -> String(bytes, Charsets.UTF_16)
        2 -> String(bytes, Charsets.UTF_16BE)
        3 -> String(bytes, Charsets.UTF_8)
        else -> String(bytes, Charsets.ISO_8859_1)
    }

    /**
     * Returns a full new file byte array with the genre (TCON) frame replaced/inserted,
     * preserving every other existing frame verbatim. Falls back to a bare new tag
     * if the original had no parseable ID3v2 tag.
     */
    fun withGenre(original: ByteArray, genre: String): ByteArray {
        val parsed = parse(original)
        val kept = parsed?.frames?.filter { it.id != "TCON" } ?: emptyList()
        val audioStart = parsed?.headerSize ?: 0

        val genreData = byteArrayOf(0) + genre.toByteArray(Charsets.ISO_8859_1) // encoding 0 = ISO-8859-1
        val tconFrame = buildV3Frame("TCON", genreData)

        val body = ByteArrayOutputStream()
        for (f in kept) body.write(f.bytes)
        body.write(tconFrame)
        val bodyBytes = body.toByteArray()

        val header = ByteArrayOutputStream()
        header.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        header.write(intToSynchsafe(bodyBytes.size))

        val out = ByteArrayOutputStream()
        out.write(header.toByteArray())
        out.write(bodyBytes)
        out.write(original, audioStart, original.size - audioStart)
        return out.toByteArray()
    }

    private fun buildV3Frame(id: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(intToBe32(data.size))
        out.write(byteArrayOf(0, 0)) // flags
        out.write(data)
        return out.toByteArray()
    }

    private fun synchsafeToInt(b: ByteArray, off: Int) =
        (b[off].toInt() and 0x7F shl 21) or (b[off + 1].toInt() and 0x7F shl 14) or
                (b[off + 2].toInt() and 0x7F shl 7) or (b[off + 3].toInt() and 0x7F)

    private fun intToSynchsafe(v: Int): ByteArray = byteArrayOf(
        ((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(),
        ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte()
    )

    private fun be32(b: ByteArray, off: Int) =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun be24(b: ByteArray, off: Int) =
        ((b[off].toInt() and 0xFF) shl 16) or ((b[off + 1].toInt() and 0xFF) shl 8) or (b[off + 2].toInt() and 0xFF)

    private fun intToBe32(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte()
    )
}
