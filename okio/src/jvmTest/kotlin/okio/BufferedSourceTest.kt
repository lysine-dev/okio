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
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import de.infix.testBalloon.framework.core.testSuite
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Arrays
import kotlin.text.Charsets.US_ASCII
import kotlin.text.Charsets.UTF_8
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.Options.Companion.of
import okio.TestUtil.SEGMENT_SIZE
import okio.TestUtil.assertByteArrayEquals
import okio.TestUtil.assertByteArraysEquals
import okio.TestUtil.randomBytes
import okio.TestUtil.segmentSizes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

val BufferedSourceTest by testSuite {
  for (factory in SourceFactory.entries) {
    testSuite(factory.name) {
      testFixture { BufferedSourceFixture(factory) } asContextForEach {
        test("readBytes") {
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte()))
          sink.emit()
          assertEquals(0xab, (source.readByte().toInt() and 0xff).toLong())
          assertEquals(0xcd, (source.readByte().toInt() and 0xff).toLong())
          assertTrue(source.exhausted())
        }

        test("readByteTooShortThrows") {
          try {
            source.readByte()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("readShort") {
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
          sink.emit()
          assertEquals(0xabcd.toShort().toLong(), source.readShort().toLong())
          assertEquals(0xef01.toShort().toLong(), source.readShort().toLong())
          assertTrue(source.exhausted())
        }

        test("readShortLe") {
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte()))
          sink.emit()
          assertEquals(0xcdab.toShort().toLong(), source.readShortLe().toLong())
          assertEquals(0x10ef.toShort().toLong(), source.readShortLe().toLong())
          assertTrue(source.exhausted())
        }

        test("readShortSplitAcrossMultipleSegments") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE - 1))
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte()))
          sink.emit()
          source.skip((SEGMENT_SIZE - 1).toLong())
          assertEquals(0xabcd.toShort().toLong(), source.readShort().toLong())
          assertTrue(source.exhausted())
        }

        test("readShortTooShortThrows") {
          sink.writeShort(Short.MAX_VALUE.toInt())
          sink.emit()
          source.readByte()
          try {
            source.readShort()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("readShortLeTooShortThrows") {
          sink.writeShortLe(Short.MAX_VALUE.toInt())
          sink.emit()
          source.readByte()
          try {
            source.readShortLe()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("readInt") {
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte(), 0x87.toByte(), 0x65.toByte(), 0x43.toByte(), 0x21.toByte()))
          sink.emit()
          assertEquals(-0x543210ff, source.readInt().toLong())
          assertEquals(-0x789abcdf, source.readInt().toLong())
          assertTrue(source.exhausted())
        }

        test("readIntLe") {
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte(), 0x87.toByte(), 0x65.toByte(), 0x43.toByte(), 0x21.toByte()))
          sink.emit()
          assertEquals(0x10efcdab, source.readIntLe().toLong())
          assertEquals(0x21436587, source.readIntLe().toLong())
          assertTrue(source.exhausted())
        }

        test("readIntSplitAcrossMultipleSegments") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE - 3))
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
          sink.emit()
          source.skip((SEGMENT_SIZE - 3).toLong())
          assertEquals(-0x543210ff, source.readInt().toLong())
          assertTrue(source.exhausted())
        }

        test("readIntTooShortThrows") {
          sink.writeInt(Int.MAX_VALUE)
          sink.emit()
          source.readByte()
          try {
            source.readInt()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("readIntLeTooShortThrows") {
          sink.writeIntLe(Int.MAX_VALUE)
          sink.emit()
          source.readByte()
          try {
            source.readIntLe()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("readLong") {
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte(), 0x87.toByte(), 0x65.toByte(), 0x43.toByte(), 0x21.toByte(), 0x36.toByte(), 0x47.toByte(), 0x58.toByte(), 0x69.toByte(), 0x12.toByte(), 0x23.toByte(), 0x34.toByte(), 0x45.toByte()))
          sink.emit()
          assertEquals(-0x543210ef789abcdfL, source.readLong())
          assertEquals(0x3647586912233445L, source.readLong())
          assertTrue(source.exhausted())
        }

        test("readLongLe") {
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte(), 0x87.toByte(), 0x65.toByte(), 0x43.toByte(), 0x21.toByte(), 0x36.toByte(), 0x47.toByte(), 0x58.toByte(), 0x69.toByte(), 0x12.toByte(), 0x23.toByte(), 0x34.toByte(), 0x45.toByte()))
          sink.emit()
          assertEquals(0x2143658710efcdabL, source.readLongLe())
          assertEquals(0x4534231269584736L, source.readLongLe())
          assertTrue(source.exhausted())
        }

        test("readLongSplitAcrossMultipleSegments") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE - 7))
          sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte(), 0x87.toByte(), 0x65.toByte(), 0x43.toByte(), 0x21.toByte()))
          sink.emit()
          source.skip((SEGMENT_SIZE - 7).toLong())
          assertEquals(-0x543210fe789abcdfL, source.readLong())
          assertTrue(source.exhausted())
        }

        test("readLongTooShortThrows") {
          sink.writeLong(Long.MAX_VALUE)
          sink.emit()
          source.readByte()
          try {
            source.readLong()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("readLongLeTooShortThrows") {
          sink.writeLongLe(Long.MAX_VALUE)
          sink.emit()
          source.readByte()
          try {
            source.readLongLe()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("readAll") {
          source.buffer.writeUtf8("abc")
          sink.writeUtf8("def")
          sink.emit()
          val sink = Buffer()
          assertEquals(6, source.readAll(sink))
          assertEquals("abcdef", sink.readUtf8())
          assertTrue(source.exhausted())
        }

        test("readAllExhausted") {
          val mockSink = MockSink()
          assertEquals(0, source.readAll(mockSink))
          assertTrue(source.exhausted())
          mockSink.assertLog()
        }

        test("readExhaustedSource") {
          val sink = Buffer()
          sink.writeUtf8("a".repeat(10))
          assertEquals(-1, source.read(sink, 10))
          assertEquals(10, sink.size)
          assertTrue(source.exhausted())
        }

        test("readZeroBytesFromSource") {
          val sink = Buffer()
          sink.writeUtf8("a".repeat(10))

          // Either 0 or -1 is reasonable here.
          val readResult = source.read(sink, 0)
          assertTrue(readResult == 0L || readResult == -1L)
          assertEquals(10, sink.size)
          assertTrue(source.exhausted())
        }

        test("readFully") {
          sink.writeUtf8("a".repeat(10000))
          sink.emit()
          val sink = Buffer()
          source.readFully(sink, 9999)
          assertEquals("a".repeat(9999), sink.readUtf8())
          assertEquals("a", source.readUtf8())
        }

        test("readFullyTooShortThrows") {
          sink.writeUtf8("Hi")
          sink.emit()
          val sink = Buffer()
          try {
            source.readFully(sink, 5)
            fail()
          } catch (ignored: EOFException) {
          }

          // Verify we read all that we could from the source.
          assertEquals("Hi", sink.readUtf8())
        }

        test("readFullyByteArray") {
          val data = Buffer()
          data.writeUtf8("Hello").writeUtf8("e".repeat(SEGMENT_SIZE))
          val expected = data.clone().readByteArray()
          sink.write(data, data.size)
          sink.emit()
          val sink = ByteArray(SEGMENT_SIZE + 5)
          source.readFully(sink)
          assertByteArraysEquals(expected, sink)
        }

        test("readFullyByteArrayTooShortThrows") {
          sink.writeUtf8("Hello")
          sink.emit()
          val array = ByteArray(6)
          try {
            source.readFully(array)
            fail()
          } catch (ignored: EOFException) {
          }

          // Verify we read all that we could from the source.
          assertByteArraysEquals(byteArrayOf('H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(), 0), array)
        }

        test("readIntoByteArray") {
          sink.writeUtf8("abcd")
          sink.emit()
          val sink = ByteArray(3)
          val read = source.read(sink)
          if (factory.isOneByteAtATime) {
            assertEquals(1, read.toLong())
            val expected = byteArrayOf('a'.code.toByte(), 0, 0)
            assertByteArraysEquals(expected, sink)
          } else {
            assertEquals(3, read.toLong())
            val expected = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
            assertByteArraysEquals(expected, sink)
          }
        }

        test("readIntoByteArrayNotEnough") {
          sink.writeUtf8("abcd")
          sink.emit()
          val sink = ByteArray(5)
          val read = source.read(sink)
          if (factory.isOneByteAtATime) {
            assertEquals(1, read.toLong())
            val expected = byteArrayOf('a'.code.toByte(), 0, 0, 0, 0)
            assertByteArraysEquals(expected, sink)
          } else {
            assertEquals(4, read.toLong())
            val expected = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte(), 0)
            assertByteArraysEquals(expected, sink)
          }
        }

        test("readIntoByteArrayOffsetAndCount") {
          sink.writeUtf8("abcd")
          sink.emit()
          val sink = ByteArray(7)
          val read = source.read(sink, 2, 3)
          if (factory.isOneByteAtATime) {
            assertEquals(1, read.toLong())
            val expected = byteArrayOf(0, 0, 'a'.code.toByte(), 0, 0, 0, 0)
            assertByteArraysEquals(expected, sink)
          } else {
            assertEquals(3, read.toLong())
            val expected = byteArrayOf(0, 0, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0, 0)
            assertByteArraysEquals(expected, sink)
          }
        }

        test("readByteArray") {
          val string = "abcd" + "e".repeat(SEGMENT_SIZE)
          sink.writeUtf8(string)
          sink.emit()
          assertByteArraysEquals(string.toByteArray(UTF_8), source.readByteArray())
        }

        test("readByteArrayPartial") {
          sink.writeUtf8("abcd")
          sink.emit()
          assertEquals("[97, 98, 99]", Arrays.toString(source.readByteArray(3)))
          assertEquals("d", source.readUtf8(1))
        }

        test("readByteArrayTooShortThrows") {
          sink.writeUtf8("abc")
          sink.emit()
          try {
            source.readByteArray(4)
            fail()
          } catch (expected: EOFException) {
          }
          assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
        }

        test("readByteString") {
          sink.writeUtf8("abcd").writeUtf8("e".repeat(SEGMENT_SIZE))
          sink.emit()
          assertEquals("abcd" + "e".repeat(SEGMENT_SIZE), source.readByteString().utf8())
        }

        test("readByteStringPartial") {
          sink.writeUtf8("abcd").writeUtf8("e".repeat(SEGMENT_SIZE))
          sink.emit()
          assertEquals("abc", source.readByteString(3).utf8())
          assertEquals("d", source.readUtf8(1))
        }

        test("readByteStringTooShortThrows") {
          sink.writeUtf8("abc")
          sink.emit()
          try {
            source.readByteString(4)
            fail()
          } catch (expected: EOFException) {
          }
          assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
        }

        test("readSpecificCharsetPartial") {
          sink.write(
            (
              "0000007600000259000002c80000006c000000e40000007300000259" +
                "000002cc000000720000006100000070000000740000025900000072"
              ).decodeHex(),
          )
          sink.emit()
          assertEquals("vəˈläsə", source.readString((7 * 4).toLong(), Charset.forName("utf-32")))
        }

        test("readSpecificCharset") {
          sink.write(
            (
              "0000007600000259000002c80000006c000000e40000007300000259" +
                "000002cc000000720000006100000070000000740000025900000072"
              ).decodeHex(),
          )
          sink.emit()
          assertEquals("vəˈläsəˌraptər", source.readString(Charset.forName("utf-32")))
        }

        test("readStringTooShortThrows") {
          sink.writeString("abc", US_ASCII)
          sink.emit()
          try {
            source.readString(4, US_ASCII)
            fail()
          } catch (expected: EOFException) {
          }
          assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
        }

        test("readUtf8SpansSegments") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
          sink.emit()
          source.skip((SEGMENT_SIZE - 1).toLong())
          assertEquals("aa", source.readUtf8(2))
        }

        test("readUtf8Segment") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE))
          sink.emit()
          assertEquals("a".repeat(SEGMENT_SIZE), source.readUtf8(SEGMENT_SIZE.toLong()))
        }

        test("readUtf8PartialBuffer") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE + 20))
          sink.emit()
          assertEquals("a".repeat(SEGMENT_SIZE + 10), source.readUtf8((SEGMENT_SIZE + 10).toLong()))
        }

        test("readUtf8EntireBuffer") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
          sink.emit()
          assertEquals("a".repeat(SEGMENT_SIZE * 2), source.readUtf8())
        }

        test("readUtf8TooShortThrows") {
          sink.writeUtf8("abc")
          sink.emit()
          try {
            source.readUtf8(4L)
            fail()
          } catch (expected: EOFException) {
          }
          assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
        }

        test("skip") {
          sink.writeUtf8("a")
          sink.writeUtf8("b".repeat(SEGMENT_SIZE))
          sink.writeUtf8("c")
          sink.emit()
          source.skip(1)
          assertEquals('b'.code.toLong(), (source.readByte().toInt() and 0xff).toLong())
          source.skip((SEGMENT_SIZE - 2).toLong())
          assertEquals('b'.code.toLong(), (source.readByte().toInt() and 0xff).toLong())
          source.skip(1)
          assertTrue(source.exhausted())
        }

        test("skipInsufficientData") {
          sink.writeUtf8("a")
          sink.emit()
          try {
            source.skip(2)
            fail()
          } catch (ignored: EOFException) {
          }
        }

        test("indexOf") {
          // The segment is empty.
          assertEquals(-1, source.indexOf('a'.code.toByte()))

          // The segment has one value.
          sink.writeUtf8("a") // a
          sink.emit()
          assertEquals(0, source.indexOf('a'.code.toByte()))
          assertEquals(-1, source.indexOf('b'.code.toByte()))

          // The segment has lots of data.
          sink.writeUtf8("b".repeat(SEGMENT_SIZE - 2)) // ab...b
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
          assertEquals((SEGMENT_SIZE - 3).toLong(), source.indexOf('c'.code.toByte()))

          // The segment doesn't start at 2, it starts at 4.
          source.skip(2) // b...bc
          assertEquals(-1, source.indexOf('a'.code.toByte()))
          assertEquals(0, source.indexOf('b'.code.toByte()))
          assertEquals((SEGMENT_SIZE - 5).toLong(), source.indexOf('c'.code.toByte()))

          // Two segments.
          sink.writeUtf8("d") // b...bcd, d is in the 2nd segment.
          sink.emit()
          assertEquals((SEGMENT_SIZE - 4).toLong(), source.indexOf('d'.code.toByte()))
          assertEquals(-1, source.indexOf('e'.code.toByte()))
        }

        test("indexOfByteWithStartOffset") {
          sink.writeUtf8("a").writeUtf8("b".repeat(SEGMENT_SIZE)).writeUtf8("c")
          sink.emit()
          assertEquals(-1, source.indexOf('a'.code.toByte(), 1))
          assertEquals(15, source.indexOf('b'.code.toByte(), 15))
        }

        test("indexOfByteWithBothOffsets") {
          if (factory.isOneByteAtATime) {
            // When run on Travis this causes out-of-memory errors.
            return@test
          }
          val a = 'a'.code.toByte()
          val c = 'c'.code.toByte()
          val size: Int = SEGMENT_SIZE * 5
          val bytes = ByteArray(size)
          Arrays.fill(bytes, a)

          // These are tricky places where the buffer
          // starts, ends, or segments come together.
          val points = intArrayOf(
            0, 1, 2,
            SEGMENT_SIZE - 1, SEGMENT_SIZE, SEGMENT_SIZE + 1,
            size / 2 - 1, size / 2, size / 2 + 1,
            size - SEGMENT_SIZE - 1, size - SEGMENT_SIZE, size - SEGMENT_SIZE + 1,
            size - 3, size - 2, size - 1,
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

        test("indexOfByteInvalidBoundsThrows") {
          sink.writeUtf8("abc")
          sink.emit()
          try {
            source.indexOf('a'.code.toByte(), -1)
            fail("Expected failure: fromIndex < 0")
          } catch (expected: IllegalArgumentException) {
          }
          try {
            source.indexOf('a'.code.toByte(), 10, 0)
            fail("Expected failure: fromIndex > toIndex")
          } catch (expected: IllegalArgumentException) {
          }
        }

        test("indexOfByteString") {
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

        test("indexOfByteStringAtSegmentBoundary") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE - 1))
          sink.writeUtf8("bcd")
          sink.emit()
          assertEquals((SEGMENT_SIZE - 3).toLong(), source.indexOf("aabc".encodeUtf8(), (SEGMENT_SIZE - 4).toLong()))
          assertEquals((SEGMENT_SIZE - 3).toLong(), source.indexOf("aabc".encodeUtf8(), (SEGMENT_SIZE - 3).toLong()))
          assertEquals((SEGMENT_SIZE - 2).toLong(), source.indexOf("abcd".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals((SEGMENT_SIZE - 2).toLong(), source.indexOf("abc".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals((SEGMENT_SIZE - 2).toLong(), source.indexOf("abc".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals((SEGMENT_SIZE - 2).toLong(), source.indexOf("ab".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals((SEGMENT_SIZE - 2).toLong(), source.indexOf("a".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals((SEGMENT_SIZE - 1).toLong(), source.indexOf("bc".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals((SEGMENT_SIZE - 1).toLong(), source.indexOf("b".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals(SEGMENT_SIZE.toLong(), source.indexOf("c".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals(SEGMENT_SIZE.toLong(), source.indexOf("c".encodeUtf8(), SEGMENT_SIZE.toLong()))
          assertEquals((SEGMENT_SIZE + 1).toLong(), source.indexOf("d".encodeUtf8(), (SEGMENT_SIZE - 2).toLong()))
          assertEquals((SEGMENT_SIZE + 1).toLong(), source.indexOf("d".encodeUtf8(), (SEGMENT_SIZE + 1).toLong()))
        }

        test("indexOfDoesNotWrapAround") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE - 1))
          sink.writeUtf8("bcd")
          sink.emit()
          assertEquals(-1, source.indexOf("abcda".encodeUtf8(), (SEGMENT_SIZE - 3).toLong()))
        }

        test("indexOfByteStringWithOffset") {
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

        test("indexOfByteStringInvalidArgumentsThrows") {
          try {
            source.indexOf(ByteString.of())
            fail()
          } catch (e: IllegalArgumentException) {
            assertEquals("byteCount == 0", e.message)
          }
          try {
            source.indexOf("hi".encodeUtf8(), -1)
            fail()
          } catch (e: IllegalArgumentException) {
            assertEquals("fromIndex < 0: -1", e.message)
          }
        }

        /**
         * With [SourceFactory.OneByteAtATimeSource], this code was extremely slow.
         * https://github.com/lysine-dev/okio/issues/171
         */
        test("indexOfByteStringAcrossSegmentBoundaries") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE * 2 - 3))
          sink.writeUtf8("bcdefg")
          sink.emit()
          assertEquals((SEGMENT_SIZE * 2 - 4).toLong(), source.indexOf("ab".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 - 4).toLong(), source.indexOf("abc".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 - 4).toLong(), source.indexOf("abcd".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 - 4).toLong(), source.indexOf("abcde".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 - 4).toLong(), source.indexOf("abcdef".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 - 4).toLong(), source.indexOf("abcdefg".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 - 3).toLong(), source.indexOf("bcdefg".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 - 2).toLong(), source.indexOf("cdefg".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 - 1).toLong(), source.indexOf("defg".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2).toLong(), source.indexOf("efg".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 + 1).toLong(), source.indexOf("fg".encodeUtf8()))
          assertEquals((SEGMENT_SIZE * 2 + 2).toLong(), source.indexOf("g".encodeUtf8()))
        }

        test("indexOfElement") {
          sink.writeUtf8("a").writeUtf8("b".repeat(SEGMENT_SIZE)).writeUtf8("c")
          sink.emit()
          assertEquals(0, source.indexOfElement("DEFGaHIJK".encodeUtf8()))
          assertEquals(1, source.indexOfElement("DEFGHIJKb".encodeUtf8()))
          assertEquals((SEGMENT_SIZE + 1).toLong(), source.indexOfElement("cDEFGHIJK".encodeUtf8()))
          assertEquals(1, source.indexOfElement("DEFbGHIc".encodeUtf8()))
          assertEquals(-1L, source.indexOfElement("DEFGHIJK".encodeUtf8()))
          assertEquals(-1L, source.indexOfElement("".encodeUtf8()))
        }

        test("indexOfElementWithOffset") {
          sink.writeUtf8("a").writeUtf8("b".repeat(SEGMENT_SIZE)).writeUtf8("c")
          sink.emit()
          assertEquals(-1, source.indexOfElement("DEFGaHIJK".encodeUtf8(), 1))
          assertEquals(15, source.indexOfElement("DEFGHIJKb".encodeUtf8(), 15))
        }

        test("indexOfByteWithFromIndex") {
          sink.writeUtf8("aaa")
          sink.emit()
          assertEquals(0, source.indexOf('a'.code.toByte()))
          assertEquals(0, source.indexOf('a'.code.toByte(), 0))
          assertEquals(1, source.indexOf('a'.code.toByte(), 1))
          assertEquals(2, source.indexOf('a'.code.toByte(), 2))
        }

        test("indexOfByteStringWithFromIndex") {
          sink.writeUtf8("aaa")
          sink.emit()
          assertEquals(0, source.indexOf("a".encodeUtf8()))
          assertEquals(0, source.indexOf("a".encodeUtf8(), 0))
          assertEquals(1, source.indexOf("a".encodeUtf8(), 1))
          assertEquals(2, source.indexOf("a".encodeUtf8(), 2))
        }

        test("indexOfByteStringWithFromIndexAndToIndex") {
          sink.writeUtf8("Don't move! He can't see us if we don't move.")
          sink.emit()
          val move = "move".encodeUtf8()

          assertEquals(-1L, source.indexOf(move, 0L, 6L))
          assertEquals(6L, source.indexOf(move, 0L, 7L))
          assertEquals(6L, source.indexOf(move, 6L, 7L))
          assertEquals(-1L, source.indexOf(move, 7L, 40L))
          assertEquals(40L, source.indexOf(move, 7L, 41L))
          assertEquals(40L, source.indexOf(move, 40L, 41L))
          assertEquals(-1L, source.indexOf(move, 41L, 42L))
        }

        /** In this example we must load 6 bytes to determine the string is not found. */
        test("indexOfByteStringDocumentationLoadingCase") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("shellxyz")
          sink.emit()
          assertEquals(-1, source.indexOf("hello".encodeUtf8(), 0, 4))
          assertEquals("shellx", source.buffer.readUtf8())
        }

        /** In this example we must load only 4 bytes to determine the string is not found. */
        test("indexOfByteStringDocumentationNoLoadingCase") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("lookxyz")
          sink.emit()
          assertEquals(-1, source.indexOf("hello".encodeUtf8(), 0, 4))
          assertEquals("look", source.buffer.readUtf8())
        }

        /** This demonstrates that `indexOf()` doesn't load ranges beyond the maximum required. */
        test("indexOfByteStringLoadsOnlyWhatIsRequiredWhenNotFoundSingleByte") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertEquals(-1, source.indexOf("X man,".encodeUtf8(), 0, 1))
          assertEquals("A", source.buffer.readUtf8())
        }

        test("indexOfByteStringLoadsOnlyWhatIsRequiredWhenNotFoundMultipleBytes") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertEquals(-1, source.indexOf("A Xan,".encodeUtf8(), 0, 1))
          assertEquals("A m", source.buffer.readUtf8())
        }

        test("indexOfByteStringLoadsOnlyWhatIsRequiredWhenFoundMultipleBytes") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertEquals(0, source.indexOf("A man,".encodeUtf8(), 0, 1))
          assertEquals("A man,", source.buffer.readUtf8())
        }

        test("indexOfByteStringLoadsOnlyWhatIsRequiredWhenNotFoundWithFromIndex") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertEquals(-1, source.indexOf("A man,".encodeUtf8(), 1, 2))
          assertEquals("A ", source.buffer.readUtf8())
        }

        test("indexOfByteStringLoadsOnlyWhatIsRequiredWhenFound") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertEquals(9L, source.indexOf("plan".encodeUtf8(), 0L, 10L))
          assertEquals("A man, a plan", source.buffer.readUtf8())
        }

        test("indexOfByteStringFindsResultEdgeCases") {
          for (i in 1..5) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(0L, source.indexOf("a".repeat(i).encodeUtf8(), 0L, 1L))
            source.skip(5L)
          }

          for (i in 1..4) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(1L, source.indexOf("a".repeat(i).encodeUtf8(), 1L, 5L))
            source.skip(5L)
          }

          for (i in 1..3) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(2L, source.indexOf("a".repeat(i).encodeUtf8(), 2L, 5L))
            source.skip(5L)
          }

          for (i in 1..2) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(3L, source.indexOf("a".repeat(i).encodeUtf8(), 3L, 5L))
            source.skip(5L)
          }

          for (i in 1..1) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(4L, source.indexOf("a".repeat(i).encodeUtf8(), 4L, 5L))
            source.skip(5L)
          }
        }

        test("indexOfByteStringEmptySearchRange") {
          for (i in 1..5) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(-1L, source.indexOf("a".repeat(i).encodeUtf8(), 0L, 0L))
            source.skip(5L)
          }
        }

        test("indexOfByteStringTooLongAtOffset") {
          for (i in 5..5) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(-1L, source.indexOf("a".repeat(i).encodeUtf8(), 1L, 5L))
            source.skip(5L)
          }
          for (i in 4..5) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(-1L, source.indexOf("a".repeat(i).encodeUtf8(), 2L, 5L))
            source.skip(5L)
          }
          for (i in 3..5) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(-1L, source.indexOf("a".repeat(i).encodeUtf8(), 3L, 5L))
            source.skip(5L)
          }
          for (i in 2..5) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(-1L, source.indexOf("a".repeat(i).encodeUtf8(), 4L, 5L))
            source.skip(5L)
          }
          for (i in 1..5) {
            sink.writeUtf8("aaaaa")
            sink.emit()
            assertEquals(-1L, source.indexOf("a".repeat(i).encodeUtf8(), 5L, 5L))
            source.skip(5L)
          }
        }

        test("indexOfHonorsToIndexWhenAvoidingLoadsAndDoesNotLoad") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          source.require(2) // Source buffer contains 'A '
          assertEquals(-1L, source.indexOf(" man".encodeUtf8(), 0L, 1L))
          assertEquals("A ", source.buffer.readUtf8())
        }

        test("indexOfHonorsFromIndexWhenAvoidingLoads") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertEquals(-1, source.indexOf("Panama.".encodeUtf8(), 25L, 27L))
          assertEquals("A man, a plan, a canal. Pan", source.buffer.readUtf8())
        }

        test("indexOfHonorsToIndexWhenAvoidingLoadsAndLoads") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.

          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          source.require(2) // Source buffer contains 'A '
          assertEquals(0L, source.indexOf("A ma".encodeUtf8(), 0L, 1L))
          assertEquals("A ma", source.buffer.readUtf8())
        }

        test("indexOfElementWithFromIndex") {
          sink.writeUtf8("aaa")
          sink.emit()
          assertEquals(0, source.indexOfElement("a".encodeUtf8()))
          assertEquals(0, source.indexOfElement("a".encodeUtf8(), 0))
          assertEquals(1, source.indexOfElement("a".encodeUtf8(), 1))
          assertEquals(2, source.indexOfElement("a".encodeUtf8(), 2))
        }

        test("request") {
          sink.writeUtf8("a").writeUtf8("b".repeat(SEGMENT_SIZE)).writeUtf8("c")
          sink.emit()
          assertTrue(source.request((SEGMENT_SIZE + 2).toLong()))
          assertFalse(source.request((SEGMENT_SIZE + 3).toLong()))
        }

        test("require") {
          sink.writeUtf8("a").writeUtf8("b".repeat(SEGMENT_SIZE)).writeUtf8("c")
          sink.emit()
          source.require((SEGMENT_SIZE + 2).toLong())
          try {
            source.require((SEGMENT_SIZE + 3).toLong())
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("inputStream") {
          sink.writeUtf8("abc")
          sink.emit()
          val `in` = source.inputStream()
          val bytes = byteArrayOf('z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte())
          var read = `in`.read(bytes)
          if (factory.isOneByteAtATime) {
            assertEquals(1, read.toLong())
            assertByteArrayEquals("azz", bytes)
            read = `in`.read(bytes)
            assertEquals(1, read.toLong())
            assertByteArrayEquals("bzz", bytes)
            read = `in`.read(bytes)
            assertEquals(1, read.toLong())
            assertByteArrayEquals("czz", bytes)
          } else {
            assertEquals(3, read.toLong())
            assertByteArrayEquals("abc", bytes)
          }
          assertEquals(-1, `in`.read().toLong())
        }

        test("inputStreamOffsetCount") {
          sink.writeUtf8("abcde")
          sink.emit()
          val `in` = source.inputStream()
          val bytes =
            byteArrayOf('z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte())
          val read = `in`.read(bytes, 1, 3)
          if (factory.isOneByteAtATime) {
            assertEquals(1, read.toLong())
            assertByteArrayEquals("zazzz", bytes)
          } else {
            assertEquals(3, read.toLong())
            assertByteArrayEquals("zabcz", bytes)
          }
        }

        test("inputStreamSkip") {
          sink.writeUtf8("abcde")
          sink.emit()
          val `in` = source.inputStream()
          assertEquals(4, `in`.skip(4))
          assertEquals('e'.code.toLong(), `in`.read().toLong())
          sink.writeUtf8("abcde")
          sink.emit()
          assertEquals(5, `in`.skip(10)) // Try to skip too much.
          assertEquals(0, `in`.skip(1)) // Try to skip when exhausted.
        }

        test("inputStreamCharByChar") {
          sink.writeUtf8("abc")
          sink.emit()
          val `in` = source.inputStream()
          assertEquals('a'.code.toLong(), `in`.read().toLong())
          assertEquals('b'.code.toLong(), `in`.read().toLong())
          assertEquals('c'.code.toLong(), `in`.read().toLong())
          assertEquals(-1, `in`.read().toLong())
        }

        test("inputStreamBounds") {
          sink.writeUtf8("a".repeat(100))
          sink.emit()
          val `in` = source.inputStream()
          try {
            `in`.read(ByteArray(100), 50, 51)
            fail()
          } catch (expected: java.lang.ArrayIndexOutOfBoundsException) {
          }
        }

        test("inputStreamTransferTo") {
          try {
            ByteArrayInputStream(byteArrayOf(1)).transferTo(ByteArrayOutputStream())
          } catch (e: NoSuchMethodError) {
            return@test // This JDK doesn't have transferTo(). Skip this test.
          }

          val data = "a".repeat(SEGMENT_SIZE * 3 + 1)
          sink.writeUtf8(data)
          sink.emit()
          val inputStream = source.inputStream()
          val outputStream = ByteArrayOutputStream()
          inputStream.transferTo(outputStream)
          assertThat(source.exhausted()).isTrue()
          assertThat(outputStream.toByteArray().toUtf8String()).isEqualTo(data)
        }

        test("longHexString") {
          assertLongHexString("8000000000000000", -0x7fffffffffffffffL - 1L)
          assertLongHexString("fffffffffffffffe", -0x2L)
          assertLongHexString("FFFFFFFFFFFFFFFe", -0x2L)
          assertLongHexString("ffffffffffffffff", -0x1L)
          assertLongHexString("FFFFFFFFFFFFFFFF", -0x1L)
          assertLongHexString("0000000000000000", 0x0)
          assertLongHexString("0000000000000001", 0x1)
          assertLongHexString("7999999999999999", 0x7999999999999999L)
          assertLongHexString("FF", 0xFF)
          assertLongHexString("0000000000000001", 0x1)
        }

        test("hexStringWithManyLeadingZeros") {
          assertLongHexString("00000000000000001", 0x1)
          assertLongHexString("0000000000000000ffffffffffffffff", -0x1L)
          assertLongHexString("00000000000000007fffffffffffffff", 0x7fffffffffffffffL)
          assertLongHexString("0".repeat(SEGMENT_SIZE + 1) + "1", 0x1)
        }

        test("longHexStringAcrossSegment") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE - 8)).writeUtf8("FFFFFFFFFFFFFFFF")
          sink.emit()
          source.skip((SEGMENT_SIZE - 8).toLong())
          assertEquals(-1, source.readHexadecimalUnsignedLong())
        }

        test("longHexStringTooLongThrows") {
          try {
            sink.writeUtf8("fffffffffffffffff")
            sink.emit()
            source.readHexadecimalUnsignedLong()
            fail()
          } catch (e: NumberFormatException) {
            assertEquals("Number too large: fffffffffffffffff", e.message)
          }
        }

        test("longHexStringTooShortThrows") {
          try {
            sink.writeUtf8(" ")
            sink.emit()
            source.readHexadecimalUnsignedLong()
            fail()
          } catch (e: NumberFormatException) {
            assertEquals("Expected leading [0-9a-fA-F] character but was 0x20", e.message)
          }
        }

        test("longHexEmptySourceThrows") {
          try {
            sink.writeUtf8("")
            sink.emit()
            source.readHexadecimalUnsignedLong()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("longDecimalString") {
          assertLongDecimalString("-9223372036854775808", -9223372036854775807L - 1L)
          assertLongDecimalString("-1", -1L)
          assertLongDecimalString("0", 0L)
          assertLongDecimalString("1", 1L)
          assertLongDecimalString("9223372036854775807", 9223372036854775807L)
          assertLongDecimalString("00000001", 1L)
          assertLongDecimalString("-000001", -1L)
        }

        test("longDecimalStringAcrossSegment") {
          sink.writeUtf8("a".repeat(SEGMENT_SIZE - 8)).writeUtf8("1234567890123456")
          sink.writeUtf8("zzz")
          sink.emit()
          source.skip((SEGMENT_SIZE - 8).toLong())
          assertEquals(1234567890123456L, source.readDecimalLong())
          assertEquals("zzz", source.readUtf8())
        }

        test("longDecimalStringTooLongThrows") {
          try {
            sink.writeUtf8("12345678901234567890") // Too many digits.
            sink.emit()
            source.readDecimalLong()
            fail()
          } catch (e: NumberFormatException) {
            assertEquals("Number too large: 12345678901234567890", e.message)
          }
        }

        test("longDecimalStringTooHighThrows") {
          try {
            sink.writeUtf8("9223372036854775808") // Right size but cannot fit.
            sink.emit()
            source.readDecimalLong()
            fail()
          } catch (e: NumberFormatException) {
            assertEquals("Number too large: 9223372036854775808", e.message)
          }
        }

        test("longDecimalStringTooLowThrows") {
          try {
            sink.writeUtf8("-9223372036854775809") // Right size but cannot fit.
            sink.emit()
            source.readDecimalLong()
            fail()
          } catch (e: NumberFormatException) {
            assertEquals("Number too large: -9223372036854775809", e.message)
          }
        }

        test("longDecimalStringTooShortThrows") {
          try {
            sink.writeUtf8(" ")
            sink.emit()
            source.readDecimalLong()
            fail()
          } catch (e: NumberFormatException) {
            assertEquals("Expected a digit or '-' but was 0x20", e.message)
          }
        }

        test("longDecimalEmptyThrows") {
          try {
            sink.writeUtf8("")
            sink.emit()
            source.readDecimalLong()
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("codePoints") {
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

        test("decimalStringWithManyLeadingZeros") {
          assertLongDecimalString("00000000000000001", 1)
          assertLongDecimalString("00000000000000009223372036854775807", 9223372036854775807L)
          assertLongDecimalString("-00000000000000009223372036854775808", -9223372036854775807L - 1L)
          assertLongDecimalString("0".repeat(SEGMENT_SIZE + 1) + "1", 1)
        }

        test("select") {
          val options = of(
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

        /** Note that this test crashes the VM on Android.  */
        test("selectSpanningMultipleSegments") {
          val commonPrefix = randomBytes(SEGMENT_SIZE + 10)
          val a = Buffer().write(commonPrefix).writeUtf8("a").readByteString()
          val bc = Buffer().write(commonPrefix).writeUtf8("bc").readByteString()
          val bd = Buffer().write(commonPrefix).writeUtf8("bd").readByteString()
          val options = of(a, bc, bd)
          sink.write(bd)
          sink.write(a)
          sink.write(bc)
          sink.emit()
          assertEquals(2, source.select(options).toLong())
          assertEquals(0, source.select(options).toLong())
          assertEquals(1, source.select(options).toLong())
          assertTrue(source.exhausted())
        }

        test("selectNotFound") {
          val options = of(
            "ROCK".encodeUtf8(),
            "SCISSORS".encodeUtf8(),
            "PAPER".encodeUtf8(),
          )
          sink.writeUtf8("SPOCK")
          sink.emit()
          assertEquals(-1, source.select(options).toLong())
          assertEquals("SPOCK", source.readUtf8())
        }

        test("selectValuesHaveCommonPrefix") {
          val options = of(
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

        test("selectLongerThanSource") {
          val options = of(
            "abcd".encodeUtf8(),
            "abce".encodeUtf8(),
            "abcc".encodeUtf8(),
          )
          sink.writeUtf8("abc")
          sink.emit()
          assertEquals(-1, source.select(options).toLong())
          assertEquals("abc", source.readUtf8())
        }

        test("selectReturnsFirstByteStringThatMatches") {
          val options = of(
            "abcd".encodeUtf8(),
            "abc".encodeUtf8(),
            "abcde".encodeUtf8(),
          )
          sink.writeUtf8("abcdef")
          sink.emit()
          assertEquals(0, source.select(options).toLong())
          assertEquals("ef", source.readUtf8())
        }

        test("selectFromEmptySource") {
          val options = of(
            "abc".encodeUtf8(),
            "def".encodeUtf8(),
          )
          assertEquals(-1, source.select(options).toLong())
        }

        test("selectNoByteStringsFromEmptySource") {
          val options = Options.of()
          assertEquals(-1, source.select(options).toLong())
        }

        test("peek") {
          sink.writeUtf8("abcdefghi")
          sink.emit()
          assertEquals("abc", source.readUtf8(3))
          val peek = source.peek()
          assertEquals("def", peek.readUtf8(3))
          assertEquals("ghi", peek.readUtf8(3))
          assertFalse(peek.request(1))
          assertEquals("def", source.readUtf8(3))
        }

        test("peekMultiple") {
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

        test("peekLarge") {
          sink.writeUtf8("abcdef")
          sink.writeUtf8("g".repeat(2 * SEGMENT_SIZE))
          sink.writeUtf8("hij")
          sink.emit()
          assertEquals("abc", source.readUtf8(3))
          val peek = source.peek()
          assertEquals("def", peek.readUtf8(3))
          peek.skip((2 * SEGMENT_SIZE).toLong())
          assertEquals("hij", peek.readUtf8(3))
          assertFalse(peek.request(1))
          assertEquals("def", source.readUtf8(3))
          source.skip((2 * SEGMENT_SIZE).toLong())
          assertEquals("hij", source.readUtf8(3))
        }

        test("peekInvalid") {
          sink.writeUtf8("abcdefghi")
          sink.emit()
          assertEquals("abc", source.readUtf8(3))
          val peek = source.peek()
          assertEquals("def", peek.readUtf8(3))
          assertEquals("ghi", peek.readUtf8(3))
          assertFalse(peek.request(1))
          assertEquals("def", source.readUtf8(3))
          try {
            peek.readUtf8()
            fail()
          } catch (e: IllegalStateException) {
            assertEquals("Peek source is invalid because upstream source was used", e.message)
          }
        }

        test("peekSegmentThenInvalid") {
          sink.writeUtf8("abc")
          sink.writeUtf8("d".repeat(2 * SEGMENT_SIZE))
          sink.emit()
          assertEquals("abc", source.readUtf8(3))

          // Peek a little data and skip the rest of the upstream source
          val peek = source.peek()
          assertEquals("ddd", peek.readUtf8(3))
          source.readAll(blackholeSink())

          // Skip the rest of the buffered data
          peek.skip(peek.buffer.size)
          try {
            peek.readByte()
            fail()
          } catch (e: IllegalStateException) {
            assertEquals("Peek source is invalid because upstream source was used", e.message)
          }
        }

        test("peekDoesntReadTooMuch") {
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

        test("rangeEquals") {
          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertTrue(source.rangeEquals(7, "a plan".encodeUtf8()))
          assertTrue(source.rangeEquals(0, "A man".encodeUtf8()))
          assertTrue(source.rangeEquals(24, "Panama".encodeUtf8()))
          assertFalse(source.rangeEquals(24, "Panama. Panama. Panama.".encodeUtf8()))
        }

        test("rangeEqualsWithOffsetAndCount") {
          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertTrue(source.rangeEquals(7, "aaa plannn".encodeUtf8(), 2, 6))
          assertTrue(source.rangeEquals(0, "AAA mannn".encodeUtf8(), 2, 5))
          assertTrue(source.rangeEquals(24, "PPPanamaaa".encodeUtf8(), 2, 6))
        }

        test("rangeEqualsOnlyReadsUntilMismatch") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.
          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertFalse(source.rangeEquals(0, "A man.".encodeUtf8()))
          assertEquals("A man,", source.buffer.readUtf8())
        }

        test("rangeEqualsBreaksAfterFirstMismatch") {
          if (factory !== SourceFactory.OneByteAtATimeSource) return@test // Other sources read in chunks anyway.
          sink.writeUtf8("A man, a plan, a canal. Panama.")
          sink.emit()
          assertFalse(source.rangeEquals(0, "X man,".encodeUtf8()))
          assertEquals("A", source.buffer.readUtf8())
        }

        test("rangeEqualsArgumentValidation") {
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

        test("readNioBuffer") {
          val expected = if (factory.isOneByteAtATime) "a" else "abcdefg"
          sink.writeUtf8("abcdefg")
          sink.emit()
          val nioByteBuffer = ByteBuffer.allocate(1024)
          val byteCount = source.read(nioByteBuffer)
          assertEquals(expected.length.toLong(), byteCount.toLong())
          assertEquals(expected.length.toLong(), nioByteBuffer.position().toLong())
          assertEquals(nioByteBuffer.capacity().toLong(), nioByteBuffer.limit().toLong())
          (nioByteBuffer as java.nio.Buffer).flip() // Cast necessary for Java 8.
          val data = ByteArray(expected.length)
          nioByteBuffer[data]
          assertEquals(expected, data.decodeToString())
        }

        /** Note that this test crashes the VM on Android.  */
        test("readLargeNioBufferOnlyReadsOneSegment") {
          val expected = if (factory.isOneByteAtATime) "a" else "a".repeat(SEGMENT_SIZE)
          sink.writeUtf8("a".repeat(SEGMENT_SIZE * 4))
          sink.emit()
          val nioByteBuffer = ByteBuffer.allocate(SEGMENT_SIZE * 3)
          val byteCount = source.read(nioByteBuffer)
          assertEquals(expected.length.toLong(), byteCount.toLong())
          assertEquals(expected.length.toLong(), nioByteBuffer.position().toLong())
          assertEquals(nioByteBuffer.capacity().toLong(), nioByteBuffer.limit().toLong())
          (nioByteBuffer as java.nio.Buffer).flip() // Cast necessary for Java 8.
          val data = ByteArray(expected.length)
          nioByteBuffer[data]
          assertEquals(expected, data.decodeToString())
        }

        test("factorySegmentSizes") {
          sink.writeUtf8("abc")
          sink.emit()
          source.require(3)
          if (factory.isOneByteAtATime) {
            assertEquals(mutableListOf(1, 1, 1), segmentSizes(source.buffer))
          } else {
            assertEquals(listOf(3), segmentSizes(source.buffer))
          }
        }
      }
    }
  }
}

private class BufferedSourceFixture(factory: SourceFactory) : AutoCloseable {
  val pipe = factory.pipe()
  val sink: BufferedSink = pipe.sink
  val source: BufferedSource = pipe.source

  fun assertLongDecimalString(s: String, expected: Long) {
    sink.writeUtf8(s)
    sink.writeUtf8("zzz")
    sink.emit()
    val actual = source.readDecimalLong()
    assertEquals(expected, actual, "$s --> $expected")
    assertEquals("zzz", source.readUtf8())
  }

  fun assertLongHexString(s: String, expected: Long) {
    sink.writeUtf8(s)
    sink.emit()
    val actual = source.readHexadecimalUnsignedLong()
    assertEquals(expected, actual, "$s --> $expected")
  }

  override fun close() {
    runCatching { source.buffer.clear() }
  }
}

private enum class SourceFactory {
  NewBuffer {
    override fun pipe(): SourcePipe {
      val buffer = Buffer()
      return SourcePipe(buffer, buffer)
    }

    override val isOneByteAtATime: Boolean get() = false
  },

  SourceBuffer {
    override fun pipe(): SourcePipe {
      val buffer = Buffer()
      return SourcePipe(
        sink = buffer,
        source = (buffer as Source).buffer(),
      )
    }

    override val isOneByteAtATime: Boolean get() = false
  },

  /**
   * A factory deliberately written to create buffers whose internal segments are always 1 byte
   * long. We like testing with these segments because are likely to trigger bugs!
   */
  OneByteAtATimeSource {
    override fun pipe(): SourcePipe {
      val buffer = Buffer()
      return SourcePipe(
        sink = buffer,
        source = object : ForwardingSource(buffer) {
          override fun read(sink: Buffer, byteCount: Long): Long {
            // Read one byte into a new buffer, then clone it so that the segment is shared.
            // Shared segments cannot be compacted so we'll get a long chain of short segments.
            val box = Buffer()
            val result = super.read(box, Math.min(byteCount, 1L))
            if (result > 0L) sink.write(box.clone(), result)
            return result
          }
        }.buffer(),
      )
    }

    override val isOneByteAtATime: Boolean get() = true
  },

  OneByteAtATimeSink {
    override fun pipe(): SourcePipe {
      val buffer = Buffer()
      val sink = object : ForwardingSink(buffer) {
        override fun write(source: Buffer, byteCount: Long) {
          // Write each byte into a new buffer, then clone it so that the segments are shared.
          // Shared segments cannot be compacted so we'll get a long chain of short segments.
          for (i in 0 until byteCount) {
            val box = Buffer()
            box.write(source, 1)
            super.write(box.clone(), 1)
          }
        }
      }.buffer()
      return SourcePipe(
        sink = sink,
        source = buffer,
      )
    }

    override val isOneByteAtATime: Boolean get() = true
  },

  PeekSource {
    override fun pipe(): SourcePipe {
      val buffer = Buffer()
      return SourcePipe(
        sink = buffer,
        source = buffer.peek(),
      )
    }

    override val isOneByteAtATime: Boolean get() = false
  },

  PeekBufferedSource {
    override fun pipe(): SourcePipe {
      val buffer = Buffer()
      return SourcePipe(
        sink = buffer,
        source = (buffer as Source).buffer().peek(),
      )
    }

    override val isOneByteAtATime: Boolean get() = false
  },
  ;

  abstract fun pipe(): SourcePipe
  abstract val isOneByteAtATime: Boolean
}

private class SourcePipe(
  var sink: BufferedSink,
  var source: BufferedSource,
)
