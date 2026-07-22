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
package okio

import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_SYNC_FLUSH

actual class DeflaterSink internal actual constructor(
  private val sink: BufferedSink,
  internal val deflater: Deflater,
) : Sink {
  actual constructor(
    sink: Sink,
    deflater: Deflater,
  ) : this(sink.buffer(), deflater)

  actual override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)

    deflater.flush = Z_NO_FLUSH
    deflater.dataProcessor.writeBytesFromSource(
      source = source,
      sourceExactByteCount = byteCount,
      target = sink,
    )
  }

  actual override fun flush() {
    deflater.flush = Z_SYNC_FLUSH
    deflater.dataProcessor.writeBytesFromSource(
      source = null,
      sourceExactByteCount = 0L,
      target = sink,
    )

    sink.flush()
  }

  actual override fun timeout(): Timeout {
    return sink.timeout()
  }

  internal actual fun finishDeflate() {
    deflater.flush = Z_FINISH
    deflater.dataProcessor.writeBytesFromSource(
      source = null,
      sourceExactByteCount = 0L,
      target = sink,
    )
  }

  actual override fun close() {
    if (deflater.dataProcessor.closed) return

    // We must close the deflater and the target, even if flushing fails. Otherwise, we'll leak
    // resources! (And we re-throw whichever exception we catch first.)
    var thrown: Throwable? = null

    try {
      finishDeflate()
    } catch (e: Throwable) {
      thrown = e
    }

    deflater.dataProcessor.close()

    try {
      sink.close()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    if (thrown != null) throw thrown
  }
}
