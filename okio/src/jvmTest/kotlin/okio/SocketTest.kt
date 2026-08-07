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
package okio

import app.cash.burst.Burst
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import okio.internal.DefaultSocket
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

@Burst
class SocketTest(val factory: Factory = Factory.Default) {
  private lateinit var socket: Socket
  private lateinit var peerSocket: Socket
  private lateinit var peer: AsyncSocket

  @Before
  fun setUp() {
    val socketPair = factory.createSocketPair()
    this.socket = socketPair[0]
    this.peerSocket = socketPair[1]
    this.peer = AsyncSocket(peerSocket)
  }

  @After
  fun tearDown() {
    peer.close()
    socket.source.close()
    try {
      socket.sink.close()
    } catch (_: IOException) {
      // Ignore exception if data was left in 'sink'.
    }
  }

  @Test
  fun happyPath() {
    val bufferedSource = socket.source.buffer()
    val bufferedSink = socket.sink.buffer()

    peer.write("one")
    assertThat(bufferedSource.readUtf8LineStrict()).isEqualTo("one")

    bufferedSink.writeUtf8("two\n")
    bufferedSink.flush()
    assertThat(peer.read()).isEqualTo("two")

    peer.write("three")
    assertThat(bufferedSource.readUtf8LineStrict()).isEqualTo("three")

    bufferedSink.writeUtf8("four\n")
    bufferedSink.flush()
    assertThat(peer.read()).isEqualTo("four")
  }

  @Test
  fun sourceIsReadableAfterSinkIsClosed() {
    peer.closeSource()
    socket.sink.close()

    peer.write("Hello")
    assertThat(socket.source.buffer().readUtf8Line()).isEqualTo("Hello")

    socket.source.close()
    peer.closeSink()
  }

  @Test
  fun sinkIsWritableAfterSourceIsClosed() {
    peer.closeSink()
    socket.source.close()

    val bufferedSink = socket.sink.buffer()
    bufferedSink.writeUtf8("Hello\n")
    bufferedSink.flush()
    assertThat(peer.read()).isEqualTo("Hello")

    socket.sink.close()
    peer.closeSource()
  }

  @Test
  fun localCancelCausesSubsequentReadToFail() {
    peer.write("Hello")

    socket.cancel()

    assertFailsWith<IOException> {
      socket.source.buffer().readUtf8Line()
    }
  }

  @Test
  fun localCancelCausesSubsequentWriteToFail() {
    socket.cancel()

    val bufferedSink = socket.sink.buffer()
    bufferedSink.writeUtf8("Hello\n")
    assertFailsWith<IOException> {
      bufferedSink.flush()
    }
  }

  @Test
  fun peerCloseCausesSubsequentLocalReadToFail() {
    peer.closeSink()

    val bufferedSource = socket.source.buffer()
    assertFailsWith<IOException> {
      bufferedSource.readUtf8LineStrict()
    }
  }

  @Test
  fun peerCancelCausesSubsequentLocalReadToFail() {
    peerSocket.cancel()

    val bufferedSource = socket.source.buffer()
    assertFailsWith<IOException> {
      bufferedSource.readUtf8LineStrict()
    }
  }

  @Test
  fun readTimeout() {
    val bufferedSource = socket.source.buffer()
    bufferedSource.timeout().timeout(500, TimeUnit.MILLISECONDS)

    val duration = measureTime {
      assertFailsWith<InterruptedIOException> {
        bufferedSource.readUtf8Line()
      }
    }

    assertThat(duration).isBetween(250.milliseconds, 750.milliseconds)
  }

  /** Make a large-enough write to saturate the outgoing write buffer. */
  @Test
  fun writeTimeout() {
    val bufferedSink = socket.sink.buffer()
    bufferedSink.timeout().timeout(500, TimeUnit.MILLISECONDS)

    val duration = measureTime {
      assertFailsWith<InterruptedIOException> {
        bufferedSink.write(ByteArray(1024 * 1024 * 16))
      }
    }

    assertThat(duration).isBetween(250.milliseconds, 750.milliseconds)
  }

  @Test
  fun closeSourceDoesNotCloseJavaNetSocket() {
    val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return

    socket.source.close()
    assertThat(javaNetSocket.isInputShutdown).isTrue()
    assertThat(javaNetSocket.isOutputShutdown).isFalse()
    assertThat(javaNetSocket.isClosed).isFalse()
  }

  @Test
  fun closeSinkDoesNotCloseJavaNetSocket() {
    val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return

    socket.sink.close()
    assertThat(javaNetSocket.isInputShutdown).isFalse()
    assertThat(javaNetSocket.isOutputShutdown).isTrue()
    assertThat(javaNetSocket.isClosed).isFalse()
  }

  @Test
  fun closeSourceThenSinkClosesJavaNetSocket() {
    val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return

    socket.source.close()
    socket.sink.close()
    assertThat(javaNetSocket.isClosed).isTrue()
  }

  @Test
  fun closeSinkThenSourceClosesJavaNetSocket() {
    val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return

    socket.sink.close()
    socket.source.close()
    assertThat(javaNetSocket.isClosed).isTrue()
  }

  @Test
  fun closeSinkThenSourceClosesJavaNetSocketEvenIfStreamsAlreadyClosed() {
    val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return
    javaNetSocket.shutdownInput()
    javaNetSocket.shutdownOutput()
    assertThat(javaNetSocket.isClosed).isFalse()

    socket.sink.close()
    socket.source.close()
    assertThat(javaNetSocket.isClosed).isTrue()
  }

  @Test
  fun closeSourceIsIdempotent() {
    val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return

    socket.source.close()
    assertThat(javaNetSocket.isInputShutdown).isTrue()
    assertThat(javaNetSocket.isClosed).isFalse()
    socket.source.close()
    assertThat(javaNetSocket.isInputShutdown).isTrue()
    assertThat(javaNetSocket.isClosed).isFalse()
  }

  @Test
  fun closeSinkIsIdempotent() {
    val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return

    socket.sink.close()
    assertThat(javaNetSocket.isOutputShutdown).isTrue()
    assertThat(javaNetSocket.isClosed).isFalse()
    socket.sink.close()
    assertThat(javaNetSocket.isOutputShutdown).isTrue()
    assertThat(javaNetSocket.isClosed).isFalse()
  }

  @Test
  fun cannotCreateOkioSocketFromClosedJavaNetSocket() {
    val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return
    javaNetSocket.close()

    assertFailsWith<SocketException> {
      javaNetSocket.asOkioSocket()
    }
  }

  @Test
  fun cannotCreateOkioSocketFromUnconnectedJavaNetSocket() {
    val unconnected = SocketFactory.getDefault().createSocket()
    assertFailsWith<SocketException> {
      unconnected.asOkioSocket()
    }
  }

  @Test
  fun cancelIsQuiet() {
    assumeTrue(factory == Factory.Default)

    val (socketA, socketB) = createSocketPairThatThrowsOnClose(IOException("boom!"))
    socketA.cancel()
    socketB.cancel()
  }

  @Test
  fun conscryptCrashIsQuiet() {
    assumeTrue(factory == Factory.Default)

    val (socketA, socketB) = createSocketPairThatThrowsOnClose(RuntimeException("bio == null"))
    socketA.cancel()
    socketB.cancel()
  }

  private fun createSocketPairThatThrowsOnClose(e: Throwable): Array<Socket> {
    val localhost = InetAddress.getByName("localhost")

    val serverSocket = ServerSocket()
    serverSocket.bind(InetSocketAddress(localhost, 0))

    val socketBFuture = CompletableFuture<java.net.Socket>()
    thread(name = "createSocketPair") {
      socketBFuture.complete(serverSocket.accept())
    }

    val socketA = object : java.net.Socket() {
      override fun close() {
        throw e
      }
    }
    socketA.connect(InetSocketAddress(localhost, serverSocket.localPort))

    val socketB = socketBFuture.get()
    return arrayOf(socketA.asOkioSocket(), socketB.asOkioSocket())
  }

  @Suppress("ktlint:trailing-comma-on-declaration-site")
  enum class Factory {
    /** Implements an okio.Socket using the `java.net.Socket` API on OS sockets. */
    Default {
      override fun createSocketPair(): Array<Socket> {
        val localhost = InetAddress.getByName("localhost")

        val serverSocket = ServerSocket()
        serverSocket.bind(InetSocketAddress(localhost, 0))

        val socketBFuture = CompletableFuture<java.net.Socket>()
        thread(name = "createSocketPair") {
          socketBFuture.complete(serverSocket.accept())
        }

        val socketA = SocketFactory.getDefault().createSocket()
        socketA.connect(InetSocketAddress(localhost, serverSocket.localPort))

        val socketB = socketBFuture.get()
        return arrayOf(socketA.asOkioSocket(), socketB.asOkioSocket())
      }
    },

    Pipes {
      override fun createSocketPair() = inMemorySocketPair(1024)
    };

    /** Returns two mutually-connected sockets. */
    abstract fun createSocketPair(): Array<Socket>
  }
}
