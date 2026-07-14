package com.inspiremusic.data

import java.io.File
import java.io.RandomAccessFile

object EmbeddedLyricsExtractor {

    /**
     * 從音訊檔案中提取內嵌歌詞。
     * 支援 MP3 (ID3v2 USLT/ULT 幀) 以及 M4A/MP4 (©lyr Atom)。
     */
    fun extract(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val extension = file.extension.lowercase()
            if (extension == "mp3") {
                extractFromMp3(file)
            } else if (extension == "m4a" || extension == "mp4") {
                extractFromM4a(file)
            } else {
                extractFromMp3(file) ?: extractFromM4a(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractFromMp3(file: File): String? {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 10) return null
            val header = ByteArray(10)
            raf.readFully(header)
            
            // 檢查 ID3v2 標記
            if (header[0] != 'I'.toByte() || header[1] != 'D'.toByte() || header[2] != '3'.toByte()) {
                return null
            }
            val version = header[3].toInt()
            if (version < 2 || version > 4) return null

            // Synchsafe size of ID3 header (excluding 10-byte header itself)
            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                          ((header[7].toInt() and 0x7F) shl 14) or
                          ((header[8].toInt() and 0x7F) shl 7) or
                          (header[9].toInt() and 0x7F)

            val flags = header[5].toInt()
            var offset = 10L

            // 處理擴展標頭 (Extended Header)
            if (version >= 3 && (flags and 0x40) != 0) {
                if (version == 3) {
                    val extHeaderSize = raf.readInt()
                    raf.skipBytes(extHeaderSize)
                    offset += 4 + extHeaderSize
                } else if (version == 4) {
                    val extHeaderSizeBytes = ByteArray(4)
                    raf.readFully(extHeaderSizeBytes)
                    val extHeaderSize = ((extHeaderSizeBytes[0].toInt() and 0x7F) shl 21) or
                                        ((extHeaderSizeBytes[1].toInt() and 0x7F) shl 14) or
                                        ((extHeaderSizeBytes[2].toInt() and 0x7F) shl 7) or
                                        (extHeaderSizeBytes[3].toInt() and 0x7F)
                    raf.skipBytes(extHeaderSize - 4)
                    offset += extHeaderSize
                }
            }

            while (offset < tagSize + 10) {
                raf.seek(offset)
                if (version == 2) {
                    // ID3v2.2: 6 bytes header (3 bytes ID, 3 bytes size)
                    if (offset + 6 > tagSize + 10) break
                    val frameIdBytes = ByteArray(3)
                    raf.readFully(frameIdBytes)
                    val frameId = String(frameIdBytes, Charsets.US_ASCII)
                    
                    val frameSize = ((raf.read() and 0xFF) shl 16) or
                                    ((raf.read() and 0xFF) shl 8) or
                                    (raf.read() and 0xFF)
                    
                    offset += 6
                    if (frameId == "ULT") {
                        val data = ByteArray(frameSize)
                        raf.readFully(data)
                        return parseUSLTPayload(data)
                    } else {
                        if (frameId.isBlank() || frameSize <= 0) break
                        offset += frameSize
                    }
                } else {
                    // ID3v2.3 和 ID3v2.4: 10 bytes header (4 bytes ID, 4 bytes size, 2 bytes flags)
                    if (offset + 10 > tagSize + 10) break
                    val frameIdBytes = ByteArray(4)
                    raf.readFully(frameIdBytes)
                    val frameId = String(frameIdBytes, Charsets.US_ASCII)

                    val rawSize = raf.readInt()
                    val frameSize = if (version == 4) {
                        // ID3v2.4 幀大小為 synchsafe integer
                        ((rawSize shr 24 and 0x7F) shl 21) or
                        ((rawSize shr 16 and 0x7F) shl 14) or
                        ((rawSize shr 8 and 0x7F) shl 7) or
                        (rawSize and 0x7F)
                    } else {
                        // ID3v2.3 幀大小為標準 32 位整數
                        rawSize
                    }
                    raf.readShort() // 跳過 flags
                    offset += 10

                    if (frameId == "USLT") {
                        val data = ByteArray(frameSize)
                        raf.readFully(data)
                        return parseUSLTPayload(data)
                    } else {
                        if (frameId.isBlank() || frameSize <= 0) break
                        offset += frameSize
                    }
                }
            }
        }
        return null
    }

    private fun parseUSLTPayload(data: ByteArray): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt()
        
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }

        // 搜尋 Content Descriptor 的 Null 終止符 (從 index 4 開始)
        var descEnd = 4
        if (encoding == 1 || encoding == 2) {
            // UTF-16 用雙零位元組終止符，且需 2 載波對齊
            while (descEnd < data.size - 1) {
                if (data[descEnd] == 0.toByte() && data[descEnd + 1] == 0.toByte()) {
                    descEnd += 2
                    break
                }
                descEnd += 2
            }
        } else {
            // 單零位元組終止符
            while (descEnd < data.size) {
                if (data[descEnd] == 0.toByte()) {
                    descEnd += 1
                    break
                }
                descEnd++
            }
        }

        if (descEnd >= data.size) return null

        // 剩餘的部分即為歌詞內容
        return String(data, descEnd, data.size - descEnd, charset).trim()
    }

    private fun extractFromM4a(file: File): String? {
        RandomAccessFile(file, "r").use { raf ->
            return findM4aLyrics(raf, 0, raf.length())
        }
    }

    private fun findM4aLyrics(raf: RandomAccessFile, startOffset: Long, endOffset: Long): String? {
        var currentOffset = startOffset
        while (currentOffset < endOffset) {
            raf.seek(currentOffset)
            if (currentOffset + 8 > endOffset) break
            val size = raf.readInt().toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            // 使用 ISO_8859_1 確保 '\u00a9' 能被正確解碼成 '©'
            val type = String(typeBytes, Charsets.ISO_8859_1)

            var boxHeaderSize = 8L
            var boxDataSize = size - 8L
            if (size == 1L) {
                val largeSize = raf.readLong()
                boxHeaderSize = 16L
                boxDataSize = largeSize - 16L
            } else if (size == 0L) {
                boxDataSize = endOffset - currentOffset - boxHeaderSize
            }

            val dataOffset = currentOffset + boxHeaderSize

            if (type == "moov" || type == "udta" || type == "ilst" || type == "\u00a9lyr") {
                val result = findM4aLyrics(raf, dataOffset, dataOffset + boxDataSize)
                if (result != null) return result
            } else if (type == "meta") {
                // meta 是一個 FullBox，帶有 4 個位元組的 version & flags
                val result = findM4aLyrics(raf, dataOffset + 4, dataOffset + boxDataSize)
                if (result != null) return result
            } else if (type == "data") {
                // 在 ©lyr 底下會有一個 data box
                // data box 包裹著歌詞的 raw text，前 8 個位元組為 flags 欄位
                if (boxDataSize > 8) {
                    raf.seek(dataOffset + 8)
                    val textBytes = ByteArray((boxDataSize - 8).toInt())
                    raf.readFully(textBytes)
                    return String(textBytes, Charsets.UTF_8).trim()
                }
            }

            currentOffset += size
            if (size <= 0) break
        }
        return null
    }
}
