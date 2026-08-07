/*
 * Copyright (C) 2019 Square, Inc.
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

/** Essential APIs for working with Okio. */
@file:JvmMultifileClass
@file:JvmName("Okio")

package okio

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Returns a new source that buffers reads from this. The returned source will perform bulk
 * reads into its in-memory buffer. Use this wherever you read a source to get an ergonomic and
 * efficient access to data.
 */
fun Source.buffer(): BufferedSource = RealBufferedSource(this)

/**
 * Returns a new sink that buffers writes to this. The returned sink will batch writes to this.
 * Use this wherever you write to a sink to get an ergonomic and efficient access to data.
 */
fun Sink.buffer(): BufferedSink = RealBufferedSink(this)

/** Returns a sink that writes nowhere. */
@JvmName("blackhole")
fun blackholeSink(): Sink = BlackholeSink()

private class BlackholeSink : Sink {
  override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)
  override fun flush() {}
  override fun timeout() = Timeout.NONE
  override fun close() {}
}

/** Execute [block] then close this. This will be closed even if [block] throws. */
inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }

  var thrown: Throwable? = null

  val result = try {
    block(this)
  } catch (t: Throwable) {
    thrown = t
    null
  } finally {
    try {
      this?.close()
    } catch (t: Throwable) {
      if (thrown == null) {
        thrown = t
      } else {
        thrown.addSuppressed(t)
      }
    }
  }

  if (thrown != null) throw thrown
  @Suppress("UNCHECKED_CAST")
  return result as R
}

/**
 * Returns a new source that returns exactly [byteCount] bytes from this.
 *
 * Closing the returned source closes this.
 *
 * @param throwIfSourceIsLonger true to also throw if this has more than [byteCount] bytes.
 *   This works by attempting to read more than [byteCount] bytes.
 *
 * @throws [EOFException] if this returns fewer than [byteCount] bytes.
 */
@JvmOverloads
fun Source.limit(
  byteCount: Long,
  throwIfSourceIsLonger: Boolean = false,
): Source = LimitSource(this, byteCount, throwIfSourceIsLonger)

private class LimitSource(
  delegate: Source,
  private val byteCount: Long,
  private val throwIfSourceIsLonger: Boolean,
) : ForwardingSource(delegate) {
  private var bytesReceived = 0L

  init {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
  }

  override fun read(sink: Buffer, byteCount: Long): Long {
    val remainingByteCount = this.byteCount - bytesReceived
    val toRead = when {
      remainingByteCount < 0 -> {
        throw IOException("expected ${this.byteCount} bytes but got $bytesReceived")
      }

      throwIfSourceIsLonger -> {
        // Attempt to read an extra byte, so we can detect if too many bytes are returned.
        byteCount.coerceAtMost(remainingByteCount + 1)
      }

      remainingByteCount == 0L -> {
        return -1L // Already read exactly the promised size.
      }

      else -> byteCount.coerceAtMost(remainingByteCount)
    }

    val result = super.read(sink, toRead)

    if (result == -1L) {
      if (remainingByteCount == 0L) return -1L
      throw EOFException("expected ${this.byteCount} bytes but got $bytesReceived")
    }

    bytesReceived += result

    val beyondLimitByteCount = bytesReceived - this.byteCount
    if (beyondLimitByteCount > 0) {
      // If we received bytes beyond the limit, don't return them to the caller.
      sink.truncateToSize(sink.size - beyondLimitByteCount)
      throw IOException("expected ${this.byteCount} bytes but got $bytesReceived")
    }

    return result
  }

  private fun Buffer.truncateToSize(newByteCount: Long) {
    val scratch = Buffer()
    scratch.writeAll(this)
    write(scratch, newByteCount)
    scratch.clear()
  }
}
