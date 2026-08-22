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

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class SocketTimeoutTest {
  @Test
  fun readWithoutTimeout() {
    val socket = socket(ONE_MB, 0)
    val source = socket.source().buffer()
    source.timeout().timeout(5000, TimeUnit.MILLISECONDS)
    source.require(ONE_MB.toLong())
    socket.close()
  }

  @Test
  fun readWithTimeout() {
    val socket = socket(0, 0)
    val source = socket.source().buffer()
    source.timeout().timeout(250, TimeUnit.MILLISECONDS)
    try {
      source.require(ONE_MB.toLong())
      fail()
    } catch (expected: SocketTimeoutException) {
    }
    socket.close()
  }

  @Test
  fun writeWithoutTimeout() {
    val socket = socket(0, ONE_MB)
    val sink: Sink = socket.sink().buffer()
    sink.timeout().timeout(500, TimeUnit.MILLISECONDS)
    val data = ByteArray(ONE_MB)
    sink.write(Buffer().write(data), data.size.toLong())
    sink.flush()
    socket.close()
  }

  @Test
  fun writeWithTimeout() {
    val socket = socket(0, 0)
    val sink = socket.sink()
    sink.timeout().timeout(500, TimeUnit.MILLISECONDS)
    val data = ByteArray(ONE_MB)
    val start = System.nanoTime()
    try {
      sink.write(Buffer().write(data), data.size.toLong())
      sink.flush()
      fail()
    } catch (expected: SocketTimeoutException) {
    }
    val elapsed = System.nanoTime() - start
    socket.close()
    assertTrue(TimeUnit.NANOSECONDS.toMillis(elapsed) >= 500, "elapsed: $elapsed")
    assertTrue(TimeUnit.NANOSECONDS.toMillis(elapsed) <= 750, "elapsed: $elapsed")
  }

  companion object {
    // The size of the socket buffers to use. Less than half the data transferred during tests to
    // ensure send and receive buffers are flooded and any necessary blocking behavior takes place.
    private const val SOCKET_BUFFER_SIZE = 256 * 1024
    private const val ONE_MB = 1024 * 1024

    /**
     * Returns a socket that can read `readableByteCount` incoming bytes and
     * will accept `writableByteCount` written bytes. The socket will idle
     * for 5 seconds when the required data has been read and written.
     */
    fun socket(readableByteCount: Int, writableByteCount: Int): Socket {
      val inetAddress = InetAddress.getByName("localhost")
      val serverSocket = ServerSocket(0, 50, inetAddress)
      serverSocket.reuseAddress = true
      serverSocket.receiveBufferSize = SOCKET_BUFFER_SIZE
      val peer: Thread = object : Thread("peer") {
        override fun run() {
          var socket: Socket? = null
          try {
            socket = serverSocket.accept()
            socket.sendBufferSize = SOCKET_BUFFER_SIZE
            writeFully(socket.getOutputStream(), readableByteCount)
            readFully(socket.getInputStream(), writableByteCount)
            sleep(5000) // Sleep 5 seconds so the peer can close the connection.
          } catch (ignored: Exception) {
          } finally {
            try {
              socket?.close()
            } catch (ignored: IOException) {
            }
          }
        }
      }
      peer.start()
      val socket = Socket(serverSocket.inetAddress, serverSocket.localPort)
      socket.receiveBufferSize = SOCKET_BUFFER_SIZE
      socket.sendBufferSize = SOCKET_BUFFER_SIZE
      return socket
    }

    private fun writeFully(out: OutputStream, byteCount: Int) {
      out.write(ByteArray(byteCount))
      out.flush()
    }

    private fun readFully(`in`: InputStream, byteCount: Int): ByteArray {
      var count = 0
      val result = ByteArray(byteCount)
      while (count < byteCount) {
        val read = `in`.read(result, count, result.size - count)
        if (read == -1) throw EOFException()
        count += read
      }
      return result
    }
  }
}
