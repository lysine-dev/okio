/*
 * Copyright 2014 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import de.infix.testBalloon.framework.core.testSuite
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Random
import kotlin.text.Charsets.US_ASCII
import kotlin.text.Charsets.UTF_16BE
import kotlin.text.Charsets.UTF_32BE
import kotlin.text.Charsets.UTF_8
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encode
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.readByteString
import okio.ByteString.Companion.toByteString
import okio.TestUtil.assertByteArraysEquals
import okio.TestUtil.assertEquivalent
import okio.TestUtil.makeSegments
import okio.TestUtil.reserialize
import org.junit.jupiter.api.Assertions.assertEquals

val ByteStringJavaTest by testSuite {
  val bronzeHorseman = "На берегу пустынных волн"

  for (factory in ByteStringJavaFactory.entries) {
    testSuite(factory.name) {
      test("ofByteBuffer") {
        val bytes = "Hello, World!".toByteArray(UTF_8)
        val byteBuffer = ByteBuffer.wrap(bytes)
        (byteBuffer as java.nio.Buffer).position(2).limit(11) // Cast necessary for Java 8.
        val byteString: ByteString = byteBuffer.toByteString()
        // Verify that the bytes were copied out.
        byteBuffer.put(4, 'a'.code.toByte())
        assertEquals("llo, Worl", byteString.utf8())
      }

      test("read") {
        val inputStream = ByteArrayInputStream("abc".toByteArray(UTF_8))
        assertEquals("6162".decodeHex(), inputStream.readByteString(2))
        assertEquals("63".decodeHex(), inputStream.readByteString(1))
        assertEquals(ByteString.of(), inputStream.readByteString(0))
      }

      test("readAndToLowercase") {
        val inputStream = ByteArrayInputStream("ABC".toByteArray(UTF_8))
        assertEquals("ab".encodeUtf8(), inputStream.readByteString(2).toAsciiLowercase())
        assertEquals("c".encodeUtf8(), inputStream.readByteString(1).toAsciiLowercase())
        assertEquals(ByteString.EMPTY, inputStream.readByteString(0).toAsciiLowercase())
      }

      test("readAndToUppercase") {
        val inputStream = ByteArrayInputStream("abc".toByteArray(UTF_8))
        assertEquals("AB".encodeUtf8(), inputStream.readByteString(2).toAsciiUppercase())
        assertEquals("C".encodeUtf8(), inputStream.readByteString(1).toAsciiUppercase())
        assertEquals(ByteString.EMPTY, inputStream.readByteString(0).toAsciiUppercase())
      }

      test("write") {
        val out = ByteArrayOutputStream()
        factory.decodeHex("616263").write(out)
        assertByteArraysEquals(byteArrayOf(0x61, 0x62, 0x63), out.toByteArray())
      }

      test("compareToSingleBytes") {
        val originalByteStrings = listOf(
          factory.decodeHex("00"),
          factory.decodeHex("01"),
          factory.decodeHex("7e"),
          factory.decodeHex("7f"),
          factory.decodeHex("80"),
          factory.decodeHex("81"),
          factory.decodeHex("fe"),
          factory.decodeHex("ff"),
        )
        val sortedByteStrings = originalByteStrings.toMutableList()
        sortedByteStrings.shuffle(Random(0))
        sortedByteStrings.sort()
        assertEquals(originalByteStrings, sortedByteStrings)
      }

      test("compareToMultipleBytes") {
        val originalByteStrings = listOf(
          factory.decodeHex(""),
          factory.decodeHex("00"),
          factory.decodeHex("0000"),
          factory.decodeHex("000000"),
          factory.decodeHex("00000000"),
          factory.decodeHex("0000000000"),
          factory.decodeHex("0000000001"),
          factory.decodeHex("000001"),
          factory.decodeHex("00007f"),
          factory.decodeHex("0000ff"),
          factory.decodeHex("000100"),
          factory.decodeHex("000101"),
          factory.decodeHex("007f00"),
          factory.decodeHex("00ff00"),
          factory.decodeHex("010000"),
          factory.decodeHex("010001"),
          factory.decodeHex("01007f"),
          factory.decodeHex("0100ff"),
          factory.decodeHex("010100"),
          factory.decodeHex("01010000"),
          factory.decodeHex("0101000000"),
          factory.decodeHex("0101000001"),
          factory.decodeHex("010101"),
          factory.decodeHex("7f0000"),
          factory.decodeHex("7f0000ffff"),
          factory.decodeHex("ffffff"),
        )
        val sortedByteStrings = originalByteStrings.toMutableList()
        sortedByteStrings.shuffle(Random(0))
        sortedByteStrings.sort()
        assertEquals(originalByteStrings, sortedByteStrings)
      }

      test("javaSerializationTestNonEmpty") {
        val byteString = factory.encodeUtf8(bronzeHorseman)
        assertEquivalent(byteString, reserialize(byteString))
      }

      test("javaSerializationTestEmpty") {
        val byteString = factory.decodeHex("")
        assertEquivalent(byteString, reserialize(byteString))
      }

      test("asByteBuffer") {
        assertEquals(
          0x42,
          ByteString.of(0x41.toByte(), 0x42.toByte(), 0x43.toByte()).asByteBuffer()[1].toLong(),
        )
      }

      test("encodeDecodeStringUtf8") {
        val byteString = bronzeHorseman.encode(UTF_8)
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(UTF_8))
        assertEquals(
          byteString,
          (
            "d09dd0b020d0b1d0b5d180d0b5d0b3d18320d0bfd183d181" +
              "d182d18bd0bdd0bdd18bd18520d0b2d0bed0bbd0bd"
            ).decodeHex(),
        )
        assertEquals(bronzeHorseman, byteString.string(UTF_8))
      }

      test("encodeDecodeStringUtf16be") {
        val byteString = bronzeHorseman.encode(UTF_16BE)
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(UTF_16BE))
        assertEquals(
          byteString,
          (
            "041d043000200431043504400435043304430020043f0443" +
              "04410442044b043d043d044b044500200432043e043b043d"
            ).decodeHex(),
        )
        assertEquals(bronzeHorseman, byteString.string(UTF_16BE))
      }

      test("encodeDecodeStringUtf32be") {
        val byteString: ByteString = bronzeHorseman.encode(UTF_32BE)
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(UTF_32BE))
        assertEquals(
          byteString,
          (
            "0000041d0000043000000020000004310000043500000440" +
              "000004350000043300000443000000200000043f0000044300000441000004420000044b0000043d0000043d" +
              "0000044b0000044500000020000004320000043e0000043b0000043d"
            ).decodeHex(),
        )
        assertEquals(bronzeHorseman, byteString.string(UTF_32BE))
      }

      test("encodeDecodeStringAsciiIsLossy") {
        val byteString: ByteString = bronzeHorseman.encode(US_ASCII)
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(US_ASCII))
        assertEquals(
          byteString,
          "3f3f203f3f3f3f3f3f203f3f3f3f3f3f3f3f3f203f3f3f3f".decodeHex(),
        )
        assertEquals("?? ?????? ????????? ????", byteString.string(US_ASCII))
      }

      test("decodeMalformedStringReturnsReplacementCharacter") {
        val string = "04".decodeHex().string(UTF_16BE)
        assertEquals("\ufffd", string)
      }
    }
  }
}

enum class ByteStringJavaFactory {
  BaseByteString {
    override fun decodeHex(hex: String): ByteString {
      return hex.decodeHex()
    }

    override fun encodeUtf8(s: String): ByteString {
      return s.encodeUtf8()
    }
  },
  SegmentedByteString {
    override fun decodeHex(hex: String): ByteString {
      val buffer = Buffer()
      buffer.write(hex.decodeHex())
      return buffer.snapshot()
    }

    override fun encodeUtf8(s: String): ByteString {
      val buffer = Buffer()
      buffer.writeUtf8(s)
      return buffer.snapshot()
    }
  },
  OneBytePerSegment {
    override fun decodeHex(hex: String): ByteString {
      return makeSegments(hex.decodeHex())
    }

    override fun encodeUtf8(s: String): ByteString {
      return makeSegments(s.encodeUtf8())
    }
  },
  ;

  abstract fun decodeHex(hex: String): ByteString
  abstract fun encodeUtf8(s: String): ByteString
}
