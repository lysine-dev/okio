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

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Socket
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption.APPEND
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OkioKotlinTest {
  @TempDir lateinit var temp: File

  @Test fun outputStreamSink() {
    val baos = ByteArrayOutputStream()
    val sink = baos.sink()
    sink.write(Buffer().writeUtf8("a"), 1L)
    assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
  }

  @Test fun inputStreamSource() {
    val bais = ByteArrayInputStream(byteArrayOf(0x61))
    val source = bais.source()
    val buffer = Buffer()
    source.read(buffer, 1)
    assertThat(buffer.readUtf8()).isEqualTo("a")
  }

  @Test fun fileSink() {
    val file = temp.newFile()
    file.sink().use { it.write(Buffer().writeUtf8("a"), 1L) }
    assertThat(file.readText()).isEqualTo("a")
  }

  @Test fun fileAppendingSink() {
    val file = temp.newFile()
    file.writeText("a")
    file.sink(append = true).use { it.write(Buffer().writeUtf8("b"), 1L) }
    assertThat(file.readText()).isEqualTo("ab")
  }

  @Test fun fileSource() {
    val file = temp.newFile()
    file.writeText("a")
    file.source().use { source ->
      val buffer = Buffer()
      source.read(buffer, 1L)
      assertThat(buffer.readUtf8()).isEqualTo("a")
    }
  }

  @Test fun pathSink() {
    val file = temp.newFile()
    val sink = file.toPath().sink()
    sink.write(Buffer().writeUtf8("a"), 1L)
    assertThat(file.readText()).isEqualTo("a")
  }

  @Test fun pathSinkWithOptions() {
    val file = temp.newFile()
    file.writeText("a")
    val sink = file.toPath().sink(APPEND)
    sink.write(Buffer().writeUtf8("b"), 1L)
    assertThat(file.readText()).isEqualTo("ab")
  }

  @Test fun pathSource() {
    val file = temp.newFile()
    file.writeText("a")
    val source = file.toPath().source()
    val buffer = Buffer()
    source.read(buffer, 1L)
    assertThat(buffer.readUtf8()).isEqualTo("a")
  }

  @Disabled("Not sure how to test this")
  @Test
  fun pathSourceWithOptions() {
    val file = File(temp, "new.txt")
    file.toPath().source(StandardOpenOption.CREATE_NEW)
    // This still throws NoSuchFileException...
  }

  @Test fun socketSink() {
    val baos = ByteArrayOutputStream()
    val socket = object : Socket() {
      override fun getOutputStream() = baos
    }
    val sink = socket.sink()
    sink.write(Buffer().writeUtf8("a"), 1L)
    assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
  }

  @Test fun socketSource() {
    val bais = ByteArrayInputStream(byteArrayOf(0x61))
    val socket = object : Socket() {
      override fun getInputStream() = bais
    }
    val source = socket.source()
    val buffer = Buffer()
    source.read(buffer, 1L)
    assertThat(buffer.readUtf8()).isEqualTo("a")
  }
}
