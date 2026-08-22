/*
 * Copyright (C) 2013 Square, Inc.
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
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import java.io.IOException
import java.util.Arrays
import okio.Buffer
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class SourceMarkerTest {
  @Test
  fun test() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    assertThat(source.readUtf8(3)).isEqualTo("ABC")
    val pos3 = marker.mark(7) // DEFGHIJ
    assertThat(source.readUtf8(4)).isEqualTo("DEFG")
    val pos7 = marker.mark(5) // HIJKL
    assertThat(source.readUtf8(4)).isEqualTo("HIJK")
    marker.reset(pos7) // Back to 'H'
    assertThat(source.readUtf8(3)).isEqualTo("HIJ")
    marker.reset(pos3) // Back to 'D'
    assertThat(source.readUtf8(7)).isEqualTo("DEFGHIJ")
    marker.reset(pos7) // Back to 'H' again.
    assertThat(source.readUtf8(6)).isEqualTo("HIJKLM")
    try {
      marker.reset(pos7)
      fail()
    } catch (expected: IOException) {
      assertThat(expected)
        .hasMessage("cannot reset to 7: out of range")
    }
    try {
      marker.reset(pos3)
      fail()
    } catch (expected: IOException) {
      assertThat(expected)
        .hasMessage("cannot reset to 3: out of range")
    }
  }

  @Test
  fun exceedLimitTest() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    assertThat(source.readUtf8(3)).isEqualTo("ABC")
    val pos3 = marker.mark(Long.MAX_VALUE) // D...
    assertThat(source.readUtf8(4)).isEqualTo("DEFG")
    val pos7 = marker.mark(5) // H...
    assertThat(source.readUtf8(4)).isEqualTo("HIJK")
    marker.reset(pos7) // Back to 'H'
    assertThat(source.readUtf8(3)).isEqualTo("HIJ")
    marker.reset(pos3) // Back to 'D'
    assertThat(source.readUtf8(7)).isEqualTo("DEFGHIJ")
    marker.reset(pos7) // Back to 'H' again.
    assertThat(source.readUtf8(6)).isEqualTo("HIJKLM")

    marker.reset(pos7) // Back to 'H' again despite the original limit being exceeded
    assertThat(source.readUtf8(2)).isEqualTo("HI") // confirm we're really back at H

    marker.reset(pos3) // Back to 'D' again despite the original limit being exceeded
    assertThat(source.readUtf8(2))
      .isEqualTo("DE") // confirm that we're really back at D
  }

  @Test
  fun markAndLimitSmallerThanUserBuffer() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    // Load 5 bytes into the user buffer, then mark 0..3 and confirm that resetting from 4 fails.
    source.require(5)
    val pos0 = marker.mark(3)
    assertThat(source.readUtf8(3)).isEqualTo("ABC")
    marker.reset(pos0)
    assertThat(source.readUtf8(4)).isEqualTo("ABCD")
    try {
      marker.reset(pos0)
      fail()
    } catch (expected: IOException) {
      assertThat(expected).hasMessage("cannot reset to 0: out of range")
    }
  }

  @Test
  fun resetTooLow() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    source.skip(3)
    marker.mark(3)
    source.skip(2)
    try {
      marker.reset(2)
      fail()
    } catch (expected: IOException) {
      assertThat(expected).hasMessage("cannot reset to 2: out of range")
    }
  }

  @Test
  fun resetTooHigh() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    marker.mark(3)
    source.skip(6)
    try {
      marker.reset(4)
      fail()
    } catch (expected: IOException) {
      assertThat(expected).hasMessage("cannot reset to 4: out of range")
    }
  }

  @Test
  fun resetUnread() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))

    marker.mark(3)
    try {
      marker.reset(2)
      fail()
    } catch (expected: IOException) {
      assertThat(expected).hasMessage("cannot reset to 2: out of range")
    }
  }

  @Test
  fun markNothingBuffered() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    val pos0 = marker.mark(5)
    assertThat(source.readUtf8(4)).isEqualTo("ABCD")
    marker.reset(pos0)
    assertThat(source.readUtf8(6)).isEqualTo("ABCDEF")
  }

  @Test
  fun mark0() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    val pos0 = marker.mark(0)
    marker.reset(pos0)
    assertThat(source.readUtf8(3)).isEqualTo("ABC")
  }

  @Test
  fun markNegative() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))

    try {
      marker.mark(-1L)
      fail()
    } catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessage("readLimit < 0: -1")
    }
  }

  @Test
  fun resetAfterEof() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDE"))
    val source = marker.source()

    val pos0 = marker.mark(5)
    assertThat(source.readUtf8()).isEqualTo("ABCDE")
    marker.reset(pos0)
    assertThat(source.readUtf8(3)).isEqualTo("ABC")
  }

  @Test
  fun closeThenMark() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    source.close()
    try {
      marker.mark(5)
      fail()
    } catch (expected: IllegalStateException) {
      assertThat(expected).hasMessage("closed")
    }
  }

  @Test
  fun closeThenReset() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    val pos0 = marker.mark(5)
    source.close()
    try {
      marker.reset(pos0)
      fail()
    } catch (expected: IllegalStateException) {
      assertThat(expected).hasMessage("closed")
    }
  }

  @Test
  fun closeThenRead() {
    val marker = SourceMarker(Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    val source = marker.source()

    source.close()
    try {
      source.readUtf8(3)
      fail()
    } catch (expected: IllegalStateException) {
      assertThat(expected).hasMessage("closed")
    }
  }

  @Test
  fun multipleSegments() {
    val `as` = repeat('a', 10000)
    val bs = repeat('b', 10000)
    val cs = repeat('c', 10000)
    val ds = repeat('d', 10000)

    val marker = SourceMarker(Buffer().writeUtf8(`as` + bs + cs + ds))
    val source = marker.source()

    assertThat(source.readUtf8(10000)).isEqualTo(`as`)
    val pos10k = marker.mark(15000)
    assertThat(source.readUtf8(10000)).isEqualTo(bs)
    val pos20k = marker.mark(15000)
    assertThat(source.readUtf8(10000)).isEqualTo(cs)
    marker.reset(pos20k)
    marker.reset(pos10k)
    assertThat(source.readUtf8(30000)).isEqualTo(bs + cs + ds)
  }

  private fun repeat(c: Char, count: Int): String {
    val array = CharArray(count)
    Arrays.fill(array, c)
    return String(array)
  }
}
