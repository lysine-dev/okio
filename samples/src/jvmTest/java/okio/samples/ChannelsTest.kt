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
package okio.samples

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.EnumSet
import okio.Buffer
import okio.Timeout
import okio.buffer
import okio.newFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ChannelsTest {
  @TempDir
  lateinit var temporaryFolder: File

  @Test
  fun testReadChannel() {
    val channel: ReadableByteChannel = Buffer().writeUtf8(quote)

    val buffer = Buffer()
    val source = ByteChannelSource(channel, Timeout.NONE)
    source.read(buffer, 75)

    assertThat(buffer.readUtf8())
      .isEqualTo("John, the kind of control you're attempting simply is... it's not possible.")
  }

  @Test
  fun testReadChannelFully() {
    val channel: ReadableByteChannel = Buffer().writeUtf8(quote)

    val source = ByteChannelSource(channel, Timeout.NONE).buffer()
    assertThat(source.readUtf8())
      .isEqualTo(quote)
  }

  @Test
  fun testWriteChannel() {
    val channel = Buffer()

    val sink = ByteChannelSink(channel, Timeout.NONE)
    sink.write(Buffer().writeUtf8(quote), 75)

    assertThat(channel.readUtf8())
      .isEqualTo("John, the kind of control you're attempting simply is... it's not possible.")
  }

  @Test
  fun testReadWriteFile() {
    val path = temporaryFolder.newFile().toPath()

    val sink = FileChannelSink(FileChannel.open(path, w), Timeout.NONE)
    sink.write(Buffer().writeUtf8(quote), 317)
    sink.close()
    assertTrue(Files.exists(path))
    assertEquals(quote.length.toLong(), Files.size(path))

    val buffer = Buffer()
    val source = FileChannelSource(FileChannel.open(path, r), Timeout.NONE)

    source.read(buffer, 44)
    assertThat(buffer.readUtf8())
      .isEqualTo("John, the kind of control you're attempting ")

    source.read(buffer, 31)
    assertThat(buffer.readUtf8())
      .isEqualTo("simply is... it's not possible.")
  }

  @Test
  fun testAppend() {
    val path = temporaryFolder.newFile().toPath()
    val buffer = Buffer().writeUtf8(quote)

    FileChannelSink(FileChannel.open(path, w), Timeout.NONE).use { sink ->
      sink.write(buffer, 75)
    }
    assertTrue(Files.exists(path))
    assertEquals(75, Files.size(path))

    FileChannelSource(FileChannel.open(path, r), Timeout.NONE).buffer().use { source ->
      assertThat(source.readUtf8())
        .isEqualTo("John, the kind of control you're attempting simply is... it's not possible.")
    }

    FileChannelSink(FileChannel.open(path, append), Timeout.NONE).use { sink ->
      sink.write(buffer, buffer.size)
    }
    assertTrue(Files.exists(path))
    assertEquals(quote.length.toLong(), Files.size(path))

    FileChannelSource(FileChannel.open(path, r), Timeout.NONE).buffer().use { source ->
      assertThat(source.readUtf8())
        .isEqualTo(quote)
    }
  }

  companion object {
    private val quote =
      "John, the kind of control you're attempting simply is... it's not " +
        "possible. If there is one thing the history of evolution has " +
        "taught us it's that life will not be contained. Life breaks " +
        "free, it expands to new territories and crashes through " +
        "barriers, painfully, maybe even dangerously, but, uh... well, " +
        "there it is."

    private val r = EnumSet.of(
      StandardOpenOption.READ,
    )
    private val w = EnumSet.of(
      StandardOpenOption.WRITE,
    )
    private val append = EnumSet.of(
      StandardOpenOption.WRITE,
      StandardOpenOption.APPEND,
    )
  }
}
