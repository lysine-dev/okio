/*
 * Copyright (C) 2025 Square, Inc.
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
package okio.internal

import java.io.IOException
import java.net.Socket as JavaNetSocket
import java.util.concurrent.atomic.AtomicInteger
import okio.Buffer
import okio.Segment
import okio.SegmentPool
import okio.Sink
import okio.Socket
import okio.Source
import okio.checkOffsetAndCount
import okio.minOf

/**
 * Implement Okio's [okio.Socket] interface on a any `java.net.Socket`, which may itself be a secure
 * socket or other subclass.
 *
 * Note this behaves differently from [java.net.Socket.source()] and [java.net.Socket.sink()]
 * because those return streams that close the entire socket when they are closed, whereas this
 * class only closes the input or output respectively.
 */
internal class DefaultSocket(val socket: JavaNetSocket) : Socket {
  private var closeBits = AtomicInteger()

  override val source: Source = SocketSource()
  override val sink: Sink = SocketSink()

  override fun cancel() {
    socket.close()
  }

  override fun toString() = socket.toString()

  inner class SocketSink : Sink {
    private val outputStream = socket.outputStream
    private val timeout = SocketAsyncTimeout(socket)

    override fun write(source: Buffer, byteCount: Long) {
      checkOffsetAndCount(source.size, 0, byteCount)
      var remaining = byteCount
      while (remaining > 0L) {
        timeout.throwIfReached()
        val head = source.head!!
        val toCopy = minOf(remaining, head.limit - head.pos).toInt()
        timeout.withTimeout {
          outputStream.write(head.data, head.pos, toCopy)
        }

        head.pos += toCopy
        remaining -= toCopy
        source.size -= toCopy

        if (head.pos == head.limit) {
          source.head = head.pop()
          SegmentPool.recycle(head)
        }
      }
    }

    override fun flush() {
      timeout.withTimeout {
        outputStream.flush()
      }
    }

    override fun close() {
      timeout.withTimeout {
        when (closeBits.setBitsOrZero(SINK_CLOSED_BIT)) {
          // If setBitOrZero() returns 0, this sink is already closed.
          0 -> return

          // Release the socket if both streams are closed.
          ALL_CLOSED_BITS -> socket.close()

          // Close this stream only.
          else -> {
            if (socket.isClosed || socket.isOutputShutdown) return // Nothing to do.
            outputStream.flush()
            try {
              socket.shutdownOutput()
            } catch (_: UnsupportedOperationException) {
              // Android API 21's SSLSocket doesn't implement this! So close the whole socket.
              // https://github.com/lysine-dev/okhttp/issues/9123
              outputStream.close()
            }
          }
        }
      }
    }

    override fun timeout() = timeout

    override fun toString() = "sink($socket)"
  }

  inner class SocketSource : Source {
    private val inputStream = socket.inputStream
    private val timeout = SocketAsyncTimeout(socket)

    override fun read(sink: Buffer, byteCount: Long): Long {
      if (byteCount == 0L) return 0L
      require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
      timeout.throwIfReached()
      val tail = sink.writableSegment(1)
      val maxToCopy = minOf(byteCount, Segment.Companion.SIZE - tail.limit).toInt()
      val bytesRead = try {
        timeout.withTimeout {
          inputStream.read(tail.data, tail.limit, maxToCopy)
        }
      } catch (e: AssertionError) {
        if (e.isAndroidGetsocknameError) throw IOException(e)
        throw e
      }
      if (bytesRead == -1) {
        if (tail.pos == tail.limit) {
          // We allocated a tail segment, but didn't end up needing it. Recycle!
          sink.head = tail.pop()
          SegmentPool.recycle(tail)
        }
        return -1
      }
      tail.limit += bytesRead
      sink.size += bytesRead
      return bytesRead.toLong()
    }

    override fun close() {
      timeout.withTimeout {
        when (closeBits.setBitsOrZero(SOURCE_CLOSED_BIT)) {
          // If setBitOrZero() returns 0, this source is already closed.
          0 -> return

          // Release the socket if both streams are closed.
          ALL_CLOSED_BITS -> socket.close()

          // Close this stream only.
          else -> {
            if (socket.isClosed || socket.isInputShutdown) return // Nothing to do.
            try {
              socket.shutdownInput()
            } catch (_: UnsupportedOperationException) {
              // Android API 21's SSLSocket doesn't implement this! So close the whole socket.
              // https://github.com/lysine-dev/okhttp/issues/9123
              inputStream.close()
            }
          }
        }
      }
    }

    override fun timeout() = timeout

    override fun toString() = "source($socket)"
  }
}

private const val SINK_CLOSED_BIT = 1
private const val SOURCE_CLOSED_BIT = 2
private const val ALL_CLOSED_BITS = SINK_CLOSED_BIT or SOURCE_CLOSED_BIT
