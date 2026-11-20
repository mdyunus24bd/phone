package com.example.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PureM4aContainerWriterTest {

    @Test
    fun emptyRecordingDeletesFileWithoutError() {
        val tempFile = File.createTempFile("voxline_empty_", ".m4a")
        try {
            val writer = PureM4aContainerWriter(tempFile, sampleRate = 16_000, bitRate = 64_000)
            writer.finishAndClose(null)
            assertFalse("Empty recording should be deleted on close", tempFile.exists())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun abortDeletesFileCleanly() {
        val tempFile = File.createTempFile("voxline_abort_", ".m4a")
        try {
            val writer = PureM4aContainerWriter(tempFile, sampleRate = 16_000, bitRate = 64_000)
            writer.writeAacFrame(ByteArray(42) { 0x11 })
            writer.abortAndDelete()
            assertFalse("Aborted recording should be deleted", tempFile.exists())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun multiChunkM4aHasValidTopLevelAndStblStructure() {
        val tempFile = File.createTempFile("voxline_valid_", ".m4a")
        try {
            val writer = PureM4aContainerWriter(tempFile, sampleRate = 16_000, bitRate = 64_000)
            val totalFrames = 100
            var expectedPayloadBytes = 0
            for (i in 0 until totalFrames) {
                val frame = ByteArray(32 + (i % 17)) { (i and 0xFF).toByte() }
                expectedPayloadBytes += frame.size
                writer.writeAacFrame(frame)
            }
            writer.finishAndClose(byteArrayOf(0x14, 0x08))

            assertTrue("Finalized .m4a file must exist", tempFile.exists())
            val bytes = tempFile.readBytes()
            val topBoxes = parseBoxes(bytes, 0, bytes.size)
            assertEquals(listOf("ftyp", "mdat", "moov"), topBoxes.map { it.type })

            val ftyp = topBoxes[0]
            val mdat = topBoxes[1]
            val moov = topBoxes[2]
            assertEquals(expectedPayloadBytes + 8, mdat.size)
            assertEquals(bytes.size, ftyp.size + mdat.size + moov.size)

            val moovChildren = parseBoxes(bytes, moov.dataOffset, moov.endOffset)
            assertEquals(listOf("mvhd", "trak"), moovChildren.map { it.type })

            val trak = moovChildren[1]
            val trakChildren = parseBoxes(bytes, trak.dataOffset, trak.endOffset)
            assertEquals(listOf("tkhd", "mdia"), trakChildren.map { it.type })

            val mdia = trakChildren[1]
            val mdiaChildren = parseBoxes(bytes, mdia.dataOffset, mdia.endOffset)
            assertEquals(listOf("mdhd", "hdlr", "minf"), mdiaChildren.map { it.type })

            val minf = mdiaChildren[2]
            val minfChildren = parseBoxes(bytes, minf.dataOffset, minf.endOffset)
            assertEquals(listOf("smhd", "dinf", "stbl"), minfChildren.map { it.type })

            val stbl = minfChildren[2]
            val stblChildren = parseBoxes(bytes, stbl.dataOffset, stbl.endOffset)
            assertEquals(listOf("stsd", "stts", "stsc", "stsz", "stco"), stblChildren.map { it.type })

            // Verify stco has 2 chunks (64 + 36) starting at mdat data offset
            val stco = stblChildren[4]
            val stcoBuf = ByteBuffer.wrap(bytes, stco.dataOffset, stco.size - 8).order(ByteOrder.BIG_ENDIAN)
            assertEquals(0, stcoBuf.int) // version + flags
            assertEquals(2, stcoBuf.int) // entry_count
            assertEquals(mdat.dataOffset, stcoBuf.int) // chunk 1 starts at mdat payload
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun fallbackAudioSpecificConfigMatchesIsoSpec() {
        val asc16k = PureM4aContainerWriter.buildFallbackAudioSpecificConfig(16_000, 1)
        assertEquals(0x14.toByte(), asc16k[0])
        assertEquals(0x08.toByte(), asc16k[1])

        val asc44k = PureM4aContainerWriter.buildFallbackAudioSpecificConfig(44_100, 1)
        assertEquals(0x12.toByte(), asc44k[0])
        assertEquals(0x08.toByte(), asc44k[1])

        val asc48k = PureM4aContainerWriter.buildFallbackAudioSpecificConfig(48_000, 1)
        assertEquals(0x11.toByte(), asc48k[0])
        assertEquals(0x88.toByte(), asc48k[1])
    }

    private data class Mp4Box(
        val type: String,
        val offset: Int,
        val size: Int
    ) {
        val dataOffset: Int get() = offset + 8
        val endOffset: Int get() = offset + size
    }

    private fun parseBoxes(bytes: ByteArray, start: Int, end: Int): List<Mp4Box> {
        val result = mutableListOf<Mp4Box>()
        var cursor = start
        while (cursor + 8 <= end) {
            val size = ByteBuffer.wrap(bytes, cursor, 4).order(ByteOrder.BIG_ENDIAN).int
            assertTrue("Box size must be >= 8 at offset $cursor (got $size)", size >= 8)
            assertTrue("Box must fit inside parent bounds", cursor + size <= end)
            val type = String(bytes, cursor + 4, 4, Charsets.US_ASCII)
            result.add(Mp4Box(type, cursor, size))
            cursor += size
        }
        assertEquals("Parent box must be fully consumed without trailing bytes", end, cursor)
        return result
    }
}
