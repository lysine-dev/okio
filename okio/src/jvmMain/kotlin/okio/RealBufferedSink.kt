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
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import okio.internal.commonClose
import okio.internal.commonEmit
import okio.internal.commonEmitCompleteSegments
import okio.internal.commonFlush
import okio.internal.commonTimeout
import okio.internal.commonToString
import okio.internal.commonWrite
import okio.internal.commonWriteAll
import okio.internal.commonWriteByte
import okio.internal.commonWriteDecimalLong
import okio.internal.commonWriteHexadecimalUnsignedLong
import okio.internal.commonWriteInt
import okio.internal.commonWriteIntLe
import okio.internal.commonWriteLong
import okio.internal.commonWriteLongLe
import okio.internal.commonWriteShort
import okio.internal.commonWriteShortLe
import okio.internal.commonWriteUtf8
import okio.internal.commonWriteUtf8CodePoint

internal actual class RealBufferedSink actual constructor(
  @JvmField actual val sink: Sink,
) : BufferedSink {
  @JvmField val bufferField = Buffer()

  @JvmField actual var closed: Boolean = false

  @Suppress("OVERRIDE_BY_INLINE") // Prevent internal code from calling the getter.
  actual override val buffer: Buffer
    inline get() = bufferField

  override fun buffer() = bufferField

  actual override fun write(source: Buffer, byteCount: Long) = commonWrite(source, byteCount)
  actual override fun write(byteString: ByteString) = commonWrite(byteString)
  actual override fun write(byteString: ByteString, offset: Int, byteCount: Int) =
    commonWrite(byteString, offset, byteCount)
  actual override fun writeUtf8(string: String) = commonWriteUtf8(string)
  actual override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int) =
    commonWriteUtf8(string, beginIndex, endIndex)

  actual override fun writeUtf8CodePoint(codePoint: Int) = commonWriteUtf8CodePoint(codePoint)

  override fun writeString(string: String, charset: Charset): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeString(string, charset)
    return emitCompleteSegments()
  }

  override fun writeString(
    string: String,
    beginIndex: Int,
    endIndex: Int,
    charset: Charset,
  ): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeString(string, beginIndex, endIndex, charset)
    return emitCompleteSegments()
  }

  actual override fun write(source: ByteArray) = commonWrite(source)
  actual override fun write(source: ByteArray, offset: Int, byteCount: Int) =
    commonWrite(source, offset, byteCount)

  override fun write(source: ByteBuffer): Int {
    check(!closed) { "closed" }
    val result = buffer.write(source)
    emitCompleteSegments()
    return result
  }

  actual override fun writeAll(source: Source) = commonWriteAll(source)
  actual override fun write(source: Source, byteCount: Long): BufferedSink = commonWrite(source, byteCount)
  actual override fun writeByte(b: Int) = commonWriteByte(b)
  actual override fun writeShort(s: Int) = commonWriteShort(s)
  actual override fun writeShortLe(s: Int) = commonWriteShortLe(s)
  actual override fun writeInt(i: Int) = commonWriteInt(i)
  actual override fun writeIntLe(i: Int) = commonWriteIntLe(i)
  actual override fun writeLong(v: Long) = commonWriteLong(v)
  actual override fun writeLongLe(v: Long) = commonWriteLongLe(v)
  actual override fun writeDecimalLong(v: Long) = commonWriteDecimalLong(v)
  actual override fun writeHexadecimalUnsignedLong(v: Long) = commonWriteHexadecimalUnsignedLong(v)
  actual override fun emitCompleteSegments() = commonEmitCompleteSegments()
  actual override fun emit() = commonEmit()

  override fun outputStream(): OutputStream {
    return object : OutputStream() {
      override fun write(b: Int) {
        if (closed) throw IOException("closed")
        buffer.writeByte(b.toByte().toInt())
        emitCompleteSegments()
      }

      override fun write(data: ByteArray, offset: Int, byteCount: Int) {
        if (closed) throw IOException("closed")
        buffer.write(data, offset, byteCount)
        emitCompleteSegments()
      }

      override fun flush() {
        // For backwards compatibility, a flush() on a closed stream is a no-op.
        if (!closed) {
          this@RealBufferedSink.flush()
        }
      }

      override fun close() = this@RealBufferedSink.close()

      override fun toString() = "${this@RealBufferedSink}.outputStream()"
    }
  }

  override fun utf8Appendable(): Appendable {
    return object : Appendable {
      private val delegate = buffer.utf8Appendable()

      override fun append(charSequence: CharSequence?) = apply {
        delegate.append(charSequence)
        emitCompleteSegments()
      }

      override fun append(
        charSequence: CharSequence?,
        start: Int,
        end: Int,
      ) = apply {
        delegate.append(charSequence, start, end)
        emitCompleteSegments()
      }

      override fun append(c: Char) = apply {
        delegate.append(c)
        emitCompleteSegments()
      }
    }
  }

  actual override fun flush() = commonFlush()

  override fun isOpen() = !closed

  actual override fun close() = commonClose()
  actual override fun timeout() = commonTimeout()
  override fun toString() = commonToString()
}
