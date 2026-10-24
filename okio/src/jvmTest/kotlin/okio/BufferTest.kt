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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.util.Arrays
import java.util.Random
import kotlin.text.Charsets.UTF_8
import okio.ByteString.Companion.decodeHex
import okio.TestUtil.SEGMENT_POOL_MAX_SIZE
import okio.TestUtil.SEGMENT_SIZE
import okio.TestUtil.bufferWithRandomSegmentLayout
import okio.TestUtil.segmentPoolByteCount
import okio.TestUtil.segmentSizes
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * Tests solely for the behavior of Buffer's implementation. For generic BufferedSink or
 * BufferedSource behavior use BufferedSinkTest or BufferedSourceTest, respectively.
 */
class BufferTest {
  @Test
  fun readAndWriteUtf8() {
    val buffer = Buffer()
    buffer.writeUtf8("ab")
    assertEquals(2, buffer.size)
    buffer.writeUtf8("cdef")
    assertEquals(6, buffer.size)
    assertEquals("abcd", buffer.readUtf8(4))
    assertEquals(2, buffer.size)
    assertEquals("ef", buffer.readUtf8(2))
    assertEquals(0, buffer.size)
    try {
      buffer.readUtf8(1)
      fail()
    } catch (expected: EOFException) {
    }
  }

  /** Buffer's toString is the same as ByteString's.  */
  @Test
  fun bufferToString() {
    assertEquals("[size=0]", Buffer().toString())
    assertEquals(
      "[text=a\\r\\nb\\nc\\rd\\\\e]",
      Buffer().writeUtf8("a\r\nb\nc\rd\\e").toString(),
    )
    assertEquals(
      "[text=Tyrannosaur]",
      Buffer().writeUtf8("Tyrannosaur").toString(),
    )
    assertEquals(
      "[text=təˈranəˌsôr]",
      Buffer()
        .write("74c999cb8872616ec999cb8c73c3b472".decodeHex())
        .toString(),
    )
    assertEquals(
      "[hex=0000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "0000000000000000000000000000000000000000000000000000]",
      Buffer().write(ByteArray(64)).toString(),
    )
  }

  @Test
  fun multipleSegmentBuffers() {
    val buffer = Buffer()
    buffer.writeUtf8("a".repeat(1000))
    buffer.writeUtf8("b".repeat(2500))
    buffer.writeUtf8("c".repeat(5000))
    buffer.writeUtf8("d".repeat(10000))
    buffer.writeUtf8("e".repeat(25000))
    buffer.writeUtf8("f".repeat(50000))
    assertEquals("a".repeat(999), buffer.readUtf8(999)) // a...a
    assertEquals("a" + "b".repeat(2500) + "c", buffer.readUtf8(2502)) // ab...bc
    assertEquals("c".repeat(4998), buffer.readUtf8(4998)) // c...c
    assertEquals("c" + "d".repeat(10000) + "e", buffer.readUtf8(10002)) // cd...de
    assertEquals("e".repeat(24998), buffer.readUtf8(24998)) // e...e
    assertEquals("e" + "f".repeat(50000), buffer.readUtf8(50001)) // ef...f
    assertEquals(0, buffer.size)
  }

  @Test
  fun fillAndDrainPool() {
    val buffer = Buffer()

    // Take 2 * MAX_SIZE segments. This will drain the pool, even if other tests filled it.
    buffer.write(ByteArray(SEGMENT_POOL_MAX_SIZE))
    buffer.write(ByteArray(SEGMENT_POOL_MAX_SIZE))
    assertEquals(0, segmentPoolByteCount().toLong())

    // Recycle MAX_SIZE segments. They're all in the pool.
    buffer.skip(SEGMENT_POOL_MAX_SIZE.toLong())
    assertEquals(SEGMENT_POOL_MAX_SIZE.toLong(), segmentPoolByteCount().toLong())

    // Recycle MAX_SIZE more segments. The pool is full so they get garbage collected.
    buffer.skip(SEGMENT_POOL_MAX_SIZE.toLong())
    assertEquals(SEGMENT_POOL_MAX_SIZE.toLong(), segmentPoolByteCount().toLong())

    // Take MAX_SIZE segments to drain the pool.
    buffer.write(ByteArray(SEGMENT_POOL_MAX_SIZE))
    assertEquals(0, segmentPoolByteCount().toLong())

    // Take MAX_SIZE more segments. The pool is drained so these will need to be allocated.
    buffer.write(ByteArray(SEGMENT_POOL_MAX_SIZE))
    assertEquals(0, segmentPoolByteCount().toLong())
  }

  @Test
  fun moveBytesBetweenBuffersShareSegment() {
    val size: Int = SEGMENT_SIZE / 2 - 1
    val segmentSizes = moveBytesBetweenBuffers("a".repeat(size), "b".repeat(size))
    assertEquals(Arrays.asList(size * 2), segmentSizes)
  }

  @Test
  fun moveBytesBetweenBuffersReassignSegment() {
    val size: Int = SEGMENT_SIZE / 2 + 1
    val segmentSizes = moveBytesBetweenBuffers("a".repeat(size), "b".repeat(size))
    assertEquals(Arrays.asList(size, size), segmentSizes)
  }

  @Test
  fun moveBytesBetweenBuffersMultipleSegments() {
    val size: Int = 3 * SEGMENT_SIZE + 1
    val segmentSizes = moveBytesBetweenBuffers("a".repeat(size), "b".repeat(size))
    assertEquals(
      listOf(
        SEGMENT_SIZE,
        SEGMENT_SIZE,
        SEGMENT_SIZE,
        1,
        SEGMENT_SIZE,
        SEGMENT_SIZE,
        SEGMENT_SIZE,
        1,
      ),
      segmentSizes,
    )
  }

  private fun moveBytesBetweenBuffers(vararg contents: String): List<Int> {
    val expected = StringBuilder()
    val buffer = Buffer()
    for (s in contents) {
      val source = Buffer()
      source.writeUtf8(s)
      buffer.writeAll(source)
      expected.append(s)
    }
    val segmentSizes = segmentSizes(buffer)
    assertEquals(expected.toString(), buffer.readUtf8(expected.length.toLong()))
    return segmentSizes
  }

  /** The big part of source's first segment is being moved.  */
  @Test
  fun writeSplitSourceBufferLeft() {
    val writeSize: Int = SEGMENT_SIZE / 2 + 1
    val sink = Buffer()
    sink.writeUtf8("b".repeat(SEGMENT_SIZE - 10))
    val source = Buffer()
    source.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
    sink.write(source, writeSize.toLong())
    assertEquals(Arrays.asList<Int>(SEGMENT_SIZE - 10, writeSize), segmentSizes(sink))
    assertEquals(Arrays.asList<Int>(SEGMENT_SIZE - writeSize, SEGMENT_SIZE), segmentSizes(source))
  }

  /** The big part of source's first segment is staying put.  */
  @Test
  fun writeSplitSourceBufferRight() {
    val writeSize: Int = SEGMENT_SIZE / 2 - 1
    val sink = Buffer()
    sink.writeUtf8("b".repeat(SEGMENT_SIZE - 10))
    val source = Buffer()
    source.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
    sink.write(source, writeSize.toLong())
    assertEquals(Arrays.asList<Int>(SEGMENT_SIZE - 10, writeSize), segmentSizes(sink))
    assertEquals(Arrays.asList<Int>(SEGMENT_SIZE - writeSize, SEGMENT_SIZE), segmentSizes(source))
  }

  @Test
  fun writePrefixDoesntSplit() {
    val sink = Buffer()
    sink.writeUtf8("b".repeat(10))
    val source = Buffer()
    source.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
    sink.write(source, 20)
    assertEquals(mutableListOf(30), segmentSizes(sink))
    assertEquals(Arrays.asList<Int>(SEGMENT_SIZE - 20, SEGMENT_SIZE), segmentSizes(source))
    assertEquals(30, sink.size)
    assertEquals((SEGMENT_SIZE * 2 - 20).toLong(), source.size)
  }

  @Test
  fun writePrefixDoesntSplitButRequiresCompact() {
    val sink = Buffer()
    sink.writeUtf8("b".repeat(SEGMENT_SIZE - 10)) // limit = size - 10
    sink.readUtf8((SEGMENT_SIZE - 20).toLong()) // pos = size = 20
    val source = Buffer()
    source.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
    sink.write(source, 20)
    assertEquals(mutableListOf(30), segmentSizes(sink))
    assertEquals(Arrays.asList<Int>(SEGMENT_SIZE - 20, SEGMENT_SIZE), segmentSizes(source))
    assertEquals(30, sink.size)
    assertEquals((SEGMENT_SIZE * 2 - 20).toLong(), source.size)
  }

  @Test
  fun copyToSpanningSegments() {
    val source = Buffer()
    source.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
    source.writeUtf8("b".repeat(SEGMENT_SIZE * 2))
    val out = ByteArrayOutputStream()
    source.copyTo(out, 10, (SEGMENT_SIZE * 3).toLong())
    assertEquals(
      "a".repeat(SEGMENT_SIZE * 2 - 10) + "b".repeat(SEGMENT_SIZE + 10),
      out.toString(),
    )
    assertEquals(
      "a".repeat(SEGMENT_SIZE * 2) + "b".repeat(SEGMENT_SIZE * 2),
      source.readUtf8((SEGMENT_SIZE * 4).toLong()),
    )
  }

  @Test
  fun copyToStream() {
    val buffer = Buffer().writeUtf8("hello, world!")
    val out = ByteArrayOutputStream()
    buffer.copyTo(out)
    val outString = out.toByteArray().toString(UTF_8)
    assertEquals("hello, world!", outString)
    assertEquals("hello, world!", buffer.readUtf8())
  }

  @Test
  fun writeToSpanningSegments() {
    val buffer = Buffer()
    buffer.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
    buffer.writeUtf8("b".repeat(SEGMENT_SIZE * 2))
    val out = ByteArrayOutputStream()
    buffer.skip(10)
    buffer.writeTo(out, (SEGMENT_SIZE * 3).toLong())
    assertEquals(
      "a".repeat(SEGMENT_SIZE * 2 - 10) + "b".repeat(SEGMENT_SIZE + 10),
      out.toString(),
    )
    assertEquals("b".repeat(SEGMENT_SIZE - 10), buffer.readUtf8(buffer.size))
  }

  @Test
  fun writeToStream() {
    val buffer = Buffer().writeUtf8("hello, world!")
    val out = ByteArrayOutputStream()
    buffer.writeTo(out)
    val outString = out.toByteArray().toString(UTF_8)
    assertEquals("hello, world!", outString)
    assertEquals(0, buffer.size)
  }

  @Test
  fun readFromStream() {
    val `in`: InputStream = ByteArrayInputStream("hello, world!".toByteArray(UTF_8))
    val buffer = Buffer()
    buffer.readFrom(`in`)
    val out = buffer.readUtf8()
    assertEquals("hello, world!", out)
  }

  @Test
  fun readFromSpanningSegments() {
    val `in`: InputStream = ByteArrayInputStream("hello, world!".toByteArray(UTF_8))
    val buffer = Buffer().writeUtf8("a".repeat(SEGMENT_SIZE - 10))
    buffer.readFrom(`in`)
    val out = buffer.readUtf8()
    assertEquals("a".repeat(SEGMENT_SIZE - 10) + "hello, world!", out)
  }

  @Test
  fun readFromStreamWithCount() {
    val `in`: InputStream = ByteArrayInputStream("hello, world!".toByteArray(UTF_8))
    val buffer = Buffer()
    buffer.readFrom(`in`, 10)
    val out = buffer.readUtf8()
    assertEquals("hello, wor", out)
  }

  @Test
  fun readFromDoesNotLeaveEmptyTailSegment() {
    val buffer = Buffer()
    buffer.readFrom(ByteArrayInputStream(ByteArray(SEGMENT_SIZE)))
    assertNoEmptySegments(buffer)
  }

  @Test
  fun moveAllRequestedBytesWithRead() {
    val sink = Buffer()
    sink.writeUtf8("a".repeat(10))
    val source = Buffer()
    source.writeUtf8("b".repeat(15))
    assertEquals(10, source.read(sink, 10))
    assertEquals(20, sink.size)
    assertEquals(5, source.size)
    assertEquals("a".repeat(10) + "b".repeat(10), sink.readUtf8(20))
  }

  @Test
  fun moveFewerThanRequestedBytesWithRead() {
    val sink = Buffer()
    sink.writeUtf8("a".repeat(10))
    val source = Buffer()
    source.writeUtf8("b".repeat(20))
    assertEquals(20, source.read(sink, 25))
    assertEquals(30, sink.size)
    assertEquals(0, source.size)
    assertEquals("a".repeat(10) + "b".repeat(20), sink.readUtf8(30))
  }

  @Test
  fun indexOfWithOffset() {
    val buffer = Buffer()
    val halfSegment: Int = SEGMENT_SIZE / 2
    buffer.writeUtf8("a".repeat(halfSegment))
    buffer.writeUtf8("b".repeat(halfSegment))
    buffer.writeUtf8("c".repeat(halfSegment))
    buffer.writeUtf8("d".repeat(halfSegment))
    assertEquals(0, buffer.indexOf('a'.code.toByte(), 0))
    assertEquals((halfSegment - 1).toLong(), buffer.indexOf('a'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals(halfSegment.toLong(), buffer.indexOf('b'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 2).toLong(), buffer.indexOf('c'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 2).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 3).toLong()))
    assertEquals((halfSegment * 4 - 1).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 4 - 1).toLong()))
  }

  @Test
  fun byteAt() {
    val buffer = Buffer()
    buffer.writeUtf8("a")
    buffer.writeUtf8("b".repeat(SEGMENT_SIZE))
    buffer.writeUtf8("c")
    assertEquals('a'.code.toLong(), buffer[0].toLong())
    assertEquals('a'.code.toLong(), buffer[0].toLong()) // getByte doesn't mutate!
    assertEquals('c'.code.toLong(), buffer[buffer.size - 1].toLong())
    assertEquals('b'.code.toLong(), buffer[buffer.size - 2].toLong())
    assertEquals('b'.code.toLong(), buffer[buffer.size - 3].toLong())
  }

  @Test
  fun getByteOfEmptyBuffer() {
    val buffer = Buffer()
    try {
      buffer[0]
      fail()
    } catch (expected: IndexOutOfBoundsException) {
    }
  }

  @Test
  fun writePrefixToEmptyBuffer() {
    val sink = Buffer()
    val source = Buffer()
    source.writeUtf8("abcd")
    sink.write(source, 2)
    assertEquals("ab", sink.readUtf8(2))
  }

  @Test
  fun cloneDoesNotObserveWritesToOriginal() {
    val original = Buffer()
    val clone = original.clone()
    original.writeUtf8("abc")
    assertEquals(0, clone.size)
  }

  @Test
  fun cloneDoesNotObserveReadsFromOriginal() {
    val original = Buffer()
    original.writeUtf8("abc")
    val clone = original.clone()
    assertEquals("abc", original.readUtf8(3))
    assertEquals(3, clone.size)
    assertEquals("ab", clone.readUtf8(2))
  }

  @Test
  fun originalDoesNotObserveWritesToClone() {
    val original = Buffer()
    val clone = original.clone()
    clone.writeUtf8("abc")
    assertEquals(0, original.size)
  }

  @Test
  fun originalDoesNotObserveReadsFromClone() {
    val original = Buffer()
    original.writeUtf8("abc")
    val clone = original.clone()
    assertEquals("abc", clone.readUtf8(3))
    assertEquals(3, original.size)
    assertEquals("ab", original.readUtf8(2))
  }

  @Test
  fun cloneMultipleSegments() {
    val original = Buffer()
    original.writeUtf8("a".repeat(SEGMENT_SIZE * 3))
    val clone = original.clone()
    original.writeUtf8("b".repeat(SEGMENT_SIZE * 3))
    clone.writeUtf8("c".repeat(SEGMENT_SIZE * 3))
    assertEquals(
      "a".repeat(SEGMENT_SIZE * 3) + "b".repeat(SEGMENT_SIZE * 3),
      original.readUtf8((SEGMENT_SIZE * 6).toLong()),
    )
    assertEquals(
      "a".repeat(SEGMENT_SIZE * 3) + "c".repeat(SEGMENT_SIZE * 3),
      clone.readUtf8((SEGMENT_SIZE * 6).toLong()),
    )
  }

  @Test
  fun equalsAndHashCodeEmpty() {
    val a = Buffer()
    val b = Buffer()
    assertEquals(a, b)
    assertEquals(a.hashCode().toLong(), b.hashCode().toLong())
  }

  @Test
  fun equalsAndHashCode() {
    val a = Buffer().writeUtf8("dog")
    val b = Buffer().writeUtf8("hotdog")
    Assertions.assertNotEquals(a, b)
    Assertions.assertNotEquals(a.hashCode().toLong(), b.hashCode().toLong())
    b.readUtf8(3) // Leaves b containing 'dog'.
    assertEquals(a, b)
    assertEquals(a.hashCode().toLong(), b.hashCode().toLong())
  }

  @Test
  fun equalsAndHashCodeSpanningSegments() {
    val data = ByteArray(1024 * 1024)
    val dice = Random(0)
    dice.nextBytes(data)
    val a = bufferWithRandomSegmentLayout(dice, data)
    val b = bufferWithRandomSegmentLayout(dice, data)
    assertEquals(a, b)
    assertEquals(a.hashCode().toLong(), b.hashCode().toLong())
    data[data.size / 2]++ // Change a single byte.
    val c = bufferWithRandomSegmentLayout(dice, data)
    Assertions.assertNotEquals(a, c)
    Assertions.assertNotEquals(a.hashCode().toLong(), c.hashCode().toLong())
  }

  @Test
  fun bufferInputStreamByteByByte() {
    val source = Buffer()
    source.writeUtf8("abc")
    val `in` = source.inputStream()
    assertEquals(3, `in`.available().toLong())
    assertEquals('a'.code.toLong(), `in`.read().toLong())
    assertEquals('b'.code.toLong(), `in`.read().toLong())
    assertEquals('c'.code.toLong(), `in`.read().toLong())
    assertEquals(-1, `in`.read().toLong())
    assertEquals(0, `in`.available().toLong())
  }

  @Test
  fun bufferInputStreamBulkReads() {
    val source = Buffer()
    source.writeUtf8("abc")
    val byteArray = ByteArray(4)
    byteArray.fill(-5)
    val `in` = source.inputStream()
    assertEquals(3, `in`.read(byteArray).toLong())
    assertEquals("[97, 98, 99, -5]", Arrays.toString(byteArray))
    byteArray.fill(-7)
    assertEquals(-1, `in`.read(byteArray).toLong())
    assertEquals("[-7, -7, -7, -7]", Arrays.toString(byteArray))
  }

  /**
   * When writing data that's already buffered, there's no reason to page the
   * data by segment.
   */
  @Test
  fun readAllWritesAllSegmentsAtOnce() {
    val write1 = Buffer().writeUtf8(
      "" +
        "a".repeat(SEGMENT_SIZE) +
        "b".repeat(SEGMENT_SIZE) +
        "c".repeat(SEGMENT_SIZE),
    )
    val source = Buffer().writeUtf8(
      "" +
        "a".repeat(SEGMENT_SIZE) +
        "b".repeat(SEGMENT_SIZE) +
        "c".repeat(SEGMENT_SIZE),
    )
    val mockSink = MockSink()
    assertEquals((SEGMENT_SIZE * 3).toLong(), source.readAll(mockSink))
    assertEquals(0, source.size)
    mockSink.assertLog("write(" + write1 + ", " + write1.size + ")")
  }

  @Test
  fun writeAllMultipleSegments() {
    val source = Buffer().writeUtf8("a".repeat(SEGMENT_SIZE * 3))
    val sink = Buffer()
    assertEquals((SEGMENT_SIZE * 3).toLong(), sink.writeAll(source))
    assertEquals(0, source.size)
    assertEquals("a".repeat(SEGMENT_SIZE * 3), sink.readUtf8())
  }

  @Test
  fun copyTo() {
    val source = Buffer()
    source.writeUtf8("party")
    val target = Buffer()
    source.copyTo(target, 1, 3)
    assertEquals("art", target.readUtf8())
    assertEquals("party", source.readUtf8())
  }

  @Test
  fun copyToOnSegmentBoundary() {
    val `as` = "a".repeat(SEGMENT_SIZE)
    val bs = "b".repeat(SEGMENT_SIZE)
    val cs = "c".repeat(SEGMENT_SIZE)
    val ds = "d".repeat(SEGMENT_SIZE)
    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)
    source.writeUtf8(cs)
    val target = Buffer()
    target.writeUtf8(ds)
    source.copyTo(target, `as`.length.toLong(), (bs.length + cs.length).toLong())
    assertEquals(ds + bs + cs, target.readUtf8())
  }

  @Test
  fun copyToOffSegmentBoundary() {
    val `as` = "a".repeat(SEGMENT_SIZE - 1)
    val bs = "b".repeat(SEGMENT_SIZE + 2)
    val cs = "c".repeat(SEGMENT_SIZE - 4)
    val ds = "d".repeat(SEGMENT_SIZE + 8)
    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)
    source.writeUtf8(cs)
    val target = Buffer()
    target.writeUtf8(ds)
    source.copyTo(target, `as`.length.toLong(), (bs.length + cs.length).toLong())
    assertEquals(ds + bs + cs, target.readUtf8())
  }

  @Test
  fun copyToSourceAndTargetCanBeTheSame() {
    val `as` = "a".repeat(SEGMENT_SIZE)
    val bs = "b".repeat(SEGMENT_SIZE)
    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)
    source.copyTo(source, 0, source.size)
    assertEquals(`as` + bs + `as` + bs, source.readUtf8())
  }

  @Test
  fun copyToEmptySource() {
    val source = Buffer()
    val target = Buffer().writeUtf8("aaa")
    source.copyTo(target, 0L, 0L)
    assertEquals("", source.readUtf8())
    assertEquals("aaa", target.readUtf8())
  }

  @Test
  fun copyToEmptyTarget() {
    val source = Buffer().writeUtf8("aaa")
    val target = Buffer()
    source.copyTo(target, 0L, 3L)
    assertEquals("aaa", source.readUtf8())
    assertEquals("aaa", target.readUtf8())
  }

  @Test
  fun snapshotReportsAccurateSize() {
    val buf = Buffer().write(byteArrayOf(0, 1, 2, 3))
    assertEquals(1, buf.snapshot(1).size.toLong())
  }
}
