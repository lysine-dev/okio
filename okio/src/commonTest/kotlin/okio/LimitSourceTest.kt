/*
 * Copyright (C) 2021 Square, Inc.
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
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertFailsWith

val LimitSourceTest by testSuite {
  for (throwIfSourceIsLonger in listOf(true, false)) {
    test("happyPath throwIfSourceIsLonger=$throwIfSourceIsLonger") {
      val delegate = Buffer().writeUtf8("abcdefghijklmnop")
      val limitSource = delegate.limit(16L, throwIfSourceIsLonger = throwIfSourceIsLonger)
      val buffer = Buffer()
      assertThat(limitSource.read(buffer, 10L)).isEqualTo(10L)
      assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
      assertThat(limitSource.read(buffer, 10L)).isEqualTo(6L)
      assertThat(buffer.readUtf8()).isEqualTo("klmnop")
      assertThat(limitSource.read(buffer, 10L)).isEqualTo(-1L)
      assertThat(buffer.readUtf8()).isEqualTo("")
    }
  }

  test("delegateTooLong") {
    val delegate = Buffer().writeUtf8("abcdefghijklmnopqr")
    val limitSource = delegate.limit(16L)
    val buffer = Buffer()
    assertThat(limitSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    assertThat(limitSource.read(buffer, 10L)).isEqualTo(6L)
    assertThat(buffer.readUtf8()).isEqualTo("klmnop")
    assertThat(limitSource.read(buffer, 10L)).isEqualTo(-1L)
    assertThat(buffer.readUtf8()).isEqualTo("")
  }

  test("delegateTooLongFencepost") {
    val delegate = Buffer().writeUtf8("abcdefghijklmnop")
    val limitSource = delegate.limit(10L)
    val buffer = Buffer()
    assertThat(limitSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
    assertThat(limitSource.read(buffer, 10L)).isEqualTo(-1L)
    assertThat(buffer.readUtf8()).isEmpty()
  }

  test("delegateTooLongThrowIfSourceIsLonger") {
    val delegate = Buffer().writeUtf8("abcdefghijklmnopqr")
    val limitSource = delegate.limit(16L, throwIfSourceIsLonger = true)
    val buffer = Buffer()
    assertThat(limitSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")

    val e1 = assertFailsWith<IOException> {
      limitSource.read(buffer, 10L)
    }
    assertThat(e1).hasMessage("expected 16 bytes but got 17")
    assertThat(buffer.readUtf8()).isEqualTo("klmnop") // Doesn't produce too many bytes!

    val e2 = assertFailsWith<IOException> {
      limitSource.read(buffer, 10L)
    }
    assertThat(e2).hasMessage("expected 16 bytes but got 17")
    assertThat(buffer.readUtf8()).isEmpty() // Doesn't produce any bytes!
  }

  test("delegateTooLongThrowIfSourceIsLongerFencepost") {
    val delegate = Buffer().writeUtf8("abcdefghijklmnop")
    val limitSource = delegate.limit(10L, throwIfSourceIsLonger = true)
    val buffer = Buffer()
    assertThat(limitSource.read(buffer, 10L)).isEqualTo(10L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")

    val e1 = assertFailsWith<IOException> {
      limitSource.read(buffer, 10L)
    }
    assertThat(e1).hasMessage("expected 10 bytes but got 11")
    assertThat(buffer.readUtf8()).isEmpty() // Doesn't produce too many bytes!

    val e2 = assertFailsWith<IOException> {
      limitSource.read(buffer, 10L)
    }
    assertThat(e2).hasMessage("expected 10 bytes but got 11")
    assertThat(buffer.readUtf8()).isEmpty() // Doesn't produce any bytes!
  }

  for (throwIfSourceIsLonger in listOf(true, false)) {
    test("delegateTooShort throwIfSourceIsLonger=$throwIfSourceIsLonger") {
      val delegate = Buffer().writeUtf8("abcdefghijklmn")
      val limitSource = delegate.limit(16L, throwIfSourceIsLonger = throwIfSourceIsLonger)
      val buffer = Buffer()
      assertThat(limitSource.read(buffer, 10L)).isEqualTo(10L)
      assertThat(buffer.readUtf8()).isEqualTo("abcdefghij")
      assertThat(limitSource.read(buffer, 10L)).isEqualTo(4L)
      assertThat(buffer.readUtf8()).isEqualTo("klmn")

      val e1 = assertFailsWith<EOFException> {
        limitSource.read(buffer, 10L)
      }
      assertThat(e1).hasMessage("expected 16 bytes but got 14")

      val e2 = assertFailsWith<EOFException> {
        limitSource.read(buffer, 10L)
      }
      assertThat(e2).hasMessage("expected 16 bytes but got 14")
    }
  }

  for (throwIfSourceIsLonger in listOf(true, false)) {
    test("byteCountNotNegative throwIfSourceIsLonger=$throwIfSourceIsLonger") {
      val source = Buffer()
      val e = assertFailsWith<IllegalArgumentException> {
        source.limit(-2L, throwIfSourceIsLonger = throwIfSourceIsLonger)
      }
      assertThat(e).hasMessage("byteCount < 0: -2")
    }
  }

  test("zeroByteTarget") {
    val delegate = Buffer().writeUtf8("abcdefg")
    val limitSource = delegate.limit(0L)
    val buffer = Buffer()

    assertThat(limitSource.read(buffer, 10L)).isEqualTo(-1L)
    assertThat(buffer.readUtf8()).isEqualTo("")
    assertThat(delegate.readUtf8()).isEqualTo("abcdefg")
  }

  test("zeroByteTargetThrowIfSourceIsLonger") {
    val delegate = Buffer().writeUtf8("abcdefg")
    val limitSource = delegate.limit(0L, throwIfSourceIsLonger = true)
    val buffer = Buffer()

    val e1 = assertFailsWith<IOException> {
      limitSource.read(buffer, 10L)
    }
    assertThat(e1).hasMessage("expected 0 bytes but got 1")
    assertThat(buffer.readUtf8()).isEqualTo("")

    val e2 = assertFailsWith<IOException> {
      limitSource.read(buffer, 10L)
    }
    assertThat(e2).hasMessage("expected 0 bytes but got 1")
    assertThat(buffer.readUtf8()).isEqualTo("")
  }
}
