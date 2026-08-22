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

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.StandardOpenOption
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.text.Charsets.UTF_8
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Test interop between our beloved Okio and java.nio.  */
class NioTest {
  @TempDir
  lateinit var temporaryFolder: File

  @Test
  fun sourceIsOpen() {
    val source = (Buffer() as Source).buffer()
    assertTrue(source.isOpen())
    source.close()
    assertFalse(source.isOpen())
  }

  @Test
  fun sinkIsOpen() {
    val sink = (Buffer() as Sink).buffer()
    assertTrue(sink.isOpen)
    sink.close()
    assertFalse(sink.isOpen)
  }

  @Test
  fun writableChannelNioFile() {
    val file = temporaryFolder.newFile()
    val fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE)
    testWritableByteChannel(fileChannel)
    val emitted = file.source().buffer()
    assertEquals("defghijklmnopqrstuvw", emitted.readUtf8())
    emitted.close()
  }

  @Test
  fun writableChannelBuffer() {
    val buffer = Buffer()
    testWritableByteChannel(buffer)
    assertEquals("defghijklmnopqrstuvw", buffer.readUtf8())
  }

  @Test
  fun writableChannelBufferedSink() {
    val buffer = Buffer()
    val bufferedSink = (buffer as Sink).buffer()
    testWritableByteChannel(bufferedSink)
    assertEquals("defghijklmnopqrstuvw", buffer.readUtf8())
  }

  @Test
  fun readableChannelNioFile() {
    val file = temporaryFolder.newFile()
    val initialData = file.sink().buffer()
    initialData.writeUtf8("abcdefghijklmnopqrstuvwxyz")
    initialData.close()
    val fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)
    testReadableByteChannel(fileChannel)
  }

  @Test
  fun readableChannelBuffer() {
    val buffer = Buffer()
    buffer.writeUtf8("abcdefghijklmnopqrstuvwxyz")
    testReadableByteChannel(buffer)
  }

  @Test
  fun readableChannelBufferedSource() {
    val buffer = Buffer()
    val bufferedSource = (buffer as Source).buffer()
    buffer.writeUtf8("abcdefghijklmnopqrstuvwxyz")
    testReadableByteChannel(bufferedSource)
  }

  /**
   * Does some basic writes to `channel`. We execute this against both Okio's channels and
   * also a standard implementation from the JDK to confirm that their behavior is consistent.
   */
  private fun testWritableByteChannel(channel: WritableByteChannel) {
    assertTrue(channel.isOpen)
    val byteBuffer = ByteBuffer.allocate(1024)
    byteBuffer.put("abcdefghijklmnopqrstuvwxyz".toByteArray(UTF_8))
    (byteBuffer as java.nio.Buffer).flip() // Cast necessary for Java 8.
    (byteBuffer as java.nio.Buffer).position(3) // Cast necessary for Java 8.
    (byteBuffer as java.nio.Buffer).limit(23) // Cast necessary for Java 8.
    val byteCount = channel.write(byteBuffer)
    assertEquals(20, byteCount)
    assertEquals(23, byteBuffer.position())
    assertEquals(23, byteBuffer.limit())
    channel.close()
    assertEquals(channel is Buffer, channel.isOpen) // Buffer.close() does nothing.
  }

  /**
   * Does some basic reads from `channel`. We execute this against both Okio's channels and
   * also a standard implementation from the JDK to confirm that their behavior is consistent.
   */
  private fun testReadableByteChannel(channel: ReadableByteChannel) {
    assertTrue(channel.isOpen)
    val byteBuffer = ByteBuffer.allocate(1024)
    (byteBuffer as java.nio.Buffer).position(3) // Cast necessary for Java 8.
    (byteBuffer as java.nio.Buffer).limit(23) // Cast necessary for Java 8.
    val byteCount = channel.read(byteBuffer)
    assertEquals(20, byteCount)
    assertEquals(23, byteBuffer.position())
    assertEquals(23, byteBuffer.limit())
    channel.close()
    assertEquals(channel is Buffer, channel.isOpen) // Buffer.close() does nothing.
    (byteBuffer as java.nio.Buffer).flip() // Cast necessary for Java 8.
    (byteBuffer as java.nio.Buffer).position(3) // Cast necessary for Java 8.
    val data = ByteArray(byteBuffer.remaining())
    byteBuffer[data]
    assertEquals("abcdefghijklmnopqrst", String(data, UTF_8))
  }
}
