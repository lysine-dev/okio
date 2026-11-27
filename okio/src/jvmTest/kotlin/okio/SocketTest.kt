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

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import de.infix.testBalloon.framework.core.testSuite
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

val SocketTest by testSuite {
  for (factory in SocketTestFactory.entries) {
    testSuite(factory.name) {
      testFixture { SocketFixture(factory) } asContextForEach {
        test("happyPath") {
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

        test("sourceIsReadableAfterSinkIsClosed") {
          peer.closeSource()
          socket.sink.close()

          peer.write("Hello")
          assertThat(socket.source.buffer().readUtf8Line()).isEqualTo("Hello")

          socket.source.close()
          peer.closeSink()
        }

        test("sinkIsWritableAfterSourceIsClosed") {
          peer.closeSink()
          socket.source.close()

          val bufferedSink = socket.sink.buffer()
          bufferedSink.writeUtf8("Hello\n")
          bufferedSink.flush()
          assertThat(peer.read()).isEqualTo("Hello")

          socket.sink.close()
          peer.closeSource()
        }

        test("localCancelCausesSubsequentReadToFail") {
          peer.write("Hello")

          socket.cancel()

          assertFailsWith<IOException> {
            socket.source.buffer().readUtf8Line()
          }
        }

        test("localCancelCausesSubsequentWriteToFail") {
          socket.cancel()

          val bufferedSink = socket.sink.buffer()
          bufferedSink.writeUtf8("Hello\n")
          assertFailsWith<IOException> {
            bufferedSink.flush()
          }
        }

        test("peerCloseCausesSubsequentLocalReadToFail") {
          peer.closeSink()

          val bufferedSource = socket.source.buffer()
          assertFailsWith<IOException> {
            bufferedSource.readUtf8LineStrict()
          }
        }

        test("peerCancelCausesSubsequentLocalReadToFail") {
          peerSocket.cancel()

          val bufferedSource = socket.source.buffer()
          assertFailsWith<IOException> {
            bufferedSource.readUtf8LineStrict()
          }
        }

        test("readTimeout") {
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
        test("writeTimeout") {
          val bufferedSink = socket.sink.buffer()
          bufferedSink.timeout().timeout(500, TimeUnit.MILLISECONDS)

          val duration = measureTime {
            assertFailsWith<InterruptedIOException> {
              bufferedSink.write(ByteArray(1024 * 1024 * 16))
            }
          }

          assertThat(duration).isBetween(250.milliseconds, 750.milliseconds)
        }

        test("closeSourceDoesNotCloseJavaNetSocket") {
          val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return@test

          socket.source.close()
          assertThat(javaNetSocket.isInputShutdown).isTrue()
          assertThat(javaNetSocket.isOutputShutdown).isFalse()
          assertThat(javaNetSocket.isClosed).isFalse()
        }

        test("closeSinkDoesNotCloseJavaNetSocket") {
          val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return@test

          socket.sink.close()
          assertThat(javaNetSocket.isInputShutdown).isFalse()
          assertThat(javaNetSocket.isOutputShutdown).isTrue()
          assertThat(javaNetSocket.isClosed).isFalse()
        }

        test("closeSourceThenSinkClosesJavaNetSocket") {
          val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return@test

          socket.source.close()
          socket.sink.close()
          assertThat(javaNetSocket.isClosed).isTrue()
        }

        test("closeSinkThenSourceClosesJavaNetSocket") {
          val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return@test

          socket.sink.close()
          socket.source.close()
          assertThat(javaNetSocket.isClosed).isTrue()
        }

        test("closeSinkThenSourceClosesJavaNetSocketEvenIfStreamsAlreadyClosed") {
          val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return@test
          javaNetSocket.shutdownInput()
          javaNetSocket.shutdownOutput()
          assertThat(javaNetSocket.isClosed).isFalse()

          socket.sink.close()
          socket.source.close()
          assertThat(javaNetSocket.isClosed).isTrue()
        }

        test("closeSourceIsIdempotent") {
          val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return@test

          socket.source.close()
          assertThat(javaNetSocket.isInputShutdown).isTrue()
          assertThat(javaNetSocket.isClosed).isFalse()
          socket.source.close()
          assertThat(javaNetSocket.isInputShutdown).isTrue()
          assertThat(javaNetSocket.isClosed).isFalse()
        }

        test("closeSinkIsIdempotent") {
          val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return@test

          socket.sink.close()
          assertThat(javaNetSocket.isOutputShutdown).isTrue()
          assertThat(javaNetSocket.isClosed).isFalse()
          socket.sink.close()
          assertThat(javaNetSocket.isOutputShutdown).isTrue()
          assertThat(javaNetSocket.isClosed).isFalse()
        }

        test("cannotCreateOkioSocketFromClosedJavaNetSocket") {
          val javaNetSocket = (this.socket as? DefaultSocket)?.socket ?: return@test
          javaNetSocket.close()

          assertFailsWith<SocketException> {
            javaNetSocket.asOkioSocket()
          }
        }

        test("cannotCreateOkioSocketFromUnconnectedJavaNetSocket") {
          val unconnected = SocketFactory.getDefault().createSocket()
          assertFailsWith<SocketException> {
            unconnected.asOkioSocket()
          }
        }

        test("cancelIsQuiet") {
          if (factory != SocketTestFactory.Default) return@test

          val (socketA, socketB) = createSocketPairThatThrowsOnClose(IOException("boom!"))
          socketA.cancel()
          socketB.cancel()
        }

        test("conscryptCrashIsQuiet") {
          if (factory != SocketTestFactory.Default) return@test

          val (socketA, socketB) = createSocketPairThatThrowsOnClose(RuntimeException("bio == null"))
          socketA.cancel()
          socketB.cancel()
        }
      }
    }
  }
}

private class SocketFixture(factory: SocketTestFactory) : AutoCloseable {
  val socket: Socket
  val peerSocket: Socket
  val peer: AsyncSocket

  init {
    val pair = factory.createSocketPair()
    socket = pair[0]
    peerSocket = pair[1]
    peer = AsyncSocket(peerSocket)
  }

  fun createSocketPairThatThrowsOnClose(e: Throwable): Array<Socket> {
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

  override fun close() {
    peer.close()
    socket.source.close()
    runCatching { socket.sink.close() } // Ignore exception if data was left in 'sink'.
  }
}

@Suppress("ktlint:trailing-comma-on-declaration-site")
enum class SocketTestFactory {
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
