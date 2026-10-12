package com.stormpanda.megingiard.catalog

import com.stormpanda.megingiard.AppLog

private const val TAG = "IcoImageExtractor"
private const val ICO_HEADER_SIZE = 6
private const val ICO_ENTRY_SIZE = 16
private const val ICO_TYPE_ICON = 1
private const val MAX_DIMENSION_RAW_VALUE = 0
private const val MAX_DIMENSION_RESOLVED_PX = 256
private const val PNG_HEADER_SIZE = 8

/**
 * Utility for parsing and extracting modern embedded image streams (specifically 256x256 PNG icons)
 * from Windows ICO containers commonly returned by external artwork sources like SteamGridDB.
 */
object IcoImageExtractor {
    private val PNG_MAGIC =
        byteArrayOf(
            0x89.toByte(),
            0x50.toByte(),
            0x4E.toByte(),
            0x47.toByte(),
            0x0D.toByte(),
            0x0A.toByte(),
            0x1A.toByte(),
            0x0A.toByte(),
        )

    /**
     * Checks whether [bytes] begins with the standard Windows ICO header (0x00, 0x00, 0x01, 0x00).
     */
    fun isIco(bytes: ByteArray): Boolean {
        if (bytes.size < ICO_HEADER_SIZE) return false
        val reserved = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        val type = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
        return reserved == 0 && type == ICO_TYPE_ICON
    }

    /**
     * Inspects [bytes] as a Windows ICO container, evaluates all embedded directory entries,
     * and extracts the byte payload of the highest-resolution entry (prioritizing modern PNG streams).
     *
     * @return the extracted image byte array (e.g. valid PNG payload), or null if parsing fails.
     */
    fun extractBestImage(bytes: ByteArray): ByteArray? {
        if (!isIco(bytes)) return null

        val count = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)
        if (count <= 0 || bytes.size < ICO_HEADER_SIZE + count * ICO_ENTRY_SIZE) {
            AppLog.w(TAG, "extractBestImage: invalid icon count $count for ${bytes.size} bytes")
            return null
        }

        var bestOffset = -1
        var bestSize = -1
        var bestDimension = -1
        var bestIsPng = false
        var bestBpp = -1

        for (i in 0 until count) {
            val entryOffset = ICO_HEADER_SIZE + i * ICO_ENTRY_SIZE
            val rawW = bytes[entryOffset].toInt() and 0xFF
            val width = if (rawW == MAX_DIMENSION_RAW_VALUE) MAX_DIMENSION_RESOLVED_PX else rawW
            val rawH = bytes[entryOffset + 1].toInt() and 0xFF
            val height = if (rawH == MAX_DIMENSION_RAW_VALUE) MAX_DIMENSION_RESOLVED_PX else rawH
            val bpp = (bytes[entryOffset + 6].toInt() and 0xFF) or ((bytes[entryOffset + 7].toInt() and 0xFF) shl 8)

            val size =
                (bytes[entryOffset + 8].toInt() and 0xFF) or
                    ((bytes[entryOffset + 9].toInt() and 0xFF) shl 8) or
                    ((bytes[entryOffset + 10].toInt() and 0xFF) shl 16) or
                    ((bytes[entryOffset + 11].toInt() and 0xFF) shl 24)

            val offset =
                (bytes[entryOffset + 12].toInt() and 0xFF) or
                    ((bytes[entryOffset + 13].toInt() and 0xFF) shl 8) or
                    ((bytes[entryOffset + 14].toInt() and 0xFF) shl 16) or
                    ((bytes[entryOffset + 15].toInt() and 0xFF) shl 24)

            if (offset > 0 && size > 0 && offset.toLong() + size.toLong() <= bytes.size.toLong()) {
                val isPng = isPngHeaderAt(bytes, offset, size)
                val dimension = width * height

                val isBetter =
                    when {
                        isPng && !bestIsPng -> true
                        !isPng && bestIsPng -> false
                        dimension > bestDimension -> true
                        dimension == bestDimension && bpp > bestBpp -> true
                        dimension == bestDimension && bpp == bestBpp && size > bestSize -> true
                        else -> false
                    }

                if (isBetter) {
                    bestOffset = offset
                    bestSize = size
                    bestDimension = dimension
                    bestIsPng = isPng
                    bestBpp = bpp
                }
            }
        }

        if (bestOffset >= 0 && bestSize > 0) {
            AppLog.i(
                TAG,
                "Extracted best icon stream from ICO: offset=$bestOffset, size=$bestSize, dim=$bestDimension, isPng=$bestIsPng",
            )
            return bytes.copyOfRange(bestOffset, bestOffset + bestSize)
        }

        return null
    }

    private fun isPngHeaderAt(
        bytes: ByteArray,
        offset: Int,
        size: Int,
    ): Boolean {
        if (size < PNG_HEADER_SIZE || offset + PNG_HEADER_SIZE > bytes.size) return false
        for (i in 0 until PNG_HEADER_SIZE) {
            if (bytes[offset + i] != PNG_MAGIC[i]) return false
        }
        return true
    }
}
