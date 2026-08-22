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

import assertk.all
import assertk.assertFailure
import assertk.assertions.cause
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import okio.TestUtil.SEGMENT_SIZE
import okio.TestUtil.randomBytes
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DeflaterSinkTest {
  @Test
  fun deflateWithClose() {
    val data = Buffer()
    val original = "They're moving in herds. They do move in herds."
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink, Deflater())
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    Assertions.assertEquals(original, inflated.readUtf8())
  }

  @Test
  fun deflateWithSyncFlush() {
    val original = "Yes, yes, yes. That's why we're taking extreme precautions."
    val data = Buffer()
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink, Deflater())
    deflaterSink.write(data, data.size)
    deflaterSink.flush()
    val inflated = inflate(sink)
    Assertions.assertEquals(original, inflated.readUtf8())
  }

  @Test
  fun deflateWellCompressed() {
    val original = "a".repeat(1024 * 1024)
    val data = Buffer()
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink, Deflater())
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    Assertions.assertEquals(original, inflated.readUtf8())
  }

  @Test
  fun deflatePoorlyCompressed() {
    val original = randomBytes(1024 * 1024)
    val data = Buffer()
    data.write(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink, Deflater())
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    Assertions.assertEquals(original, inflated.readByteString())
  }

  @Test
  fun multipleSegmentsWithoutCompression() {
    val buffer = Buffer()
    val deflater = Deflater()
    deflater.setLevel(Deflater.NO_COMPRESSION)
    val deflaterSink = DeflaterSink(buffer, deflater)
    val byteCount = SEGMENT_SIZE * 4
    deflaterSink.write(Buffer().writeUtf8("a".repeat(byteCount)), byteCount.toLong())
    deflaterSink.close()
    Assertions.assertEquals("a".repeat(byteCount), inflate(buffer).readUtf8(byteCount.toLong()))
  }

  @Test
  fun deflateIntoNonemptySink() {
    val original = "They're moving in herds. They do move in herds."

    // Exercise all possible offsets for the outgoing segment.
    for (i in 0 until SEGMENT_SIZE) {
      val data = Buffer().writeUtf8(original)
      val sink = Buffer().writeUtf8("a".repeat(i))
      val deflaterSink = DeflaterSink(sink, Deflater())
      deflaterSink.write(data, data.size)
      deflaterSink.close()
      sink.skip(i.toLong())
      val inflated = inflate(sink)
      Assertions.assertEquals(original, inflated.readUtf8())
    }
  }

  /**
   * This test deflates a single segment of without compression because that's
   * the easiest way to force close() to emit a large amount of data to the
   * underlying sink.
   */
  @Test
  fun closeWithExceptionWhenWritingAndClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, IOException("first"))
    mockSink.scheduleThrow(1, IOException("second"))
    val deflater = Deflater()
    deflater.setLevel(Deflater.NO_COMPRESSION)
    val deflaterSink = DeflaterSink(mockSink, deflater)
    deflaterSink.write(Buffer().writeUtf8("a".repeat(SEGMENT_SIZE)), SEGMENT_SIZE.toLong())
    try {
      deflaterSink.close()
      Assertions.fail()
    } catch (expected: IOException) {
      Assertions.assertEquals("first", expected.message)
    }
    mockSink.assertLogContains("close()")
  }

  /**
   * This test confirms that we swallow NullPointerException from Deflater and
   * rethrow as an IOException.
   */
  @Test
  fun rethrowNullPointerAsIOException() {
    val deflater = Deflater()
    // Close to cause an exception.
    deflater.end()

    val data = Buffer().apply {
      writeUtf8("They're moving in herds. They do move in herds.")
    }
    val deflaterSink = DeflaterSink(Buffer(), deflater)

    assertFailure {
      deflaterSink.write(data, data.size)
    }.all {
      isInstanceOf<IOException>()
      hasMessage("Deflater already closed")
      cause().isNotNull()
    }
  }

  /**
   * Uses streaming decompression to inflate `deflated`. The input must
   * either be finished or have a trailing sync flush.
   */
  private fun inflate(deflated: Buffer): Buffer {
    val deflatedIn = deflated.inputStream()
    val inflater = Inflater()
    val inflatedIn = InflaterInputStream(deflatedIn, inflater)
    val result = Buffer()
    val buffer = ByteArray(8192)
    while (!inflater.needsInput() || deflated.size > 0 || deflatedIn.available() > 0) {
      val count = inflatedIn.read(buffer, 0, buffer.size)
      if (count != -1) {
        result.write(buffer, 0, count)
      }
    }
    return result
  }
}
