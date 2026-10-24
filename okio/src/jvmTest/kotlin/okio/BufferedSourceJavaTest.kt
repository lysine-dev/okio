/*
 * Copyright (C) 2014 Square, Inc.
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

import java.io.EOFException
import java.io.IOException
import kotlin.text.Charsets.UTF_8
import okio.TestUtil.SEGMENT_SIZE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * Tests solely for the behavior of RealBufferedSource's implementation. For generic
 * BufferedSource behavior use BufferedSourceTest.
 */
class BufferedSourceJavaTest {
  @Test
  fun inputStreamTracksSegments() {
    val source = Buffer()
    source.writeUtf8("a")
    source.writeUtf8("b".repeat(SEGMENT_SIZE))
    source.writeUtf8("c")
    val `in` = (source as Source).buffer().inputStream()
    assertEquals(0, `in`.available().toLong())
    assertEquals((SEGMENT_SIZE + 2).toLong(), source.size)

    // Reading one byte buffers a full segment.
    assertEquals('a'.code.toLong(), `in`.read().toLong())
    assertEquals((SEGMENT_SIZE - 1).toLong(), `in`.available().toLong())
    assertEquals(2, source.size)

    // Reading as much as possible reads the rest of that buffered segment.
    val data = ByteArray(SEGMENT_SIZE * 2)
    assertEquals((SEGMENT_SIZE - 1).toLong(), `in`.read(data, 0, data.size).toLong())
    assertEquals("b".repeat(SEGMENT_SIZE - 1), String(data, 0, SEGMENT_SIZE - 1, UTF_8))
    assertEquals(2, source.size)

    // Continuing to read buffers the next segment.
    assertEquals('b'.code.toLong(), `in`.read().toLong())
    assertEquals(1, `in`.available().toLong())
    assertEquals(0, source.size)

    // Continuing to read reads from the buffer.
    assertEquals('c'.code.toLong(), `in`.read().toLong())
    assertEquals(0, `in`.available().toLong())
    assertEquals(0, source.size)

    // Once we've exhausted the source, we're done.
    assertEquals(-1, `in`.read().toLong())
    assertEquals(0, source.size)
  }

  @Test
  fun inputStreamCloses() {
    val source = (Buffer() as Source).buffer()
    val inputStream = source.inputStream()
    inputStream.close()
    try {
      source.require(1)
      fail()
    } catch (e: IllegalStateException) {
      assertEquals("closed", e.message)
    }
  }

  @Test
  fun indexOfStopsReadingAtLimit() {
    val buffer = Buffer().writeUtf8("abcdef")
    val bufferedSource = object : ForwardingSource(buffer) {
      override fun read(sink: Buffer, byteCount: Long): Long {
        return super.read(sink, Math.min(1, byteCount))
      }
    }.buffer()
    assertEquals(6, buffer.size)
    assertEquals(-1, bufferedSource.indexOf('e'.code.toByte(), 0, 4))
    assertEquals(2, buffer.size)
  }

  @Test
  fun requireTracksBufferFirst() {
    val source = Buffer()
    source.writeUtf8("bb")
    val bufferedSource = (source as Source).buffer()
    bufferedSource.buffer.writeUtf8("aa")
    bufferedSource.require(2)
    assertEquals(2, bufferedSource.buffer.size)
    assertEquals(2, source.size)
  }

  @Test
  fun requireIncludesBufferBytes() {
    val source = Buffer()
    source.writeUtf8("b")
    val bufferedSource = (source as Source).buffer()
    bufferedSource.buffer.writeUtf8("a")
    bufferedSource.require(2)
    assertEquals("ab", bufferedSource.buffer.readUtf8(2))
  }

  @Test
  fun requireInsufficientData() {
    val source = Buffer()
    source.writeUtf8("a")
    val bufferedSource = (source as Source).buffer()
    try {
      bufferedSource.require(2)
      fail()
    } catch (expected: EOFException) {
    }
  }

  @Test
  fun requireReadsOneSegmentAtATime() {
    val source = Buffer()
    source.writeUtf8("a".repeat(SEGMENT_SIZE))
    source.writeUtf8("b".repeat(SEGMENT_SIZE))
    val bufferedSource = (source as Source).buffer()
    bufferedSource.require(2)
    assertEquals(SEGMENT_SIZE.toLong(), source.size)
    assertEquals(SEGMENT_SIZE.toLong(), bufferedSource.buffer.size)
  }

  @Test
  fun skipReadsOneSegmentAtATime() {
    val source = Buffer()
    source.writeUtf8("a".repeat(SEGMENT_SIZE))
    source.writeUtf8("b".repeat(SEGMENT_SIZE))
    val bufferedSource = (source as Source).buffer()
    bufferedSource.skip(2)
    assertEquals(SEGMENT_SIZE.toLong(), source.size)
    assertEquals((SEGMENT_SIZE - 2).toLong(), bufferedSource.buffer.size)
  }

  @Test
  fun skipTracksBufferFirst() {
    val source = Buffer()
    source.writeUtf8("bb")
    val bufferedSource = (source as Source).buffer()
    bufferedSource.buffer.writeUtf8("aa")
    bufferedSource.skip(2)
    assertEquals(0, bufferedSource.buffer.size)
    assertEquals(2, source.size)
  }

  @Test
  fun operationsAfterClose() {
    val source = Buffer()
    val bufferedSource = (source as Source).buffer()
    bufferedSource.close()

    // Test a sample set of methods.
    try {
      bufferedSource.indexOf(1.toByte())
      fail()
    } catch (expected: IllegalStateException) {
    }
    try {
      bufferedSource.skip(1)
      fail()
    } catch (expected: IllegalStateException) {
    }
    try {
      bufferedSource.readByte()
      fail()
    } catch (expected: IllegalStateException) {
    }
    try {
      bufferedSource.readByteString(10)
      fail()
    } catch (expected: IllegalStateException) {
    }

    // Test a sample set of methods on the InputStream.
    val inputStream = bufferedSource.inputStream()
    try {
      inputStream.read()
      fail()
    } catch (expected: IOException) {
    }
    try {
      inputStream.read(ByteArray(10))
      fail()
    } catch (expected: IOException) {
    }
  }

  /**
   * We don't want readAll to buffer an unbounded amount of data. Instead it
   * should buffer a segment, write it, and repeat.
   */
  @Test
  fun readAllReadsOneSegmentAtATime() {
    val write1 = Buffer().writeUtf8("a".repeat(SEGMENT_SIZE))
    val write2 = Buffer().writeUtf8("b".repeat(SEGMENT_SIZE))
    val write3 = Buffer().writeUtf8("c".repeat(SEGMENT_SIZE))
    val source = Buffer().writeUtf8(
      "" +
        "a".repeat(SEGMENT_SIZE) +
        "b".repeat(SEGMENT_SIZE) +
        "c".repeat(SEGMENT_SIZE),
    )
    val mockSink = MockSink()
    val bufferedSource = (source as Source).buffer()
    assertEquals((SEGMENT_SIZE * 3).toLong(), bufferedSource.readAll(mockSink))
    mockSink.assertLog(
      "write(" + write1 + ", " + write1.size + ")",
      "write(" + write2 + ", " + write2.size + ")",
      "write(" + write3 + ", " + write3.size + ")",
    )
  }
}
