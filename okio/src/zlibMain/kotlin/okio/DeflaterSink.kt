/*
 * Copyright (C) 2024 Square, Inc.
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

@file:JvmName("-DeflaterSinkExtensions")

package okio

import kotlin.jvm.JvmName

/**
 * A sink that uses [DEFLATE](https://tools.ietf.org/html/rfc1951) to
 * compress data written to another source.
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
expect class DeflaterSink
/**
 * This internal constructor shares a buffer with its trusted caller. In general, we can't share a
 * BufferedSource because the deflater holds input bytes until they are inflated.
 */
internal constructor(
  sink: BufferedSink,
  deflater: Deflater,
) : Sink {
  constructor(sink: Sink, deflater: Deflater)

  internal fun finishDeflate()

  override fun write(source: Buffer, byteCount: Long)
  override fun flush()
  override fun timeout(): Timeout
  override fun close()
}

/**
 * Returns an [DeflaterSink] that DEFLATE-compresses data to this [Sink] while writing.
 *
 * @see DeflaterSink
 */
inline fun Sink.deflate(deflater: Deflater = Deflater()): DeflaterSink =
  DeflaterSink(this, deflater)
