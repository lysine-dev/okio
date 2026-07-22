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

import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.Locale
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assume

object TestUtil {
  // Necessary to make an internal member visible to Java.
  @JvmField val SEGMENT_POOL_MAX_SIZE = SegmentPool.MAX_SIZE
  const val SEGMENT_SIZE = Segment.SIZE
  const val REPLACEMENT_CODE_POINT: Int = okio.REPLACEMENT_CODE_POINT

  @JvmStatic fun segmentPoolByteCount() = SegmentPool.byteCount

  @JvmStatic
  fun segmentSizes(buffer: Buffer): List<Int> = okio.segmentSizes(buffer)

  @JvmStatic
  fun assertByteArraysEquals(a: ByteArray, b: ByteArray) {
    assertEquals(a.contentToString(), b.contentToString())
  }

  @JvmStatic
  fun assertByteArrayEquals(expectedUtf8: String, b: ByteArray) {
    assertEquals(expectedUtf8, b.toString(Charsets.UTF_8))
  }

  @JvmStatic
  fun randomBytes(length: Int): ByteString {
    val random = Random(0)
    val randomBytes = ByteArray(length)
    random.nextBytes(randomBytes)
    return ByteString.of(*randomBytes)
  }

  @JvmStatic
  fun randomSource(size: Long): Source {
    return object : Source {
      internal var random = Random(0)
      internal var bytesLeft = size
      internal var closed: Boolean = false

      @Throws(IOException::class)
      override fun read(sink: Buffer, byteCount: Long): Long {
        var byteCount = byteCount
        if (closed) throw IllegalStateException("closed")
        if (bytesLeft == 0L) return -1L
        if (byteCount > bytesLeft) byteCount = bytesLeft

        // If we can read a full segment we can save a copy.
        if (byteCount >= Segment.SIZE) {
          val segment = sink.writableSegment(Segment.SIZE)
          random.nextBytes(segment.data)
          segment.limit += Segment.SIZE
          sink.size += Segment.SIZE.toLong()
          bytesLeft -= Segment.SIZE.toLong()
          return Segment.SIZE.toLong()
        } else {
          val data = ByteArray(byteCount.toInt())
          random.nextBytes(data)
          sink.write(data)
          bytesLeft -= byteCount
          return byteCount
        }
      }

      override fun timeout() = Timeout.NONE

      @Throws(IOException::class)
      override fun close() {
        closed = true
      }
    }
  }

  @JvmStatic
  fun assertEquivalent(b1: ByteString, b2: ByteString) {
    // Equals.
    assertTrue(b1 == b2)
    assertTrue(b1 == b1)
    assertTrue(b2 == b1)

    // Hash code.
    assertEquals(b1.hashCode().toLong(), b2.hashCode().toLong())
    assertEquals(b1.hashCode().toLong(), b1.hashCode().toLong())
    assertEquals(b1.toString(), b2.toString())

    // Content.
    assertEquals(b1.size.toLong(), b2.size.toLong())
    val b2Bytes = b2.toByteArray()
    for (i in b2Bytes.indices) {
      val b = b2Bytes[i]
      assertEquals(b.toLong(), b1[i].toLong())
    }
    assertByteArraysEquals(b1.toByteArray(), b2Bytes)

    // Doesn't equal a different byte string.
    assertFalse(b1 == null)
    assertFalse(b1 == Any())
    if (b2Bytes.size > 0) {
      val b3Bytes = b2Bytes.clone()
      b3Bytes[b3Bytes.size - 1]++
      val b3 = ByteString(b3Bytes)
      assertFalse(b1 == b3)
      assertFalse(b1.hashCode() == b3.hashCode())
    } else {
      val b3 = "a".encodeUtf8()
      assertFalse(b1 == b3)
      assertFalse(b1.hashCode() == b3.hashCode())
    }
  }

  @JvmStatic
  fun assertEquivalent(b1: Buffer, b2: Buffer) {
    // Equals.
    assertTrue(b1 == b2)
    assertTrue(b1 == b1)
    assertTrue(b2 == b1)

    // Hash code.
    assertEquals(b1.hashCode().toLong(), b2.hashCode().toLong())
    assertEquals(b1.hashCode().toLong(), b1.hashCode().toLong())
    assertEquals(b1.toString(), b2.toString())

    // Content.
    assertEquals(b1.size, b2.size)
    val buffer = Buffer()
    b2.copyTo(buffer, 0, b2.size)
    val b2Bytes = b2.readByteArray()
    for (i in b2Bytes.indices) {
      val b = b2Bytes[i]
      assertEquals(b.toLong(), b1[i.toLong()].toLong())
    }

    // Doesn't equal a different buffer.
    assertFalse(b1 == Any())
    if (b2Bytes.size > 0) {
      val b3Bytes = b2Bytes.clone()
      b3Bytes[b3Bytes.size - 1]++
      val b3 = Buffer().write(b3Bytes)
      assertFalse(b1 == b3)
      assertFalse(b1.hashCode() == b3.hashCode())
    } else {
      val b3 = Buffer().writeUtf8("a")
      assertFalse(b1 == b3)
      assertFalse(b1.hashCode() == b3.hashCode())
    }
  }

  /** Serializes original to bytes, then deserializes those bytes and returns the result.  */
  // Assume serialization doesn't change types.
  @Suppress("UNCHECKED_CAST")
  @Throws(Exception::class)
  @JvmStatic
  fun <T : Serializable> reserialize(original: T): T {
    val buffer = Buffer()
    val out = ObjectOutputStream(buffer.outputStream())
    out.writeObject(original)
    val input = ObjectInputStream(buffer.inputStream())
    return input.readObject() as T
  }

  /**
   * Returns a new buffer containing the data in `data` and a segment
   * layout determined by `dice`.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun bufferWithRandomSegmentLayout(dice: Random, data: ByteArray): Buffer {
    val result = Buffer()

    // Writing to result directly will yield packed segments. Instead, write to
    // other buffers, then write those buffers to result.
    var pos = 0
    var byteCount: Int
    while (pos < data.size) {
      byteCount = Segment.SIZE / 2 + dice.nextInt(Segment.SIZE / 2)
      if (byteCount > data.size - pos) byteCount = data.size - pos
      val offset = dice.nextInt(Segment.SIZE - byteCount)

      val segment = Buffer()
      segment.write(ByteArray(offset))
      segment.write(data, pos, byteCount)
      segment.skip(offset.toLong())

      result.write(segment, byteCount.toLong())
      pos += byteCount
    }

    return result
  }

  /**
   * Returns a new buffer containing the contents of `segments`, attempting to isolate each
   * string to its own segment in the returned buffer. This clones buffers so that segments are
   * shared, preventing compaction from occurring.
   */
  @Throws(Exception::class)
  @JvmStatic
  fun bufferWithSegments(vararg segments: String): Buffer {
    val result = Buffer()
    for (s in segments) {
      val offsetInSegment = if (s.length < Segment.SIZE) (Segment.SIZE - s.length) / 2 else 0
      val buffer = Buffer()
      buffer.writeUtf8("_".repeat(offsetInSegment))
      buffer.writeUtf8(s)
      buffer.skip(offsetInSegment.toLong())
      result.write(buffer.clone(), buffer.size)
    }
    return result
  }

  @JvmStatic
  fun makeSegments(source: ByteString): ByteString {
    val buffer = Buffer()
    for (i in 0 until source.size) {
      val segment = buffer.writableSegment(SEGMENT_SIZE)
      segment.data[segment.pos] = source[i]
      segment.limit++
      buffer.size++
    }
    return buffer.snapshot()
  }

  /** Remove all segments from the pool and return them as a list. */
  @JvmStatic
  internal fun takeAllPoolSegments(): List<Segment> {
    val result = mutableListOf<Segment>()
    while (SegmentPool.byteCount > 0) {
      result += SegmentPool.take()
    }
    return result
  }

  /** Returns a copy of `buffer` with no segments with `original`.  */
  @JvmStatic
  fun deepCopy(original: Buffer): Buffer {
    val result = Buffer()
    if (original.size == 0L) return result

    result.head = original.head!!.unsharedCopy()
    result.head!!.prev = result.head
    result.head!!.next = result.head!!.prev
    var s = original.head!!.next
    while (s !== original.head) {
      result.head!!.prev!!.push(s!!.unsharedCopy())
      s = s.next
    }
    result.size = original.size

    return result
  }

  @JvmStatic
  fun Int.reverseBytes(): Int {
    /* ktlint-disable no-multi-spaces indent */
    return (this and -0x1000000 ushr 24) or
      (this and 0x00ff0000 ushr  8) or
      (this and 0x0000ff00  shl  8) or
      (this and 0x000000ff  shl 24)
    /* ktlint-enable no-multi-spaces indent */
  }

  @JvmStatic
  fun Short.reverseBytes(): Short {
    val i = toInt() and 0xffff
    /* ktlint-disable no-multi-spaces indent */
    val reversed = (i and 0xff00 ushr 8) or
      (i and 0x00ff  shl 8)
    /* ktlint-enable no-multi-spaces indent */
    return reversed.toShort()
  }

  fun assumeNotWindows() = Assume.assumeFalse(isWindows())

  fun isWindows(): Boolean =
    System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")
}
