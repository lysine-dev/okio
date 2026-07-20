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
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset

actual sealed interface BufferedSink : Sink, WritableByteChannel {
  /** Returns this sink's internal buffer. */
  @Deprecated(
    message = "moved to val: use getBuffer() instead",
    replaceWith = ReplaceWith(expression = "buffer"),
    level = DeprecationLevel.WARNING,
  )
  fun buffer(): Buffer

  actual val buffer: Buffer

  @Throws(IOException::class)
  actual fun write(byteString: ByteString): BufferedSink

  @Throws(IOException::class)
  actual fun write(byteString: ByteString, offset: Int, byteCount: Int): BufferedSink

  @Throws(IOException::class)
  actual fun write(source: ByteArray): BufferedSink

  @Throws(IOException::class)
  actual fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink

  @Throws(IOException::class)
  actual fun writeAll(source: Source): Long

  @Throws(IOException::class)
  actual fun write(source: Source, byteCount: Long): BufferedSink

  @Throws(IOException::class)
  actual fun writeUtf8(string: String): BufferedSink

  @Throws(IOException::class)
  actual fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink

  @Throws(IOException::class)
  actual fun writeUtf8CodePoint(codePoint: Int): BufferedSink

  @Throws(IOException::class)
  fun writeString(string: String, charset: Charset): BufferedSink

  @Throws(IOException::class)
  fun writeString(string: String, beginIndex: Int, endIndex: Int, charset: Charset): BufferedSink

  @Throws(IOException::class)
  actual fun writeByte(b: Int): BufferedSink

  @Throws(IOException::class)
  actual fun writeShort(s: Int): BufferedSink

  @Throws(IOException::class)
  actual fun writeShortLe(s: Int): BufferedSink

  @Throws(IOException::class)
  actual fun writeInt(i: Int): BufferedSink

  @Throws(IOException::class)
  actual fun writeIntLe(i: Int): BufferedSink

  @Throws(IOException::class)
  actual fun writeLong(v: Long): BufferedSink

  @Throws(IOException::class)
  actual fun writeLongLe(v: Long): BufferedSink

  @Throws(IOException::class)
  actual fun writeDecimalLong(v: Long): BufferedSink

  @Throws(IOException::class)
  actual fun writeHexadecimalUnsignedLong(v: Long): BufferedSink

  @Throws(IOException::class)
  actual override fun flush()

  @Throws(IOException::class)
  actual fun emit(): BufferedSink

  @Throws(IOException::class)
  actual fun emitCompleteSegments(): BufferedSink

  /** Returns an output stream that writes to this sink. */
  fun outputStream(): OutputStream

  /**
   * Returns an appendable that encodes strings as UTF-8 and writes them to this sink.
   *
   * If the last character of any [Appendable.append] call is an unpaired high surrogate, that
   * character will be saved in an attempt to pair it with a low surrogate in the following call.
   */
  fun utf8Appendable(): Appendable
}
