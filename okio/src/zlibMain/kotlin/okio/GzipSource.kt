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

@file:JvmName("-GzipSourceExtensions")
@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package okio

import kotlin.jvm.JvmName
import okio.internal.CRC32

/**
 * A source that uses [GZIP](https://www.ietf.org/rfc/rfc1952.txt) to
 * decompress data read from another source.
 */
class GzipSource(source: Source) : Source {

  /** The current section. Always progresses forward. */
  private var section = SECTION_HEADER

  /**
   * Our source should yield a GZIP header (which we consume directly), followed
   * by deflated bytes (which we consume via an InflaterSource), followed by a
   * GZIP trailer (which we also consume directly).
   */
  private val source = RealBufferedSource(source)

  /** The inflater used to decompress the deflated body. */
  private val inflater = Inflater(true)

  /**
   * The inflater source takes care of moving data between compressed source and
   * decompressed sink buffers.
   */
  private val inflaterSource = InflaterSource(this.source, inflater)

  /** Checksum used to check both the GZIP header and decompressed body. */
  private val crc = CRC32()

  @Throws(IOException::class)
  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    if (byteCount == 0L) return 0L

    // If we haven't consumed the header, we must consume it before anything else.
    if (section == SECTION_HEADER) {
      consumeHeader()
      section = SECTION_BODY
    }

    // Attempt to read at least a byte of the body. If we do, we're done.
    if (section == SECTION_BODY) {
      val offset = sink.size
      val result = inflaterSource.read(sink, byteCount)
      if (result != -1L) {
        updateCrc(sink, offset, result)
        return result
      }
      section = SECTION_TRAILER
    }

    // The body is exhausted; time to read the trailer. We always consume the
    // trailer before returning a -1 exhausted result; that way if you read to
    // the end of a GzipSource you guarantee that the CRC has been checked.
    if (section == SECTION_TRAILER) {
      consumeTrailer()
      section = SECTION_DONE

      // Gzip streams self-terminate: they return -1 before their underlying
      // source returns -1. Here we attempt to force the underlying stream to
      // return -1 which may trigger it to release its resources. If it doesn't
      // return -1, then our Gzip data finished prematurely!
      if (!source.exhausted()) {
        throw IOException("gzip finished without exhausting source")
      }
    }

    return -1
  }

  @Throws(IOException::class)
  private fun consumeHeader() {
    // Read the 10-byte header. We peek at the flags byte first so we know if we
    // need to CRC the entire header. Then we read the magic ID1ID2 sequence.
    // We can skip everything else in the first 10 bytes.
    // +---+---+---+---+---+---+---+---+---+---+
    // |ID1|ID2|CM |FLG|     MTIME     |XFL|OS | (more-->)
    // +---+---+---+---+---+---+---+---+---+---+
    source.require(10)
    val flags = source.buffer[3].toInt()
    val fhcrc = flags.getBit(FHCRC)
    if (fhcrc) updateCrc(source.buffer, 0, 10)

    val id1id2 = source.readShort()
    checkEqual("ID1ID2", 0x1f8b, id1id2.toInt())
    source.skip(8)

    // Skip optional extra fields.
    // +---+---+=================================+
    // | XLEN  |...XLEN bytes of "extra field"...| (more-->)
    // +---+---+=================================+
    if (flags.getBit(FEXTRA)) {
      source.require(2)
      if (fhcrc) updateCrc(source.buffer, 0, 2)
      val xlen = (source.buffer.readShortLe().toInt() and 0xffff).toLong()
      source.require(xlen)
      if (fhcrc) updateCrc(source.buffer, 0, xlen)
      source.skip(xlen)
    }

    // Skip an optional 0-terminated name.
    // +=========================================+
    // |...original file name, zero-terminated...| (more-->)
    // +=========================================+
    if (flags.getBit(FNAME)) {
      val index = source.indexOf(0)
      if (index == -1L) throw EOFException()
      if (fhcrc) updateCrc(source.buffer, 0, index + 1)
      source.skip(index + 1)
    }

    // Skip an optional 0-terminated comment.
    // +===================================+
    // |...file comment, zero-terminated...| (more-->)
    // +===================================+
    if (flags.getBit(FCOMMENT)) {
      val index = source.indexOf(0)
      if (index == -1L) throw EOFException()
      if (fhcrc) updateCrc(source.buffer, 0, index + 1)
      source.skip(index + 1)
    }

    // Confirm the optional header CRC.
    // +---+---+
    // | CRC16 |
    // +---+---+
    if (fhcrc) {
      checkEqual("FHCRC", source.readShortLe().toInt(), crc.getValue().toShort().toInt())
      crc.reset()
    }
  }

  @Throws(IOException::class)
  private fun consumeTrailer() {
    // Read the eight-byte trailer. Confirm the body's CRC and size.
    // +---+---+---+---+---+---+---+---+
    // |     CRC32     |     ISIZE     |
    // +---+---+---+---+---+---+---+---+
    checkEqual("CRC", source.readIntLe(), crc.getValue().toInt())
    checkEqual("ISIZE", source.readIntLe(), inflater.getBytesWritten().toInt())
  }

  override fun timeout(): Timeout = source.timeout()

  @Throws(IOException::class)
  override fun close() = inflaterSource.close()

  /** Updates the CRC with the given bytes.  */
  private fun updateCrc(buffer: Buffer, offset: Long, byteCount: Long) {
    var offset = offset
    var byteCount = byteCount
    // Skip segments that we aren't checksumming.
    var s = buffer.head!!
    while (offset >= s.limit - s.pos) {
      offset -= s.limit - s.pos
      s = s.next!!
    }

    // Checksum one segment at a time.
    while (byteCount > 0) {
      val pos = (s.pos + offset).toInt()
      val toUpdate = minOf(s.limit - pos, byteCount).toInt()
      crc.update(s.data, pos, toUpdate)
      byteCount -= toUpdate
      offset = 0
      s = s.next!!
    }
  }

  private fun checkEqual(name: String, expected: Int, actual: Int) {
    if (actual != expected) {
      throw IOException(
        "$name: " +
          "actual 0x${actual.toHexString().padStart(8, '0')} != " +
          "expected 0x${expected.toHexString().padStart(8, '0')}",
      )
    }
  }
}

private inline fun Int.getBit(bit: Int) = this shr bit and 1 == 1

private const val FHCRC = 1
private const val FEXTRA = 2
private const val FNAME = 3
private const val FCOMMENT = 4

private const val SECTION_HEADER: Byte = 0
private const val SECTION_BODY: Byte = 1
private const val SECTION_TRAILER: Byte = 2
private const val SECTION_DONE: Byte = 3

/**
 * Returns a [GzipSource] that gzip-decompresses this [Source] while reading.
 *
 * @see GzipSource
 */
inline fun Source.gzip() = GzipSource(this)
