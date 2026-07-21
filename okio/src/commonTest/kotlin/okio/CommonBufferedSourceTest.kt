/*
 * Copyright (C) 2019 Square, Inc.
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

import app.cash.burst.Burst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

@Burst
class CommonBufferedSourceTest(
  private val factory: BufferedSourceFactory,
) {
  private val sink: BufferedSink
  private val source: BufferedSource

  init {
    val pipe = factory.pipe()
    sink = pipe.sink
    source = pipe.source
  }

  @Test fun readBytes() {
    sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte()))
    sink.emit()
    assertEquals(0xab, (source.readByte() and 0xff).toLong())
    assertEquals(0xcd, (source.readByte() and 0xff).toLong())
    assertTrue(source.exhausted())
  }

  @Test fun readByteTooShortThrows() {
    assertFailsWith<EOFException> {
      source.readByte()
    }
  }

  @Test fun readShort() {
    sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
    sink.emit()
    assertEquals(0xabcd.toShort().toLong(), source.readShort().toLong())
    assertEquals(0xef01.toShort().toLong(), source.readShort().toLong())
    assertTrue(source.exhausted())
  }

  @Test fun readShortLe() {
    sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte()))
    sink.emit()
    assertEquals(0xcdab.toShort().toLong(), source.readShortLe().toLong())
    assertEquals(0x10ef.toShort().toLong(), source.readShortLe().toLong())
    assertTrue(source.exhausted())
  }

  @Test fun readShortSplitAcrossMultipleSegments() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 1))
    sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte()))
    sink.emit()
    source.skip((Segment.SIZE - 1).toLong())
    assertEquals(0xabcd.toShort().toLong(), source.readShort().toLong())
    assertTrue(source.exhausted())
  }

  @Test fun readShortTooShortThrows() {
    sink.writeShort(Short.MAX_VALUE.toInt())
    sink.emit()
    source.readByte()
    assertFailsWith<EOFException> {
      source.readShort()
    }
  }

  @Test fun readShortLeTooShortThrows() {
    sink.writeShortLe(Short.MAX_VALUE.toInt())
    sink.emit()
    source.readByte()
    assertFailsWith<EOFException> {
      source.readShortLe()
    }
  }

  @Test fun readInt() {
    sink.write(
      byteArrayOf(
        0xab.toByte(),
        0xcd.toByte(),
        0xef.toByte(),
        0x01.toByte(),
        0x87.toByte(),
        0x65.toByte(),
        0x43.toByte(),
        0x21.toByte(),
      ),
    )
    sink.emit()
    assertEquals(-0x543210ff, source.readInt().toLong())
    assertEquals(-0x789abcdf, source.readInt().toLong())
    assertTrue(source.exhausted())
  }

  @Test fun readIntLe() {
    sink.write(
      byteArrayOf(
        0xab.toByte(),
        0xcd.toByte(),
        0xef.toByte(),
        0x10.toByte(),
        0x87.toByte(),
        0x65.toByte(),
        0x43.toByte(),
        0x21.toByte(),
      ),
    )
    sink.emit()
    assertEquals(0x10efcdab, source.readIntLe().toLong())
    assertEquals(0x21436587, source.readIntLe().toLong())
    assertTrue(source.exhausted())
  }

  @Test fun readIntSplitAcrossMultipleSegments() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 3))
    sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
    sink.emit()
    source.skip((Segment.SIZE - 3).toLong())
    assertEquals(-0x543210ff, source.readInt().toLong())
    assertTrue(source.exhausted())
  }

  @Test fun readIntTooShortThrows() {
    sink.writeInt(Int.MAX_VALUE)
    sink.emit()
    source.readByte()
    assertFailsWith<EOFException> {
      source.readInt()
    }
  }

  @Test fun readIntLeTooShortThrows() {
    sink.writeIntLe(Int.MAX_VALUE)
    sink.emit()
    source.readByte()
    assertFailsWith<EOFException> {
      source.readIntLe()
    }
  }

  @Test fun readLong() {
    sink.write(
      byteArrayOf(
        0xab.toByte(),
        0xcd.toByte(),
        0xef.toByte(),
        0x10.toByte(),
        0x87.toByte(),
        0x65.toByte(),
        0x43.toByte(),
        0x21.toByte(),
        0x36.toByte(),
        0x47.toByte(),
        0x58.toByte(),
        0x69.toByte(),
        0x12.toByte(),
        0x23.toByte(),
        0x34.toByte(),
        0x45.toByte(),
      ),
    )
    sink.emit()
    assertEquals(-0x543210ef789abcdfL, source.readLong())
    assertEquals(0x3647586912233445L, source.readLong())
    assertTrue(source.exhausted())
  }

  @Test fun readLongLe() {
    sink.write(
      byteArrayOf(
        0xab.toByte(),
        0xcd.toByte(),
        0xef.toByte(),
        0x10.toByte(),
        0x87.toByte(),
        0x65.toByte(),
        0x43.toByte(),
        0x21.toByte(),
        0x36.toByte(),
        0x47.toByte(),
        0x58.toByte(),
        0x69.toByte(),
        0x12.toByte(),
        0x23.toByte(),
        0x34.toByte(),
        0x45.toByte(),
      ),
    )
    sink.emit()
    assertEquals(0x2143658710efcdabL, source.readLongLe())
    assertEquals(0x4534231269584736L, source.readLongLe())
    assertTrue(source.exhausted())
  }

  @Test fun readLongSplitAcrossMultipleSegments() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 7))
    sink.write(
      byteArrayOf(
        0xab.toByte(),
        0xcd.toByte(),
        0xef.toByte(),
        0x01.toByte(),
        0x87.toByte(),
        0x65.toByte(),
        0x43.toByte(),
        0x21.toByte(),
      ),
    )
    sink.emit()
    source.skip((Segment.SIZE - 7).toLong())
    assertEquals(-0x543210fe789abcdfL, source.readLong())
    assertTrue(source.exhausted())
  }

  @Test fun readLongTooShortThrows() {
    sink.writeLong(Long.MAX_VALUE)
    sink.emit()
    source.readByte()
    assertFailsWith<EOFException> {
      source.readLong()
    }
  }

  @Test fun readLongLeTooShortThrows() {
    sink.writeLongLe(Long.MAX_VALUE)
    sink.emit()
    source.readByte()
    assertFailsWith<EOFException> {
      source.readLongLe()
    }
  }

  @Test fun readAll() {
    source.buffer.writeUtf8("abc")
    sink.writeUtf8("def")
    sink.emit()

    val sink = Buffer()
    assertEquals(6, source.readAll(sink))
    assertEquals("abcdef", sink.readUtf8())
    assertTrue(source.exhausted())
  }

  @Test fun readAllExhausted() {
    val mockSink = MockSink()
    assertEquals(0, source.readAll(mockSink))
    assertTrue(source.exhausted())
    mockSink.assertLog()
  }

  @Test fun readExhaustedSource() {
    val sink = Buffer()
    sink.writeUtf8("a".repeat(10))
    assertEquals(-1, source.read(sink, 10))
    assertEquals(10, sink.size)
    assertTrue(source.exhausted())
  }

  @Test fun readZeroBytesFromSource() {
    val sink = Buffer()
    sink.writeUtf8("a".repeat(10))

    // Either 0 or -1 is reasonable here. For consistency with Android's
    // ByteArrayInputStream we return 0.
    val readResult = source.read(sink, 0)
    assertTrue(readResult == 0L || readResult == -1L)
    assertEquals(10, sink.size)
    assertTrue(source.exhausted())
  }

  @Test fun readFully() {
    sink.writeUtf8("a".repeat(10000))
    sink.emit()
    val sink = Buffer()
    source.readFully(sink, 9999)
    assertEquals("a".repeat(9999), sink.readUtf8())
    assertEquals("a", source.readUtf8())
  }

  @Test fun readFullyTooShortThrows() {
    sink.writeUtf8("Hi")
    sink.emit()
    val sink = Buffer()
    assertFailsWith<EOFException> {
      source.readFully(sink, 5)
    }

    // Verify we read all that we could from the source.
    assertEquals("Hi", sink.readUtf8())
  }

  @Test fun readFullyByteArray() {
    val data = Buffer()
    data.writeUtf8("Hello").writeUtf8("e".repeat(Segment.SIZE))

    val expected = data.copy().readByteArray()
    sink.write(data, data.size)
    sink.emit()

    val sink = ByteArray(Segment.SIZE + 5)
    source.readFully(sink)
    assertArrayEquals(expected, sink)
  }

  @Test fun readFullyByteArrayTooShortThrows() {
    sink.writeUtf8("Hello")
    sink.emit()

    val array = ByteArray(6)
    assertFailsWith<EOFException> {
      source.readFully(array)
    }

    // Verify we read all that we could from the source.
    assertArrayEquals(
      byteArrayOf(
        'H'.code.toByte(),
        'e'.code.toByte(),
        'l'.code.toByte(),
        'l'.code.toByte(),
        'o'.code.toByte(),
        0,
      ),
      array,
    )
  }

  @Test fun readIntoByteArray() {
    sink.writeUtf8("abcd")
    sink.emit()

    val sink = ByteArray(3)
    val read = source.read(sink)
    if (factory.isOneByteAtATime) {
      assertEquals(1, read.toLong())
      val expected = byteArrayOf('a'.code.toByte(), 0, 0)
      assertArrayEquals(expected, sink)
    } else {
      assertEquals(3, read.toLong())
      val expected = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
      assertArrayEquals(expected, sink)
    }
  }

  @Test fun readIntoByteArrayNotEnough() {
    sink.writeUtf8("abcd")
    sink.emit()

    val sink = ByteArray(5)
    val read = source.read(sink)
    if (factory.isOneByteAtATime) {
      assertEquals(1, read.toLong())
      val expected = byteArrayOf('a'.code.toByte(), 0, 0, 0, 0)
      assertArrayEquals(expected, sink)
    } else {
      assertEquals(4, read.toLong())
      val expected =
        byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte(), 0)
      assertArrayEquals(expected, sink)
    }
  }

  @Test fun readIntoByteArrayOffsetAndCount() {
    sink.writeUtf8("abcd")
    sink.emit()

    val sink = ByteArray(7)
    val read = source.read(sink, 2, 3)
    if (factory.isOneByteAtATime) {
      assertEquals(1, read.toLong())
      val expected = byteArrayOf(0, 0, 'a'.code.toByte(), 0, 0, 0, 0)
      assertArrayEquals(expected, sink)
    } else {
      assertEquals(3, read.toLong())
      val expected =
        byteArrayOf(0, 0, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0, 0)
      assertArrayEquals(expected, sink)
    }
  }

  @Test fun readByteArray() {
    val string = "abcd" + "e".repeat(Segment.SIZE)
    sink.writeUtf8(string)
    sink.emit()
    assertArrayEquals(string.asUtf8ToByteArray(), source.readByteArray())
  }

  @Test fun readByteArrayPartial() {
    sink.writeUtf8("abcd")
    sink.emit()
    assertEquals("[97, 98, 99]", source.readByteArray(3).contentToString())
    assertEquals("d", source.readUtf8(1))
  }

  @Test fun readByteArrayTooShortThrows() {
    sink.writeUtf8("abc")
    sink.emit()
    assertFailsWith<EOFException> {
      source.readByteArray(4)
    }

    assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
  }

  @Test fun readByteString() {
    sink.writeUtf8("abcd").writeUtf8("e".repeat(Segment.SIZE))
    sink.emit()
    assertEquals("abcd" + "e".repeat(Segment.SIZE), source.readByteString().utf8())
  }

  @Test fun readByteStringPartial() {
    sink.writeUtf8("abcd").writeUtf8("e".repeat(Segment.SIZE))
    sink.emit()
    assertEquals("abc", source.readByteString(3).utf8())
    assertEquals("d", source.readUtf8(1))
  }

  @Test fun readByteStringTooShortThrows() {
    sink.writeUtf8("abc")
    sink.emit()
    assertFailsWith<EOFException> {
      source.readByteString(4)
    }

    assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
  }

  @Test fun readUtf8SpansSegments() {
    sink.writeUtf8("a".repeat(Segment.SIZE * 2))
    sink.emit()
    source.skip((Segment.SIZE - 1).toLong())
    assertEquals("aa", source.readUtf8(2))
  }

  @Test fun readUtf8Segment() {
    sink.writeUtf8("a".repeat(Segment.SIZE))
    sink.emit()
    assertEquals("a".repeat(Segment.SIZE), source.readUtf8(Segment.SIZE.toLong()))
  }

  @Test fun readUtf8PartialBuffer() {
    sink.writeUtf8("a".repeat(Segment.SIZE + 20))
    sink.emit()
    assertEquals("a".repeat(Segment.SIZE + 10), source.readUtf8((Segment.SIZE + 10).toLong()))
  }

  @Test fun readUtf8EntireBuffer() {
    sink.writeUtf8("a".repeat(Segment.SIZE * 2))
    sink.emit()
    assertEquals("a".repeat(Segment.SIZE * 2), source.readUtf8())
  }

  @Test fun readUtf8TooShortThrows() {
    sink.writeUtf8("abc")
    sink.emit()
    assertFailsWith<EOFException> {
      source.readUtf8(4L)
    }

    assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
  }

  @Test fun skip() {
    sink.writeUtf8("a")
    sink.writeUtf8("b".repeat(Segment.SIZE))
    sink.writeUtf8("c")
    sink.emit()
    source.skip(1)
    assertEquals('b'.code.toLong(), (source.readByte() and 0xff).toLong())
    source.skip((Segment.SIZE - 2).toLong())
    assertEquals('b'.code.toLong(), (source.readByte() and 0xff).toLong())
    source.skip(1)
    assertTrue(source.exhausted())
  }

  @Test fun skipInsufficientData() {
    sink.writeUtf8("a")
    sink.emit()
    assertFailsWith<EOFException> {
      source.skip(2)
    }
  }

  @Test fun indexOf() {
    // The segment is empty.
    assertEquals(-1, source.indexOf('a'.code.toByte()))

    // The segment has one value.
    sink.writeUtf8("a") // a
    sink.emit()
    assertEquals(0, source.indexOf('a'.code.toByte()))
    assertEquals(-1, source.indexOf('b'.code.toByte()))

    // The segment has lots of data.
    sink.writeUtf8("b".repeat(Segment.SIZE - 2)) // ab...b
    sink.emit()
    assertEquals(0, source.indexOf('a'.code.toByte()))
    assertEquals(1, source.indexOf('b'.code.toByte()))
    assertEquals(-1, source.indexOf('c'.code.toByte()))

    // The segment doesn't start at 0, it starts at 2.
    source.skip(2) // b...b
    assertEquals(-1, source.indexOf('a'.code.toByte()))
    assertEquals(0, source.indexOf('b'.code.toByte()))
    assertEquals(-1, source.indexOf('c'.code.toByte()))

    // The segment is full.
    sink.writeUtf8("c") // b...bc
    sink.emit()
    assertEquals(-1, source.indexOf('a'.code.toByte()))
    assertEquals(0, source.indexOf('b'.code.toByte()))
    assertEquals((Segment.SIZE - 3).toLong(), source.indexOf('c'.code.toByte()))

    // The segment doesn't start at 2, it starts at 4.
    source.skip(2) // b...bc
    assertEquals(-1, source.indexOf('a'.code.toByte()))
    assertEquals(0, source.indexOf('b'.code.toByte()))
    assertEquals((Segment.SIZE - 5).toLong(), source.indexOf('c'.code.toByte()))

    // Two segments.
    sink.writeUtf8("d") // b...bcd, d is in the 2nd segment.
    sink.emit()
    assertEquals((Segment.SIZE - 4).toLong(), source.indexOf('d'.code.toByte()))
    assertEquals(-1, source.indexOf('e'.code.toByte()))
  }

  @Test fun indexOfByteWithStartOffset() {
    sink.writeUtf8("a").writeUtf8("b".repeat(Segment.SIZE)).writeUtf8("c")
    sink.emit()
    assertEquals(-1, source.indexOf('a'.code.toByte(), 1))
    assertEquals(15, source.indexOf('b'.code.toByte(), 15))
  }

  @Test fun indexOfByteWithBothOffsets() {
    if (factory.isOneByteAtATime) {
      // When run on CI this causes out-of-memory errors.
      return
    }
    val a = 'a'.code.toByte()
    val c = 'c'.code.toByte()

    val size = Segment.SIZE * 5
    val bytes = ByteArray(size) { a }

    // These are tricky places where the buffer
    // starts, ends, or segments come together.
    val points = intArrayOf(
      0,
      1,
      2,
      Segment.SIZE - 1,
      Segment.SIZE,
      Segment.SIZE + 1,
      size / 2 - 1,
      size / 2,
      size / 2 + 1,
      size - Segment.SIZE - 1,
      size - Segment.SIZE,
      size - Segment.SIZE + 1,
      size - 3,
      size - 2,
      size - 1,
    )

    // In each iteration, we write c to the known point and then search for it using different
    // windows. Some of the windows don't overlap with c's position, and therefore a match shouldn't
    // be found.
    for (p in points) {
      bytes[p] = c
      sink.write(bytes)
      sink.emit()

      assertEquals(p.toLong(), source.indexOf(c, 0, size.toLong()))
      assertEquals(p.toLong(), source.indexOf(c, 0, (p + 1).toLong()))
      assertEquals(p.toLong(), source.indexOf(c, p.toLong(), size.toLong()))
      assertEquals(p.toLong(), source.indexOf(c, p.toLong(), (p + 1).toLong()))
      assertEquals(p.toLong(), source.indexOf(c, (p / 2).toLong(), (p * 2 + 1).toLong()))
      assertEquals(-1, source.indexOf(c, 0, (p / 2).toLong()))
      assertEquals(-1, source.indexOf(c, 0, p.toLong()))
      assertEquals(-1, source.indexOf(c, 0, 0))
      assertEquals(-1, source.indexOf(c, p.toLong(), p.toLong()))

      // Reset.
      source.readUtf8()
      bytes[p] = a
    }
  }

  @Test fun indexOfByteInvalidBoundsThrows() {
    sink.writeUtf8("abc")
    sink.emit()
    assertFailsWith<IllegalArgumentException>("Expected failure: fromIndex < 0") {
      source.indexOf('a'.code.toByte(), -1)
    }
    assertFailsWith<IllegalArgumentException>("Expected failure: fromIndex > toIndex") {
      source.indexOf('a'.code.toByte(), 10, 0)
    }
  }

  @Test fun indexOfByteString() {
    assertEquals(-1, source.indexOf("flop".encodeUtf8()))

    sink.writeUtf8("flip flop")
    sink.emit()
    assertEquals(5, source.indexOf("flop".encodeUtf8()))
    source.readUtf8() // Clear stream.

    // Make sure we backtrack and resume searching after partial match.
    sink.writeUtf8("hi hi hi hey")
    sink.emit()
    assertEquals(3, source.indexOf("hi hi hey".encodeUtf8()))
  }

  @Test fun indexOfByteStringAtSegmentBoundary() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 1))
    sink.writeUtf8("bcd")
    sink.emit()
    assertEquals(
      (Segment.SIZE - 3).toLong(),
      source.indexOf("aabc".encodeUtf8(), (Segment.SIZE - 4).toLong()),
    )
    assertEquals(
      (Segment.SIZE - 3).toLong(),
      source.indexOf("aabc".encodeUtf8(), (Segment.SIZE - 3).toLong()),
    )
    assertEquals(
      (Segment.SIZE - 2).toLong(),
      source.indexOf("abcd".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      (Segment.SIZE - 2).toLong(),
      source.indexOf("abc".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      (Segment.SIZE - 2).toLong(),
      source.indexOf("abc".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      (Segment.SIZE - 2).toLong(),
      source.indexOf("ab".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      (Segment.SIZE - 2).toLong(),
      source.indexOf("a".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      (Segment.SIZE - 1).toLong(),
      source.indexOf("bc".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      (Segment.SIZE - 1).toLong(),
      source.indexOf("b".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      Segment.SIZE.toLong(),
      source.indexOf("c".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      Segment.SIZE.toLong(),
      source.indexOf("c".encodeUtf8(), Segment.SIZE.toLong()),
    )
    assertEquals(
      (Segment.SIZE + 1).toLong(),
      source.indexOf("d".encodeUtf8(), (Segment.SIZE - 2).toLong()),
    )
    assertEquals(
      (Segment.SIZE + 1).toLong(),
      source.indexOf("d".encodeUtf8(), (Segment.SIZE + 1).toLong()),
    )
  }

  @Test fun indexOfDoesNotWrapAround() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 1))
    sink.writeUtf8("bcd")
    sink.emit()
    assertEquals(-1, source.indexOf("abcda".encodeUtf8(), (Segment.SIZE - 3).toLong()))
  }

  @Test fun indexOfByteStringWithOffset() {
    assertEquals(-1, source.indexOf("flop".encodeUtf8(), 1))

    sink.writeUtf8("flop flip flop")
    sink.emit()
    assertEquals(10, source.indexOf("flop".encodeUtf8(), 1))
    source.readUtf8() // Clear stream

    // Make sure we backtrack and resume searching after partial match.
    sink.writeUtf8("hi hi hi hi hey")
    sink.emit()
    assertEquals(6, source.indexOf("hi hi hey".encodeUtf8(), 1))
  }

  @Test fun indexOfByteStringInvalidArgumentsThrows() {
    var e = assertFailsWith<IllegalArgumentException> {
      source.indexOf(ByteString.of())
    }
    assertEquals("byteCount == 0", e.message)

    e = assertFailsWith<IllegalArgumentException> {
      source.indexOf("hi".encodeUtf8(), -1)
    }
    assertEquals("fromIndex < 0: -1", e.message)
  }

  /**
   * With [BufferedSourceFactory.OneByteAtATimeSource], this code was extremely slow.
   * https://github.com/lysine-dev/okio/issues/171
   */
  @Test fun indexOfByteStringAcrossSegmentBoundaries() {
    sink.writeUtf8("a".repeat(Segment.SIZE * 2 - 3))
    sink.writeUtf8("bcdefg")
    sink.emit()
    assertEquals((Segment.SIZE * 2 - 4).toLong(), source.indexOf("ab".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 - 4).toLong(), source.indexOf("abc".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 - 4).toLong(), source.indexOf("abcd".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 - 4).toLong(), source.indexOf("abcde".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 - 4).toLong(), source.indexOf("abcdef".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 - 4).toLong(), source.indexOf("abcdefg".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 - 3).toLong(), source.indexOf("bcdefg".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 - 2).toLong(), source.indexOf("cdefg".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 - 1).toLong(), source.indexOf("defg".encodeUtf8()))
    assertEquals((Segment.SIZE * 2).toLong(), source.indexOf("efg".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 + 1).toLong(), source.indexOf("fg".encodeUtf8()))
    assertEquals((Segment.SIZE * 2 + 2).toLong(), source.indexOf("g".encodeUtf8()))
  }

  @Test fun indexOfElement() {
    sink.writeUtf8("a").writeUtf8("b".repeat(Segment.SIZE)).writeUtf8("c")
    sink.emit()
    assertEquals(0, source.indexOfElement("DEFGaHIJK".encodeUtf8()))
    assertEquals(1, source.indexOfElement("DEFGHIJKb".encodeUtf8()))
    assertEquals((Segment.SIZE + 1).toLong(), source.indexOfElement("cDEFGHIJK".encodeUtf8()))
    assertEquals(1, source.indexOfElement("DEFbGHIc".encodeUtf8()))
    assertEquals(-1L, source.indexOfElement("DEFGHIJK".encodeUtf8()))
    assertEquals(-1L, source.indexOfElement("".encodeUtf8()))
  }

  @Test fun indexOfElementWithOffset() {
    sink.writeUtf8("a").writeUtf8("b".repeat(Segment.SIZE)).writeUtf8("c")
    sink.emit()
    assertEquals(-1, source.indexOfElement("DEFGaHIJK".encodeUtf8(), 1))
    assertEquals(15, source.indexOfElement("DEFGHIJKb".encodeUtf8(), 15))
  }

  @Test fun indexOfByteWithFromIndex() {
    sink.writeUtf8("aaa")
    sink.emit()
    assertEquals(0, source.indexOf('a'.code.toByte()))
    assertEquals(0, source.indexOf('a'.code.toByte(), 0))
    assertEquals(1, source.indexOf('a'.code.toByte(), 1))
    assertEquals(2, source.indexOf('a'.code.toByte(), 2))
  }

  @Test fun indexOfByteStringWithFromIndex() {
    sink.writeUtf8("aaa")
    sink.emit()
    assertEquals(0, source.indexOf("a".encodeUtf8()))
    assertEquals(0, source.indexOf("a".encodeUtf8(), 0))
    assertEquals(1, source.indexOf("a".encodeUtf8(), 1))
    assertEquals(2, source.indexOf("a".encodeUtf8(), 2))
  }

  @Test fun indexOfElementWithFromIndex() {
    sink.writeUtf8("aaa")
    sink.emit()
    assertEquals(0, source.indexOfElement("a".encodeUtf8()))
    assertEquals(0, source.indexOfElement("a".encodeUtf8(), 0))
    assertEquals(1, source.indexOfElement("a".encodeUtf8(), 1))
    assertEquals(2, source.indexOfElement("a".encodeUtf8(), 2))
  }

  @Test fun request() {
    sink.writeUtf8("a").writeUtf8("b".repeat(Segment.SIZE)).writeUtf8("c")
    sink.emit()
    assertTrue(source.request((Segment.SIZE + 2).toLong()))
    assertFalse(source.request((Segment.SIZE + 3).toLong()))
  }

  @Test fun require() {
    sink.writeUtf8("a").writeUtf8("b".repeat(Segment.SIZE)).writeUtf8("c")
    sink.emit()
    source.require((Segment.SIZE + 2).toLong())
    assertFailsWith<EOFException> {
      source.require((Segment.SIZE + 3).toLong())
    }
  }

  @Test fun longHexString() {
    assertLongHexString("8000000000000000", Long.MIN_VALUE)
    assertLongHexString("fffffffffffffffe", -0x2L)
    assertLongHexString("FFFFFFFFFFFFFFFe", -0x2L)
    assertLongHexString("ffffffffffffffff", -0x1L)
    assertLongHexString("FFFFFFFFFFFFFFFF", -0x1L)
    assertLongHexString("0000000000000000", 0x0L)
    assertLongHexString("0000000000000001", 0x1L)
    assertLongHexString("7999999999999999", 0x7999999999999999L)

    assertLongHexString("FF", 0xFF)
    assertLongHexString("0000000000000001", 0x1)
  }

  @Test fun hexStringWithManyLeadingZeros() {
    assertLongHexString("00000000000000001", 0x1)
    assertLongHexString("0000000000000000ffffffffffffffff", -0x1L)
    assertLongHexString("00000000000000007fffffffffffffff", 0x7fffffffffffffffL)
    assertLongHexString("0".repeat(Segment.SIZE + 1) + "1", 0x1)
  }

  private fun assertLongHexString(s: String, expected: Long) {
    sink.writeUtf8(s)
    sink.emit()
    val actual = source.readHexadecimalUnsignedLong()
    assertEquals(expected, actual, "$s --> $expected")
  }

  @Test fun longHexStringAcrossSegment() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 8)).writeUtf8("FFFFFFFFFFFFFFFF")
    sink.emit()
    source.skip((Segment.SIZE - 8).toLong())
    assertEquals(-1, source.readHexadecimalUnsignedLong())
  }

  @Test fun longHexStringTooLongThrows() {
    sink.writeUtf8("fffffffffffffffff")
    sink.emit()

    val e = assertFailsWith<NumberFormatException> {
      source.readHexadecimalUnsignedLong()
    }
    assertEquals("Number too large: fffffffffffffffff", e.message)
  }

  @Test fun longHexStringTooShortThrows() {
    sink.writeUtf8(" ")
    sink.emit()

    val e = assertFailsWith<NumberFormatException> {
      source.readHexadecimalUnsignedLong()
    }
    assertEquals("Expected leading [0-9a-fA-F] character but was 0x20", e.message)
  }

  @Test fun longHexEmptySourceThrows() {
    sink.writeUtf8("")
    sink.emit()
    assertFailsWith<EOFException> { source.readHexadecimalUnsignedLong() }
  }

  @Test fun longDecimalString() {
    assertLongDecimalString("-9223372036854775808", Long.MIN_VALUE)
    assertLongDecimalString("-1", -1L)
    assertLongDecimalString("0", 0L)
    assertLongDecimalString("1", 1L)
    assertLongDecimalString("9223372036854775807", Long.MAX_VALUE)

    assertLongDecimalString("00000001", 1L)
    assertLongDecimalString("-000001", -1L)
  }

  private fun assertLongDecimalString(s: String, expected: Long) {
    sink.writeUtf8(s)
    sink.writeUtf8("zzz")
    sink.emit()
    val actual = source.readDecimalLong()
    assertEquals(expected, actual, "$s --> $expected")
    assertEquals("zzz", source.readUtf8())
  }

  @Test fun longDecimalStringAcrossSegment() {
    sink.writeUtf8("a".repeat(Segment.SIZE - 8)).writeUtf8("1234567890123456")
    sink.writeUtf8("zzz")
    sink.emit()
    source.skip((Segment.SIZE - 8).toLong())
    assertEquals(1234567890123456L, source.readDecimalLong())
    assertEquals("zzz", source.readUtf8())
  }

  @Test fun longDecimalStringTooLongThrows() {
    sink.writeUtf8("12345678901234567890") // Too many digits.
    sink.emit()

    val e = assertFailsWith<NumberFormatException> {
      source.readDecimalLong()
    }
    assertEquals("Number too large: 12345678901234567890", e.message)
  }

  @Test fun longDecimalStringTooHighThrows() {
    sink.writeUtf8("9223372036854775808") // Right size but cannot fit.
    sink.emit()

    val e = assertFailsWith<NumberFormatException> {
      source.readDecimalLong()
    }
    assertEquals("Number too large: 9223372036854775808", e.message)
  }

  @Test fun longDecimalStringTooLowThrows() {
    sink.writeUtf8("-9223372036854775809") // Right size but cannot fit.
    sink.emit()

    val e = assertFailsWith<NumberFormatException> {
      source.readDecimalLong()
    }
    assertEquals("Number too large: -9223372036854775809", e.message)
  }

  @Test fun longDecimalStringTooShortThrows() {
    sink.writeUtf8(" ")
    sink.emit()

    val e = assertFailsWith<NumberFormatException> {
      source.readDecimalLong()
    }
    assertEquals("Expected a digit or '-' but was 0x20", e.message)
  }

  @Test fun longDecimalEmptyThrows() {
    sink.writeUtf8("")
    sink.emit()
    assertFailsWith<EOFException> {
      source.readDecimalLong()
    }
  }

  @Test fun longDecimalLoneDashThrows() {
    sink.writeUtf8("-")
    sink.emit()
    assertFailsWith<EOFException> {
      source.readDecimalLong()
    }
  }

  @Test fun longDecimalDashFollowedByNonDigitThrows() {
    sink.writeUtf8("- ")
    sink.emit()
    assertFailsWith<NumberFormatException> {
      source.readDecimalLong()
    }
  }

  @Test fun codePoints() {
    sink.write("7f".decodeHex())
    sink.emit()
    assertEquals(0x7f, source.readUtf8CodePoint().toLong())

    sink.write("dfbf".decodeHex())
    sink.emit()
    assertEquals(0x07ff, source.readUtf8CodePoint().toLong())

    sink.write("efbfbf".decodeHex())
    sink.emit()
    assertEquals(0xffff, source.readUtf8CodePoint().toLong())

    sink.write("f48fbfbf".decodeHex())
    sink.emit()
    assertEquals(0x10ffff, source.readUtf8CodePoint().toLong())
  }

  @Test fun decimalStringWithManyLeadingZeros() {
    assertLongDecimalString("00000000000000001", 1)
    assertLongDecimalString("00000000000000009223372036854775807", Long.MAX_VALUE)
    assertLongDecimalString("-00000000000000009223372036854775808", Long.MIN_VALUE)
    assertLongDecimalString("0".repeat(Segment.SIZE + 1) + "1", 1)
  }

  @Test fun select() {
    val options = Options.of(
      "ROCK".encodeUtf8(),
      "SCISSORS".encodeUtf8(),
      "PAPER".encodeUtf8(),
    )

    sink.writeUtf8("PAPER,SCISSORS,ROCK")
    sink.emit()
    assertEquals(2, source.select(options).toLong())
    assertEquals(','.code.toLong(), source.readByte().toLong())
    assertEquals(1, source.select(options).toLong())
    assertEquals(','.code.toLong(), source.readByte().toLong())
    assertEquals(0, source.select(options).toLong())
    assertTrue(source.exhausted())
  }

  /** Note that this test crashes the VM on Android. */
  @Test fun selectSpanningMultipleSegments() {
    if (factory.isOneByteAtATime && isBrowser()) {
      return // This test times out on browsers.
    }
    val commonPrefix = randomBytes(Segment.SIZE + 10)
    val a = Buffer().write(commonPrefix).writeUtf8("a").readByteString()
    val bc = Buffer().write(commonPrefix).writeUtf8("bc").readByteString()
    val bd = Buffer().write(commonPrefix).writeUtf8("bd").readByteString()
    val options = Options.of(a, bc, bd)

    sink.write(bd)
    sink.write(a)
    sink.write(bc)
    sink.emit()

    assertEquals(2, source.select(options).toLong())
    assertEquals(0, source.select(options).toLong())
    assertEquals(1, source.select(options).toLong())
    assertTrue(source.exhausted())
  }

  @Test fun selectNotFound() {
    val options = Options.of(
      "ROCK".encodeUtf8(),
      "SCISSORS".encodeUtf8(),
      "PAPER".encodeUtf8(),
    )

    sink.writeUtf8("SPOCK")
    sink.emit()
    assertEquals(-1, source.select(options).toLong())
    assertEquals("SPOCK", source.readUtf8())
  }

  @Test fun selectValuesHaveCommonPrefix() {
    val options = Options.of(
      "abcd".encodeUtf8(),
      "abce".encodeUtf8(),
      "abcc".encodeUtf8(),
    )

    sink.writeUtf8("abcc").writeUtf8("abcd").writeUtf8("abce")
    sink.emit()
    assertEquals(2, source.select(options).toLong())
    assertEquals(0, source.select(options).toLong())
    assertEquals(1, source.select(options).toLong())
  }

  @Test fun selectLongerThanSource() {
    val options = Options.of(
      "abcd".encodeUtf8(),
      "abce".encodeUtf8(),
      "abcc".encodeUtf8(),
    )
    sink.writeUtf8("abc")
    sink.emit()
    assertEquals(-1, source.select(options).toLong())
    assertEquals("abc", source.readUtf8())
  }

  @Test fun selectReturnsFirstByteStringThatMatches() {
    val options = Options.of(
      "abcd".encodeUtf8(),
      "abc".encodeUtf8(),
      "abcde".encodeUtf8(),
    )
    sink.writeUtf8("abcdef")
    sink.emit()
    assertEquals(0, source.select(options).toLong())
    assertEquals("ef", source.readUtf8())
  }

  @Test fun selectFromEmptySource() {
    val options = Options.of(
      "abc".encodeUtf8(),
      "def".encodeUtf8(),
    )
    assertEquals(-1, source.select(options).toLong())
  }

  @Test fun selectNoByteStringsFromEmptySource() {
    val options = Options.of()
    assertEquals(-1, source.select(options).toLong())
  }

  @Test fun peek() {
    sink.writeUtf8("abcdefghi")
    sink.emit()

    assertEquals("abc", source.readUtf8(3))

    val peek = source.peek()
    assertEquals("def", peek.readUtf8(3))
    assertEquals("ghi", peek.readUtf8(3))
    assertFalse(peek.request(1))

    assertEquals("def", source.readUtf8(3))
  }

  @Test fun peekMultiple() {
    sink.writeUtf8("abcdefghi")
    sink.emit()

    assertEquals("abc", source.readUtf8(3))

    val peek1 = source.peek()
    val peek2 = source.peek()

    assertEquals("def", peek1.readUtf8(3))

    assertEquals("def", peek2.readUtf8(3))
    assertEquals("ghi", peek2.readUtf8(3))
    assertFalse(peek2.request(1))

    assertEquals("ghi", peek1.readUtf8(3))
    assertFalse(peek1.request(1))

    assertEquals("def", source.readUtf8(3))
  }

  @Test fun peekLarge() {
    if (factory.isOneByteAtATime) {
      // When run on CI this causes out-of-memory errors.
      return
    }
    sink.writeUtf8("abcdef")
    sink.writeUtf8("g".repeat(2 * Segment.SIZE))
    sink.writeUtf8("hij")
    sink.emit()

    assertEquals("abc", source.readUtf8(3))

    val peek = source.peek()
    assertEquals("def", peek.readUtf8(3))
    peek.skip((2 * Segment.SIZE).toLong())
    assertEquals("hij", peek.readUtf8(3))
    assertFalse(peek.request(1))

    assertEquals("def", source.readUtf8(3))
    source.skip((2 * Segment.SIZE).toLong())
    assertEquals("hij", source.readUtf8(3))
  }

  @Test fun peekInvalid() {
    sink.writeUtf8("abcdefghi")
    sink.emit()

    assertEquals("abc", source.readUtf8(3))

    val peek = source.peek()
    assertEquals("def", peek.readUtf8(3))
    assertEquals("ghi", peek.readUtf8(3))
    assertFalse(peek.request(1))

    assertEquals("def", source.readUtf8(3))

    val e = assertFailsWith<IllegalStateException> {
      peek.readUtf8()
    }
    assertEquals("Peek source is invalid because upstream source was used", e.message)
  }

  @Test fun peekSegmentThenInvalid() {
    sink.writeUtf8("abc")
    sink.writeUtf8("d".repeat(2 * Segment.SIZE))
    sink.emit()

    assertEquals("abc", source.readUtf8(3))

    // Peek a little data and skip the rest of the upstream source
    val peek = source.peek()
    assertEquals("ddd", peek.readUtf8(3))
    source.readAll(blackholeSink())

    // Skip the rest of the buffered data
    peek.skip(peek.buffer.size)

    val e = assertFailsWith<IllegalStateException> {
      peek.readByte()
    }
    assertEquals("Peek source is invalid because upstream source was used", e.message)
  }

  @Test fun peekDoesntReadTooMuch() {
    // 6 bytes in source's buffer plus 3 bytes upstream.
    sink.writeUtf8("abcdef")
    sink.emit()
    source.require(6L)
    sink.writeUtf8("ghi")
    sink.emit()

    val peek = source.peek()

    // Read 3 bytes. This reads some of the buffered data.
    assertTrue(peek.request(3))
    if (source !is Buffer) {
      assertEquals(6, source.buffer.size)
      assertEquals(6, peek.buffer.size)
    }
    assertEquals("abc", peek.readUtf8(3L))

    // Read 3 more bytes. This exhausts the buffered data.
    assertTrue(peek.request(3))
    if (source !is Buffer) {
      assertEquals(6, source.buffer.size)
      assertEquals(3, peek.buffer.size)
    }
    assertEquals("def", peek.readUtf8(3L))

    // Read 3 more bytes. This draws new bytes.
    assertTrue(peek.request(3))
    assertEquals(9, source.buffer.size)
    assertEquals(3, peek.buffer.size)
    assertEquals("ghi", peek.readUtf8(3L))
  }

  @Test fun rangeEquals() {
    sink.writeUtf8("A man, a plan, a canal. Panama.")
    sink.emit()
    assertTrue(source.rangeEquals(7, "a plan".encodeUtf8()))
    assertTrue(source.rangeEquals(0, "A man".encodeUtf8()))
    assertTrue(source.rangeEquals(24, "Panama".encodeUtf8()))
    assertFalse(source.rangeEquals(24, "Panama. Panama. Panama.".encodeUtf8()))
  }

  @Test fun rangeEqualsWithOffsetAndCount() {
    sink.writeUtf8("A man, a plan, a canal. Panama.")
    sink.emit()
    assertTrue(source.rangeEquals(7, "aaa plannn".encodeUtf8(), 2, 6))
    assertTrue(source.rangeEquals(0, "AAA mannn".encodeUtf8(), 2, 5))
    assertTrue(source.rangeEquals(24, "PPPanamaaa".encodeUtf8(), 2, 6))
  }

  @Test fun rangeEqualsOnlyReadsUntilMismatch() {
    if (factory !== BufferedSourceFactory.OneByteAtATimeSource) return // Other sources read in chunks anyway.

    sink.writeUtf8("A man, a plan, a canal. Panama.")
    sink.emit()
    assertFalse(source.rangeEquals(0, ("A man.").encodeUtf8()))
    assertEquals("A man,", source.buffer.readUtf8())
  }

  @Test fun rangeEqualsArgumentValidation() {
    // Negative source offset.
    assertFalse(source.rangeEquals(-1, "A".encodeUtf8()))
    // Negative bytes offset.
    assertFalse(source.rangeEquals(0, "A".encodeUtf8(), -1, 1))
    // Bytes offset longer than bytes length.
    assertFalse(source.rangeEquals(0, "A".encodeUtf8(), 2, 1))
    // Negative byte count.
    assertFalse(source.rangeEquals(0, "A".encodeUtf8(), 0, -1))
    // Byte count longer than bytes length.
    assertFalse(source.rangeEquals(0, "A".encodeUtf8(), 0, 2))
    // Bytes offset plus byte count longer than bytes length.
    assertFalse(source.rangeEquals(0, "A".encodeUtf8(), 1, 1))
  }

  @Test fun factorySegmentSizes() {
    sink.writeUtf8("abc")
    sink.emit()
    source.require(3)
    if (factory.isOneByteAtATime) {
      assertEquals(listOf(1, 1, 1), segmentSizes(source.buffer))
    } else {
      assertEquals(listOf(3), segmentSizes(source.buffer))
    }
  }
}
