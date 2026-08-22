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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.text.Charsets.UTF_8
import okio.TestUtil.SEGMENT_SIZE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OkioTest {
  @TempDir
  lateinit var temporaryFolder: File

  @Test
  fun readWriteFile() {
    val file = temporaryFolder.newFile()
    val sink = file.sink().buffer()
    sink.writeUtf8("Hello, java.io file!")
    sink.close()
    assertTrue(file.exists())
    assertEquals(20, file.length())
    val source = file.source().buffer()
    assertEquals("Hello, java.io file!", source.readUtf8())
    source.close()
  }

  @Test
  fun appendFile() {
    val file = temporaryFolder.newFile()
    var sink = file.appendingSink().buffer()
    sink.writeUtf8("Hello, ")
    sink.close()
    assertTrue(file.exists())
    assertEquals(7, file.length())
    sink = file.appendingSink().buffer()
    sink.writeUtf8("java.io file!")
    sink.close()
    assertEquals(20, file.length())
    val source = file.source().buffer()
    assertEquals("Hello, java.io file!", source.readUtf8())
    source.close()
  }

  @Test
  fun readWritePath() {
    val path = temporaryFolder.newFile().toPath()
    val sink = path.sink().buffer()
    sink.writeUtf8("Hello, java.nio file!")
    sink.close()
    assertTrue(Files.exists(path))
    assertEquals(21, Files.size(path))
    val source = path.source().buffer()
    assertEquals("Hello, java.nio file!", source.readUtf8())
    source.close()
  }

  @Test
  fun sinkFromOutputStream() {
    val data = Buffer()
    data.writeUtf8("a")
    data.writeUtf8("b".repeat(9998))
    data.writeUtf8("c")
    val out = ByteArrayOutputStream()
    val sink = out.sink()
    sink.write(data, 3)
    assertEquals("abb", out.toString("UTF-8"))
    sink.write(data, data.size)
    assertEquals("a" + "b".repeat(9998) + "c", out.toString("UTF-8"))
  }

  @Test
  fun sourceFromInputStream() {
    val inputStream = ByteArrayInputStream(
      ("a" + "b".repeat(SEGMENT_SIZE * 2) + "c").toByteArray(UTF_8),
    )

    // Source: ab...bc
    val source = inputStream.source()
    val sink = Buffer()

    // Source: b...bc. Sink: abb.
    assertEquals(3, source.read(sink, 3))
    assertEquals("abb", sink.readUtf8(3))

    // Source: b...bc. Sink: b...b.
    assertEquals(SEGMENT_SIZE.toLong(), source.read(sink, 20000))
    assertEquals("b".repeat(SEGMENT_SIZE), sink.readUtf8())

    // Source: b...bc. Sink: b...bc.
    assertEquals((SEGMENT_SIZE - 1).toLong(), source.read(sink, 20000))
    assertEquals("b".repeat(SEGMENT_SIZE - 2) + "c", sink.readUtf8())

    // Source and sink are empty.
    assertEquals(-1, source.read(sink, 1))
  }

  @Test
  fun sourceFromInputStreamWithSegmentSize() {
    val inputStream = ByteArrayInputStream(ByteArray(SEGMENT_SIZE))
    val source = inputStream.source()
    val sink = Buffer()
    assertEquals(SEGMENT_SIZE.toLong(), source.read(sink, SEGMENT_SIZE.toLong()))
    assertEquals(-1, source.read(sink, SEGMENT_SIZE.toLong()))
    assertNoEmptySegments(sink)
  }

  @Test
  fun sourceFromInputStreamBounds() {
    val source = ByteArrayInputStream(ByteArray(100)).source()
    try {
      source.read(Buffer(), -1)
      fail()
    } catch (expected: IllegalArgumentException) {
    }
  }

  @Test
  fun blackhole() {
    val data = Buffer()
    data.writeUtf8("blackhole")
    val blackhole = blackholeSink()
    blackhole.write(data, 5)
    assertEquals("hole", data.readUtf8())
  }
}
