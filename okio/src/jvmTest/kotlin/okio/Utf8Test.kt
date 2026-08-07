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

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import java.io.EOFException
import kotlin.test.assertFailsWith
import kotlin.text.Charsets.UTF_8
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.of
import okio.TestUtil.SEGMENT_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Utf8Test {
  @Test
  fun oneByteCharacters() {
    assertEncoded("00", 0x00) // Smallest 1-byte character.
    assertEncoded("20", ' '.code)
    assertEncoded("7e", '~'.code)
    assertEncoded("7f", 0x7f) // Largest 1-byte character.
  }

  @Test
  fun twoByteCharacters() {
    assertEncoded("c280", 0x0080) // Smallest 2-byte character.
    assertEncoded("c3bf", 0x00ff)
    assertEncoded("c480", 0x0100)
    assertEncoded("dfbf", 0x07ff) // Largest 2-byte character.
  }

  @Test
  fun threeByteCharacters() {
    assertEncoded("e0a080", 0x0800) // Smallest 3-byte character.
    assertEncoded("e0bfbf", 0x0fff)
    assertEncoded("e18080", 0x1000)
    assertEncoded("e1bfbf", 0x1fff)
    assertEncoded("ed8080", 0xd000)
    assertEncoded("ed9fbf", 0xd7ff) // Largest character lower than the min surrogate.
    assertEncoded("ee8080", 0xe000) // Smallest character greater than the max surrogate.
    assertEncoded("eebfbf", 0xefff)
    assertEncoded("ef8080", 0xf000)
    assertEncoded("efbfbf", 0xffff) // Largest 3-byte character.
  }

  @Test
  fun fourByteCharacters() {
    assertEncoded("f0908080", 0x010000) // Smallest surrogate pair.
    assertEncoded("f48fbfbf", 0x10ffff) // Largest code point expressible by UTF-16.
  }

  @Test
  fun danglingHighSurrogate() {
    assertStringEncoded("3f", "\ud800") // "?"
  }

  @Test
  fun lowSurrogateWithoutHighSurrogate() {
    assertStringEncoded("3f", "\udc00") // "?"
  }

  @Test
  fun highSurrogateFollowedByNonSurrogate() {
    assertStringEncoded("3f61", "\ud800\u0061") // "?a": Following character is too low.
    assertStringEncoded("3fee8080", "\ud800\ue000") // "?\ue000": Following character is too high.
  }

  @Test
  fun doubleLowSurrogate() {
    assertStringEncoded("3f3f", "\udc00\udc00") // "??"
  }

  @Test
  fun doubleHighSurrogate() {
    assertStringEncoded("3f3f", "\ud800\ud800") // "??"
  }

  @Test
  fun highSurrogateLowSurrogate() {
    assertStringEncoded("3f3f", "\udc00\ud800") // "??"
  }

  @Test
  fun multipleSegmentString() {
    val a = "a".repeat(SEGMENT_SIZE + SEGMENT_SIZE + 1)
    val encoded = Buffer().writeUtf8(a)
    val expected = Buffer().write(a.toByteArray(UTF_8))
    assertEquals(expected, encoded)
  }

  @Test
  fun stringSpansSegments() {
    val buffer = Buffer()
    val a = "a".repeat(SEGMENT_SIZE - 1)
    val b = "bb"
    val c = "c".repeat(SEGMENT_SIZE - 1)
    buffer.writeUtf8(a)
    buffer.writeUtf8(b)
    buffer.writeUtf8(c)
    assertEquals(a + b + c, buffer.readUtf8())
  }

  @Test
  fun readEmptyBufferThrowsEofException() {
    val buffer = Buffer()
    assertFailsWith<EOFException> {
      buffer.readUtf8CodePoint()
    }
  }

  @Test
  fun readLeadingContinuationByteReturnsReplacementCharacter() {
    val buffer = Buffer()
    buffer.writeByte(0xbf)
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertTrue(buffer.exhausted())
  }

  @Test
  fun readMissingContinuationBytesThrowsEofException() {
    val buffer = Buffer()
    buffer.writeByte(0xdf)
    assertFailsWith<EOFException> {
      buffer.readUtf8CodePoint()
    }
    assertFalse(buffer.exhausted()) // Prefix byte wasn't consumed.
  }

  @Test
  fun readTooLargeCodepointReturnsReplacementCharacter() {
    // 5-byte and 6-byte code points are not supported.
    val buffer = Buffer()
    buffer.write("f888808080".decodeHex())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertTrue(buffer.exhausted())
  }

  @Test
  fun readNonContinuationBytesReturnsReplacementCharacter() {
    // Use a non-continuation byte where a continuation byte is expected.
    val buffer = Buffer()
    buffer.write("df20".decodeHex())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertEquals(0x20, buffer.readUtf8CodePoint().toLong()) // Non-continuation character not consumed.
    assertTrue(buffer.exhausted())
  }

  @Test
  fun readCodePointBeyondUnicodeMaximum() {
    // A 4-byte encoding with data above the U+10ffff Unicode maximum.
    val buffer = Buffer()
    buffer.write("f4908080".decodeHex())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertTrue(buffer.exhausted())
  }

  @Test
  fun readSurrogateCodePoint() {
    val buffer = Buffer()
    buffer.write("eda080".decodeHex())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertTrue(buffer.exhausted())
    buffer.write("edbfbf".decodeHex())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertTrue(buffer.exhausted())
  }

  @Test
  fun readOverlongCodePoint() {
    // Use 2 bytes to encode data that only needs 1 byte.
    val buffer = Buffer()
    buffer.write("c080".decodeHex())
    assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
    assertTrue(buffer.exhausted())
  }

  @Test
  fun writeSurrogateCodePoint() {
    assertStringEncoded("ed9fbf", "\ud7ff") // Below lowest surrogate is okay.
    assertStringEncoded("3f", "\ud800") // Lowest surrogate gets '?'.
    assertStringEncoded("3f", "\udfff") // Highest surrogate gets '?'.
    assertStringEncoded("ee8080", "\ue000") // Above highest surrogate is okay.
  }

  @Test
  fun writeCodePointBeyondUnicodeMaximum() {
    val buffer = Buffer()
    val expected = assertFailsWith<IllegalArgumentException> {
      buffer.writeUtf8CodePoint(0x110000)
    }
    assertThat(expected).hasMessage("Unexpected code point: 0x110000")
  }

  @Test
  fun size() {
    assertEquals(0, "".utf8Size())
    assertEquals(3, "abc".utf8Size())
    assertEquals(16, "təˈranəˌsôr".utf8Size())
  }

  @Test
  fun sizeWithBounds() {
    assertEquals(0, "".utf8Size(0, 0))
    assertEquals(0, "abc".utf8Size(0, 0))
    assertEquals(1, "abc".utf8Size(1, 2))
    assertEquals(2, "abc".utf8Size(0, 2))
    assertEquals(3, "abc".utf8Size(0, 3))
    assertEquals(16, "təˈranəˌsôr".utf8Size(0, 11))
    assertEquals(5, "təˈranəˌsôr".utf8Size(3, 7))
  }

  @Test
  fun sizeBoundsCheck() {
    assertFailsWith<NullPointerException> {
      null!!.utf8Size(0, 0)
    }
    assertFailsWith<IllegalArgumentException> {
      "abc".utf8Size(-1, 2)
    }
    assertFailsWith<IllegalArgumentException> {
      "abc".utf8Size(2, 1)
    }
    assertFailsWith<IllegalArgumentException> {
      "abc".utf8Size(1, 4)
    }
  }

  private fun assertEncoded(hex: String, vararg codePoints: Int) {
    assertCodePointEncoded(hex, *codePoints)
    assertCodePointDecoded(hex, *codePoints)
    assertStringEncoded(hex, String(codePoints, 0, codePoints.size))
  }

  private fun assertCodePointEncoded(hex: String, vararg codePoints: Int) {
    val buffer = Buffer()
    for (codePoint in codePoints) {
      buffer.writeUtf8CodePoint(codePoint)
    }
    assertEquals(buffer.readByteString(), hex.decodeHex())
  }

  private fun assertCodePointDecoded(hex: String, vararg codePoints: Int) {
    val buffer = Buffer().write(hex.decodeHex())
    for (codePoint in codePoints) {
      assertEquals(codePoint.toLong(), buffer.readUtf8CodePoint().toLong())
    }
    assertTrue(buffer.exhausted())
  }

  private fun assertStringEncoded(hex: String, string: String) {
    val expectedUtf8 = hex.decodeHex()

    // Confirm our expectations are consistent with the platform.
    val platformUtf8 = of(*string.toByteArray(charset("UTF-8")))
    assertEquals(expectedUtf8, platformUtf8)

    // Confirm our implementation matches those expectations.
    val actualUtf8 = Buffer().writeUtf8(string).readByteString()
    assertEquals(expectedUtf8, actualUtf8)

    // Confirm we are consistent when writing one code point at a time.
    val bufferUtf8 = Buffer()
    var i = 0
    while (i < string.length) {
      val c = string.codePointAt(i)
      bufferUtf8.writeUtf8CodePoint(c)
      i += Character.charCount(c)
    }
    assertEquals(expectedUtf8, bufferUtf8.readByteString())

    // Confirm we are consistent when measuring lengths.
    assertEquals(expectedUtf8.size.toLong(), string.utf8Size())
    assertEquals(expectedUtf8.size.toLong(), string.utf8Size(0, string.length))

    // Confirm Appendable APIs works.
    assertBufferAppendableWriteString(string, expectedUtf8)
    assertBufferAppendableWriteChars(string, expectedUtf8)
    assertBufferedSinkAppendableWriteString(string, expectedUtf8)
    assertBufferedSinkAppendableWriteChars(string, expectedUtf8)
  }

  private fun assertBufferAppendableWriteString(string: String, expectedUtf8: ByteString) {
    val buffer = Buffer()
    buffer.utf8Appendable().append("abc${string}xyz")

    assertThat(buffer.readUtf8(3)).isEqualTo("abc")
    assertThat(buffer.readByteString(buffer.size - 3)).isEqualTo(expectedUtf8)
    assertThat(buffer.readUtf8(3)).isEqualTo("xyz")
  }

  private fun assertBufferAppendableWriteChars(string: String, expectedUtf8: ByteString) {
    val buffer = Buffer()
    val appendable = buffer.utf8Appendable()
    appendable.append('a')
    appendable.append('b')
    appendable.append('c')
    for (c in string) {
      appendable.append(c)
    }
    appendable.append('x')
    appendable.append('y')
    appendable.append('z')

    assertThat(buffer.readUtf8(3)).isEqualTo("abc")
    assertThat(buffer.readByteString(buffer.size - 3)).isEqualTo(expectedUtf8)
    assertThat(buffer.readUtf8(3)).isEqualTo("xyz")
  }

  private fun assertBufferedSinkAppendableWriteString(string: String, expectedUtf8: ByteString) {
    val data = Buffer()
    val sink = (data as Sink).buffer()
    sink.utf8Appendable().append("abc${string}xyz")
    sink.emit()

    assertThat(data.readUtf8(3)).isEqualTo("abc")
    assertThat(data.readByteString(data.size - 3)).isEqualTo(expectedUtf8)
    assertThat(data.readUtf8(3)).isEqualTo("xyz")
  }

  private fun assertBufferedSinkAppendableWriteChars(string: String, expectedUtf8: ByteString) {
    val data = Buffer()
    val sink = (data as Sink).buffer()
    val appendable = sink.utf8Appendable()
    appendable.append('a')
    appendable.append('b')
    appendable.append('c')
    for (c in string) {
      appendable.append(c)
    }
    appendable.append('x')
    appendable.append('y')
    appendable.append('z')
    sink.emit()

    assertThat(data.readUtf8(3)).isEqualTo("abc")
    assertThat(data.readByteString(data.size - 3)).isEqualTo(expectedUtf8)
    assertThat(data.readUtf8(3)).isEqualTo("xyz")
  }
}
