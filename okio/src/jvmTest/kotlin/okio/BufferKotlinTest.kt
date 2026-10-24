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
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class BufferKotlinTest {
  @Test fun get() {
    val actual = Buffer().writeUtf8("abc")
    assertThat(actual[0]).isEqualTo('a'.code.toByte())
    assertThat(actual[1]).isEqualTo('b'.code.toByte())
    assertThat(actual[2]).isEqualTo('c'.code.toByte())
    assertFailsWith<IndexOutOfBoundsException> {
      actual[-1]
    }
    assertFailsWith<IndexOutOfBoundsException> {
      actual[3]
    }
  }

  @Test fun copyToOutputStream() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.copyTo(target.outputStream())
    assertThat(target.readUtf8()).isEqualTo("party")
    assertThat(source.readUtf8()).isEqualTo("party")
  }

  @Test fun copyToOutputStreamWithOffset() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.copyTo(target.outputStream(), offset = 2)
    assertThat(target.readUtf8()).isEqualTo("rty")
    assertThat(source.readUtf8()).isEqualTo("party")
  }

  @Test fun copyToOutputStreamWithByteCount() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.copyTo(target.outputStream(), byteCount = 3)
    assertThat(target.readUtf8()).isEqualTo("par")
    assertThat(source.readUtf8()).isEqualTo("party")
  }

  @Test fun copyToOutputStreamWithOffsetAndByteCount() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.copyTo(target.outputStream(), offset = 1, byteCount = 3)
    assertThat(target.readUtf8()).isEqualTo("art")
    assertThat(source.readUtf8()).isEqualTo("party")
  }

  @Test fun writeToOutputStream() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.writeTo(target.outputStream())
    assertThat(target.readUtf8()).isEqualTo("party")
    assertThat(source.readUtf8()).isEqualTo("")
  }

  @Test fun writeToOutputStreamWithByteCount() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.writeTo(target.outputStream(), byteCount = 3)
    assertThat(target.readUtf8()).isEqualTo("par")
    assertThat(source.readUtf8()).isEqualTo("ty")
  }
}
