/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import de.infix.testBalloon.framework.core.testSuite
import java.util.Arrays
import okio.ByteString.Companion.of
import okio.TestUtil.SEGMENT_SIZE
import okio.TestUtil.deepCopy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail

val BufferCursorTest by testSuite {
  for (bufferFactory in BufferFactory.entries) {
    testSuite(bufferFactory.name) {
      test("apiExample") {
        val buffer = Buffer()
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.resizeBuffer(1000000)
          do {
            Arrays.fill(cursor.data, cursor.start, cursor.end, 'x'.code.toByte())
          } while (cursor.next() != -1)
          cursor.seek(3)
          cursor.data!![cursor.start] = 'o'.code.toByte()
          cursor.seek(1)
          cursor.data!![cursor.start] = 'o'.code.toByte()
          cursor.resizeBuffer(4)
        }
        assertEquals(Buffer().writeUtf8("xoxo"), buffer)
      }

      test("accessSegmentBySegment") {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
          val actual = Buffer()
          while (cursor.next().toLong() != -1L) {
            actual.write(cursor.data!!, cursor.start, cursor.end - cursor.start)
          }
          assertEquals(buffer, actual)
        }
      }

      test("seekToNegativeOneSeeksBeforeFirstSegment") {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
          cursor.seek(-1L)
          assertEquals(-1, cursor.offset)
          assertNull(cursor.data)
          assertEquals(-1, cursor.start.toLong())
          assertEquals(-1, cursor.end.toLong())
          cursor.next()
          assertEquals(0, cursor.offset)
        }
      }

      test("accessByteByByte") {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
          val actual = ByteArray(buffer.size.toInt())
          for (i in 0 until buffer.size) {
            cursor.seek(i)
            actual[i.toInt()] = cursor.data!![cursor.start]
          }
          assertEquals(of(*actual), buffer.snapshot())
        }
      }

      test("accessByteByByteReverse") {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
          val actual = ByteArray(buffer.size.toInt())
          for (i in (buffer.size - 1).toInt() downTo 0) {
            cursor.seek(i.toLong())
            actual[i] = cursor.data!![cursor.start]
          }
          assertEquals(of(*actual), buffer.snapshot())
        }
      }

      test("accessByteByByteAlwaysResettingToZero") {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
          val actual = ByteArray(buffer.size.toInt())
          for (i in 0 until buffer.size) {
            cursor.seek(i)
            actual[i.toInt()] = cursor.data!![cursor.start]
            cursor.seek(0L)
          }
          assertEquals(of(*actual), buffer.snapshot())
        }
      }

      test("segmentBySegmentNavigation") {
        val buffer = bufferFactory.newBuffer()
        val cursor = buffer.readUnsafe()
        assertEquals(-1, cursor.offset)
        cursor.use { cursor ->
          var lastOffset = cursor.offset
          while (cursor.next().toLong() != -1L) {
            Assertions.assertTrue(cursor.offset > lastOffset)
            lastOffset = cursor.offset
          }
          assertEquals(buffer.size, cursor.offset)
          assertNull(cursor.data)
          assertEquals(-1, cursor.start.toLong())
          assertEquals(-1, cursor.end.toLong())
        }
      }

      test("seekWithinSegment") {
        if (bufferFactory !== BufferFactory.SmallSegmentedBuffer) return@test
        val buffer = bufferFactory.newBuffer()
        assertEquals("abcdefghijkl", buffer.clone().readUtf8())
        buffer.readUnsafe().use { cursor ->
          assertEquals(2, cursor.seek(5).toLong()) // 2 for 2 bytes left in the segment: "fg".
          assertEquals(5, cursor.offset)
          assertEquals(2, (cursor.end - cursor.start).toLong())
          assertEquals(
            'd'.code.toLong(),
            Char(cursor.data!![cursor.start - 2].toUShort()).code.toLong(),
          ) // Out of bounds!
          assertEquals(
            'e'.code.toLong(),
            Char(cursor.data!![cursor.start - 1].toUShort()).code.toLong(),
          ) // Out of bounds!
          assertEquals('f'.code.toLong(), Char(cursor.data!![cursor.start].toUShort()).code.toLong())
          assertEquals('g'.code.toLong(), Char(cursor.data!![cursor.start + 1].toUShort()).code.toLong())
        }
      }

      test("acquireAndRelease") {
        val buffer = bufferFactory.newBuffer()
        val cursor = Buffer.UnsafeCursor()

        // Nothing initialized before acquire.
        assertEquals(-1, cursor.offset)
        assertNull(cursor.data)
        assertEquals(-1, cursor.start.toLong())
        assertEquals(-1, cursor.end.toLong())
        buffer.readUnsafe(cursor)
        cursor.close()

        // Nothing initialized after close.
        assertEquals(-1, cursor.offset)
        assertNull(cursor.data)
        assertEquals(-1, cursor.start.toLong())
        assertEquals(-1, cursor.end.toLong())
      }

      test("doubleAcquire") {
        val buffer = bufferFactory.newBuffer()
        try {
          buffer.readUnsafe().use { cursor ->
            buffer.readUnsafe(cursor)
            fail()
          }
        } catch (expected: IllegalStateException) {
        }
      }

      test("releaseWithoutAcquire") {
        val cursor = Buffer.UnsafeCursor()
        try {
          cursor.close()
          fail()
        } catch (expected: IllegalStateException) {
        }
      }

      test("releaseAfterRelease") {
        val buffer = bufferFactory.newBuffer()
        val cursor = buffer.readUnsafe()
        cursor.close()
        try {
          cursor.close()
          fail()
        } catch (expected: IllegalStateException) {
        }
      }

      test("enlarge") {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        val expected = deepCopy(buffer)
        expected.writeUtf8("abc")
        buffer.readAndWriteUnsafe().use { cursor ->
          assertEquals(originalSize, cursor.resizeBuffer(originalSize + 3))
          cursor.seek(originalSize)
          cursor.data!![cursor.start] = 'a'.code.toByte()
          cursor.seek(originalSize + 1)
          cursor.data!![cursor.start] = 'b'.code.toByte()
          cursor.seek(originalSize + 2)
          cursor.data!![cursor.start] = 'c'.code.toByte()
        }
        assertEquals(expected, buffer)
      }

      test("enlargeByManySegments") {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        val expected = deepCopy(buffer)
        expected.writeUtf8("x".repeat(1000000))
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.resizeBuffer(originalSize + 1000000)
          cursor.seek(originalSize)
          do {
            Arrays.fill(cursor.data, cursor.start, cursor.end, 'x'.code.toByte())
          } while (cursor.next() != -1)
        }
        assertEquals(expected, buffer)
      }

      test("resizeNotAcquired") {
        val cursor = Buffer.UnsafeCursor()
        try {
          cursor.resizeBuffer(10)
          fail()
        } catch (expected: IllegalStateException) {
        }
      }

      test("expandNotAcquired") {
        val cursor = Buffer.UnsafeCursor()
        try {
          cursor.expandBuffer(10)
          fail()
        } catch (expected: IllegalStateException) {
        }
      }

      test("resizeAcquiredReadOnly") {
        val buffer = bufferFactory.newBuffer()
        try {
          buffer.readUnsafe().use { cursor ->
            cursor.resizeBuffer(10)
            fail()
          }
        } catch (expected: IllegalStateException) {
        }
      }

      test("expandAcquiredReadOnly") {
        val buffer = bufferFactory.newBuffer()
        try {
          buffer.readUnsafe().use { cursor ->
            cursor.expandBuffer(10)
            fail()
          }
        } catch (expected: IllegalStateException) {
        }
      }

      test("shrink") {
        val buffer = bufferFactory.newBuffer()
        if (buffer.size <= 3) return@test
        val originalSize = buffer.size
        val expected = Buffer()
        deepCopy(buffer).copyTo(expected, 0, originalSize - 3)
        buffer.readAndWriteUnsafe().use { cursor ->
          assertEquals(originalSize, cursor.resizeBuffer(originalSize - 3))
        }
        assertEquals(expected, buffer)
      }

      test("shrinkByManySegments") {
        val buffer = bufferFactory.newBuffer()
        if (buffer.size > 1000000) return@test
        val originalSize = buffer.size
        val toShrink = Buffer()
        toShrink.writeUtf8("x".repeat(1000000))
        deepCopy(buffer).copyTo(toShrink, 0, originalSize)
        val cursor = Buffer.UnsafeCursor()
        toShrink.readAndWriteUnsafe(cursor)
        try {
          cursor.resizeBuffer(originalSize)
        } finally {
          cursor.close()
        }
        val expected = Buffer()
        expected.writeUtf8("x".repeat(originalSize.toInt()))
        assertEquals(expected, toShrink)
      }

      test("shrinkAdjustOffset") {
        val buffer = bufferFactory.newBuffer()
        if (!(buffer.size > 4)) return@test
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.seek(buffer.size - 1)
          cursor.resizeBuffer(3)
          assertEquals(3, cursor.offset)
          assertNull(cursor.data)
          assertEquals(-1, cursor.start.toLong())
          assertEquals(-1, cursor.end.toLong())
        }
      }

      test("resizeToSameSizeSeeksToEnd") {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.seek(buffer.size / 2)
          assertEquals(originalSize, buffer.size)
          cursor.resizeBuffer(originalSize)
          assertEquals(originalSize, buffer.size)
          assertEquals(originalSize, cursor.offset)
          assertNull(cursor.data)
          assertEquals(-1, cursor.start.toLong())
          assertEquals(-1, cursor.end.toLong())
        }
      }

      test("resizeEnlargeMovesCursorToOldSize") {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        val expected = deepCopy(buffer)
        expected.writeUtf8("a")
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.seek(buffer.size / 2)
          assertEquals(originalSize, buffer.size)
          cursor.resizeBuffer(originalSize + 1)
          assertEquals(originalSize, cursor.offset)
          assertNotNull(cursor.data)
          assertNotEquals(-1, cursor.start.toLong())
          assertEquals((cursor.start + 1).toLong(), cursor.end.toLong())
          cursor.data!![cursor.start] = 'a'.code.toByte()
        }
        assertEquals(expected, buffer)
      }

      test("resizeShrinkMovesCursorToEnd") {
        val buffer = bufferFactory.newBuffer()
        if (!(buffer.size > 0)) return@test
        val originalSize = buffer.size
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.seek(buffer.size / 2)
          assertEquals(originalSize, buffer.size)
          cursor.resizeBuffer(originalSize - 1)
          assertEquals(originalSize - 1, cursor.offset)
          assertNull(cursor.data)
          assertEquals(-1, cursor.start.toLong())
          assertEquals(-1, cursor.end.toLong())
        }
      }

      test("expand") {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        val expected = deepCopy(buffer)
        expected.writeUtf8("abcde")
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.expandBuffer(5)
          for (i in 0..4) {
            cursor.data!![cursor.start + i] = ('a'.code + i).toByte()
          }
          cursor.resizeBuffer(originalSize + 5)
        }
        assertEquals(expected, buffer)
      }

      test("expandSameSegment") {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        if (originalSize <= 0) return@test
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.seek(originalSize - 1)
          val originalEnd = cursor.end
          if (originalEnd >= SEGMENT_SIZE) return@test
          val addedByteCount = cursor.expandBuffer(1)
          assertEquals((SEGMENT_SIZE - originalEnd).toLong(), addedByteCount)
          assertEquals(originalSize + addedByteCount, buffer.size)
          assertEquals(originalSize, cursor.offset)
          assertEquals(originalEnd.toLong(), cursor.start.toLong())
          assertEquals(SEGMENT_SIZE.toLong(), cursor.end.toLong())
        }
      }

      test("expandNewSegment") {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        buffer.readAndWriteUnsafe().use { cursor ->
          val addedByteCount = cursor.expandBuffer(SEGMENT_SIZE)
          assertEquals(SEGMENT_SIZE.toLong(), addedByteCount)
          assertEquals(originalSize, cursor.offset)
          assertEquals(0, cursor.start.toLong())
          assertEquals(SEGMENT_SIZE.toLong(), cursor.end.toLong())
        }
      }

      test("expandMovesOffsetToOldSize") {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.seek(buffer.size / 2)
          assertEquals(originalSize, buffer.size)
          val addedByteCount = cursor.expandBuffer(5)
          assertEquals(originalSize + addedByteCount, buffer.size)
          assertEquals(originalSize, cursor.offset)
        }
      }
    }
  }
}
