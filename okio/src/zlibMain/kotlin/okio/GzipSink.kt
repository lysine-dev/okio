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

@file:JvmName("-GzipSinkExtensions")
@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package okio

import kotlin.jvm.JvmName
import okio.internal.CRC32
import okio.internal.DEFAULT_COMPRESSION

/**
 * A sink that uses [GZIP](https://www.ietf.org/rfc/rfc1952.txt) to
 * compress written data to another sink.
 *
 * ### Sync flush
 *
 * Aggressive flushing of this stream may result in reduced compression. Each
 * call to [flush] immediately compresses all currently-buffered data;
 * this early compression may be less effective than compression performed
 * without flushing.
 *
 * This is equivalent to using [Deflater] with the sync flush option.
 * This class does not offer any partial flush mechanism. For best performance,
 * only call [flush] when application behavior requires it.
 */
class GzipSink(sink: Sink) : Sink {
  /** Sink into which the GZIP format is written. */
  private val sink = RealBufferedSink(sink)

  /** The deflater used to compress the body. */
  @get:JvmName("deflater")
  val deflater = Deflater(DEFAULT_COMPRESSION, true /* No wrap */)

  /**
   * The deflater sink takes care of moving data between decompressed source and
   * compressed sink buffers.
   */
  private val deflaterSink = DeflaterSink(this.sink, deflater)

  private var closed = false

  /** Checksum calculated for the compressed body. */
  private val crc = CRC32()

  init {
    // Write the Gzip header directly into the buffer for the sink to avoid handling IOException.
    this.sink.buffer.apply {
      writeShort(0x1f8b) // Two-byte Gzip ID.
      writeByte(0x08) // 8 == Deflate compression method.
      writeByte(0x00) // No flags.
      writeInt(0x00) // No modification time.
      writeByte(0x00) // No extra flags.
      writeByte(0x00) // No OS.
    }
  }

  @Throws(IOException::class)
  override fun write(source: Buffer, byteCount: Long) {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    if (byteCount == 0L) return

    updateCrc(source, byteCount)
    deflaterSink.write(source, byteCount)
  }

  @Throws(IOException::class)
  override fun flush() = deflaterSink.flush()

  override fun timeout(): Timeout = sink.timeout()

  @Throws(IOException::class)
  override fun close() {
    if (closed) return

    // This method delegates to the DeflaterSink for finishing the deflate process
    // but keeps responsibility for releasing the deflater's resources. This is
    // necessary because writeFooter needs to query the processed byte count which
    // only works when the deflater is still open.

    var thrown: Throwable? = null
    try {
      deflaterSink.finishDeflate()
      writeFooter()
    } catch (e: Throwable) {
      thrown = e
    }

    try {
      deflater.end()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    try {
      sink.close()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    closed = true

    if (thrown != null) throw thrown
  }

  private fun writeFooter() {
    sink.writeIntLe(crc.getValue().toInt()) // CRC of original data.
    sink.writeIntLe(deflater.getBytesRead().toInt()) // Length of original data.
  }

  /** Updates the CRC with the given bytes. */
  private fun updateCrc(buffer: Buffer, byteCount: Long) {
    var head = buffer.head!!
    var remaining = byteCount
    while (remaining > 0) {
      val segmentLength = minOf(remaining, head.limit - head.pos).toInt()
      crc.update(head.data, head.pos, segmentLength)
      remaining -= segmentLength
      head = head.next!!
    }
  }

  @JvmName("-deprecated_deflater")
  @Deprecated(
    message = "moved to val",
    replaceWith = ReplaceWith(expression = "deflater"),
    level = DeprecationLevel.ERROR,
  )
  fun deflater() = deflater
}

/**
 * Returns a [GzipSink] that gzip-compresses to this [Sink] while writing.
 *
 * @see GzipSource
 */
inline fun Sink.gzip() = GzipSink(this)
