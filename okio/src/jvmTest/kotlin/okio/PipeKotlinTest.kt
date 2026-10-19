/*
 * Copyright (C) 2018 Square, Inc.
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

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

val PipeKotlinTest by testSuite(testConfig = TestConfig.withTestTimeout(5.seconds)) {
  val smallerTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(500L)
  val biggerTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(1500L)

  val smallerDeadlineNanos = TimeUnit.MILLISECONDS.toNanos(500L)
  val biggerDeadlineNanos = TimeUnit.MILLISECONDS.toNanos(1500L)

  fun assertDuration(expected: Long, block: () -> Unit) {
    val start = System.currentTimeMillis()
    block()
    val elapsed = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - start)

    assertEquals(
      expected.toDouble(),
      elapsed.toDouble(),
      TimeUnit.MILLISECONDS.toNanos(200).toDouble(),
    )
  }

  /** Writes on this sink never complete. They can only time out. */
  class TimeoutWritingSink : Sink {
    val timeout = object : AsyncTimeout() {
      override fun timedOut() {
        synchronized(this@TimeoutWritingSink) {
          (this@TimeoutWritingSink as Object).notifyAll()
        }
      }
    }

    override fun write(source: Buffer, byteCount: Long) {
      timeout.enter()
      try {
        synchronized(this) {
          (this as Object).wait()
        }
      } finally {
        timeout.exit()
      }
      source.skip(byteCount)
    }

    override fun flush() = Unit

    override fun close() = Unit

    override fun timeout() = timeout
  }

  /** Flushes on this sink never complete. They can only time out. */
  class TimeoutFlushingSink : Sink {
    val timeout = object : AsyncTimeout() {
      override fun timedOut() {
        synchronized(this@TimeoutFlushingSink) {
          (this@TimeoutFlushingSink as Object).notifyAll()
        }
      }
    }

    override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)

    override fun flush() {
      timeout.enter()
      try {
        synchronized(this) {
          (this as Object).wait()
        }
      } finally {
        timeout.exit()
      }
    }

    override fun close() = Unit

    override fun timeout() = timeout
  }

  /** Closes on this sink never complete. They can only time out. */
  class TimeoutClosingSink : Sink {
    val timeout = object : AsyncTimeout() {
      override fun timedOut() {
        synchronized(this@TimeoutClosingSink) {
          (this@TimeoutClosingSink as Object).notifyAll()
        }
      }
    }

    override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)

    override fun flush() = Unit

    override fun close() {
      timeout.enter()
      try {
        synchronized(this) {
          (this as Object).wait()
        }
      } finally {
        timeout.exit()
      }
    }

    override fun timeout() = timeout
  }

  testFixture {
    object {
      val executorService = TestExecutor(1)
    }
  } asContextForEach {

    test("pipe") {
      val pipe = Pipe(6)
      pipe.sink.write(Buffer().writeUtf8("abc"), 3L)

      val readBuffer = Buffer()
      assertEquals(3L, pipe.source.read(readBuffer, 6L))
      assertEquals("abc", readBuffer.readUtf8())

      pipe.sink.close()
      assertEquals(-1L, pipe.source.read(readBuffer, 6L))

      pipe.source.close()
    }

    test("fold") {
      val pipe = Pipe(128)

      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("hello")
      pipeSink.emit()

      val pipeSource = pipe.source.buffer()
      assertEquals("hello", pipeSource.readUtf8(5))

      val foldedSinkBuffer = Buffer()
      var foldedSinkClosed = false
      val foldedSink = object : ForwardingSink(foldedSinkBuffer) {
        override fun close() {
          foldedSinkClosed = true
          super.close()
        }
      }
      pipe.fold(foldedSink)

      pipeSink.writeUtf8("world")
      pipeSink.emit()
      assertEquals("world", foldedSinkBuffer.readUtf8(5))

      assertFailsWith<IllegalStateException> {
        pipeSource.readUtf8()
      }

      pipeSink.close()
      assertTrue(foldedSinkClosed)
    }

    test("foldWritesPipeContentsToSink") {
      val pipe = Pipe(128)

      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("hello")
      pipeSink.emit()

      val foldSink = Buffer()
      pipe.fold(foldSink)

      assertEquals("hello", foldSink.readUtf8(5))
    }

    test("foldUnblocksBlockedWrite") {
      val pipe = Pipe(4)
      val foldSink = Buffer()

      val latch = CountDownLatch(1)
      executorService.schedule(500.milliseconds) {
        pipe.fold(foldSink)
        latch.countDown()
      }

      val sink = pipe.sink.buffer()
      sink.writeUtf8("abcdefgh") // Blocks writing 8 bytes to a 4 byte pipe.
      sink.close()

      latch.await()
      assertEquals("abcdefgh", foldSink.readUtf8())
    }

    test("accessSourceAfterFold") {
      val pipe = Pipe(100L)
      pipe.fold(Buffer())
      assertFailsWith<IllegalStateException> {
        pipe.source.read(Buffer(), 1L)
      }
    }

    test("closeWhileFolding") {
      val pipe = Pipe(100L)
      val writing = CountDownLatch(1)
      val closed = CountDownLatch(1)
      val sinkBuffer = Buffer()
      val sinkClosed = AtomicBoolean()
      val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
      pipe.sink.write(Buffer().write(data), data.size.toLong())
      val foldResult = executorService.submit {
        val sink = object : Sink {
          override fun write(source: Buffer, byteCount: Long) {
            writing.countDown()
            closed.await()
            sinkBuffer.write(source, byteCount)
          }

          override fun flush() {
            sinkBuffer.flush()
          }

          override fun timeout(): Timeout {
            return sinkBuffer.timeout()
          }

          override fun close() {
            sinkBuffer.close()
            sinkClosed.set(true)
          }
        }
        pipe.fold(sink)
      }
      writing.await()
      pipe.sink.close()
      closed.countDown()
      foldResult.get()

      assertTrue(sinkClosed.get())
      assertArrayEquals(data, sinkBuffer.readByteArray())
    }

    test("honorsPipeSinkTimeoutOnWritingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutWritingSink()

      underlying.timeout.timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)
      pipe.sink.timeout().timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(smallerTimeoutNanos) {
        pipe.sink.write(Buffer().writeUtf8("abc"), 3)
      }
      assertEquals(biggerTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsUnderlyingTimeoutOnWritingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutWritingSink()

      underlying.timeout.timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)
      pipe.sink.timeout().timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(smallerTimeoutNanos) {
        pipe.sink.write(Buffer().writeUtf8("abc"), 3)
      }
      assertEquals(smallerTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsPipeSinkTimeoutOnFlushingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutFlushingSink()

      underlying.timeout.timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)
      pipe.sink.timeout().timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(smallerTimeoutNanos) {
        pipe.sink.flush()
      }
      assertEquals(biggerTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsUnderlyingTimeoutOnFlushingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutFlushingSink()

      underlying.timeout.timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)
      pipe.sink.timeout().timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(smallerTimeoutNanos) {
        pipe.sink.flush()
      }
      assertEquals(smallerTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsPipeSinkTimeoutOnClosingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutClosingSink()

      underlying.timeout.timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)
      pipe.sink.timeout().timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(smallerTimeoutNanos) {
        pipe.sink.close()
      }
      assertEquals(biggerTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsUnderlyingTimeoutOnClosingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutClosingSink()

      underlying.timeout.timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)
      pipe.sink.timeout().timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(smallerTimeoutNanos) {
        pipe.sink.close()
      }
      assertEquals(smallerTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsPipeSinkTimeoutOnWritingWhenUnderlyingSinkTimeoutIsZero") {
      val pipeSinkTimeoutNanos = smallerTimeoutNanos

      val pipe = Pipe(4)
      val underlying = TimeoutWritingSink()

      pipe.sink.timeout().timeout(pipeSinkTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(pipeSinkTimeoutNanos) {
        pipe.sink.write(Buffer().writeUtf8("abc"), 3)
      }
      assertEquals(0L, underlying.timeout().timeoutNanos())
    }

    test("honorsUnderlyingSinkTimeoutOnWritingWhenPipeSinkTimeoutIsZero") {
      val underlyingSinkTimeoutNanos = smallerTimeoutNanos

      val pipe = Pipe(4)
      val underlying = TimeoutWritingSink()

      underlying.timeout().timeout(underlyingSinkTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(underlyingSinkTimeoutNanos) {
        pipe.sink.write(Buffer().writeUtf8("abc"), 3)
      }
      assertEquals(underlyingSinkTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsPipeSinkTimeoutOnFlushingWhenUnderlyingSinkTimeoutIsZero") {
      val pipeSinkTimeoutNanos = smallerTimeoutNanos

      val pipe = Pipe(4)
      val underlying = TimeoutFlushingSink()

      pipe.sink.timeout().timeout(pipeSinkTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(pipeSinkTimeoutNanos) {
        pipe.sink.flush()
      }
      assertEquals(0L, underlying.timeout().timeoutNanos())
    }

    test("honorsUnderlyingSinkTimeoutOnFlushingWhenPipeSinkTimeoutIsZero") {
      val underlyingSinkTimeoutNanos = smallerTimeoutNanos

      val pipe = Pipe(4)
      val underlying = TimeoutFlushingSink()

      underlying.timeout().timeout(underlyingSinkTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(underlyingSinkTimeoutNanos) {
        pipe.sink.flush()
      }
      assertEquals(underlyingSinkTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsPipeSinkTimeoutOnClosingWhenUnderlyingSinkTimeoutIsZero") {
      val pipeSinkTimeoutNanos = smallerTimeoutNanos

      val pipe = Pipe(4)
      val underlying = TimeoutClosingSink()

      pipe.sink.timeout().timeout(pipeSinkTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(pipeSinkTimeoutNanos) {
        pipe.sink.close()
      }
      assertEquals(0L, underlying.timeout().timeoutNanos())
    }

    test("honorsUnderlyingSinkTimeoutOnClosingWhenPipeSinkTimeoutIsZero") {
      val underlyingSinkTimeoutNanos = smallerTimeoutNanos

      val pipe = Pipe(4)
      val underlying = TimeoutClosingSink()

      underlying.timeout().timeout(underlyingSinkTimeoutNanos, TimeUnit.NANOSECONDS)

      pipe.fold(underlying)

      assertDuration(underlyingSinkTimeoutNanos) {
        pipe.sink.close()
      }
      assertEquals(underlyingSinkTimeoutNanos, underlying.timeout().timeoutNanos())
    }

    test("honorsPipeSinkDeadlineOnWritingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutWritingSink()

      val underlyingOriginalDeadline = System.nanoTime() + biggerDeadlineNanos
      underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + smallerDeadlineNanos)

      pipe.fold(underlying)

      assertDuration(smallerDeadlineNanos) {
        pipe.sink.write(Buffer().writeUtf8("abc"), 3)
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("honorsPipeSinkDeadlineOnWritingWhenUnderlyingSinkHasNoDeadline") {
      val deadlineNanos = smallerDeadlineNanos

      val pipe = Pipe(4)
      val underlying = TimeoutWritingSink()

      underlying.timeout.clearDeadline()
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + deadlineNanos)

      pipe.fold(underlying)

      assertDuration(deadlineNanos) {
        pipe.sink.write(Buffer().writeUtf8("abc"), 3)
      }
      assertFalse(underlying.timeout().hasDeadline())
    }

    test("honorsUnderlyingSinkDeadlineOnWritingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutWritingSink()

      val underlyingOriginalDeadline = System.nanoTime() + smallerDeadlineNanos
      underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + biggerDeadlineNanos)

      pipe.fold(underlying)

      assertDuration(smallerDeadlineNanos) {
        pipe.sink.write(Buffer().writeUtf8("abc"), 3)
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("honorsUnderlyingSinkDeadlineOnWritingWhenPipeSinkHasNoDeadline") {
      val deadlineNanos = smallerDeadlineNanos

      val pipe = Pipe(4)
      val underlying = TimeoutWritingSink()

      val underlyingOriginalDeadline = System.nanoTime() + deadlineNanos
      underlying.timeout().deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().clearDeadline()

      pipe.fold(underlying)

      assertDuration(deadlineNanos) {
        pipe.sink.write(Buffer().writeUtf8("abc"), 3)
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("honorsPipeSinkDeadlineOnFlushingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutFlushingSink()

      val underlyingOriginalDeadline = System.nanoTime() + biggerDeadlineNanos
      underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + smallerDeadlineNanos)

      pipe.fold(underlying)

      assertDuration(smallerDeadlineNanos) {
        pipe.sink.flush()
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("honorsPipeSinkDeadlineOnFlushingWhenUnderlyingSinkHasNoDeadline") {
      val deadlineNanos = smallerDeadlineNanos

      val pipe = Pipe(4)
      val underlying = TimeoutFlushingSink()

      underlying.timeout.clearDeadline()
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + deadlineNanos)

      pipe.fold(underlying)

      assertDuration(deadlineNanos) {
        pipe.sink.flush()
      }
      assertFalse(underlying.timeout().hasDeadline())
    }

    test("honorsUnderlyingSinkDeadlineOnFlushingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutFlushingSink()

      val underlyingOriginalDeadline = System.nanoTime() + smallerDeadlineNanos
      underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + biggerDeadlineNanos)

      pipe.fold(underlying)

      assertDuration(smallerDeadlineNanos) {
        pipe.sink.flush()
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("honorsUnderlyingSinkDeadlineOnFlushingWhenPipeSinkHasNoDeadline") {
      val deadlineNanos = smallerDeadlineNanos

      val pipe = Pipe(4)
      val underlying = TimeoutFlushingSink()

      val underlyingOriginalDeadline = System.nanoTime() + deadlineNanos
      underlying.timeout().deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().clearDeadline()

      pipe.fold(underlying)

      assertDuration(deadlineNanos) {
        pipe.sink.flush()
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("honorsPipeSinkDeadlineOnClosingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutClosingSink()

      val underlyingOriginalDeadline = System.nanoTime() + biggerDeadlineNanos
      underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + smallerDeadlineNanos)

      pipe.fold(underlying)

      assertDuration(smallerDeadlineNanos) {
        pipe.sink.close()
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("honorsPipeSinkDeadlineOnClosingWhenUnderlyingSinkHasNoDeadline") {
      val deadlineNanos = smallerDeadlineNanos

      val pipe = Pipe(4)
      val underlying = TimeoutClosingSink()

      underlying.timeout.clearDeadline()
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + deadlineNanos)

      pipe.fold(underlying)

      assertDuration(deadlineNanos) {
        pipe.sink.close()
      }
      assertFalse(underlying.timeout().hasDeadline())
    }

    test("honorsUnderlyingSinkDeadlineOnClosingWhenItIsSmaller") {
      val pipe = Pipe(4)
      val underlying = TimeoutClosingSink()

      val underlyingOriginalDeadline = System.nanoTime() + smallerDeadlineNanos
      underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + biggerDeadlineNanos)

      pipe.fold(underlying)

      assertDuration(smallerDeadlineNanos) {
        pipe.sink.close()
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("honorsUnderlyingSinkDeadlineOnClosingWhenPipeSinkHasNoDeadline") {
      val deadlineNanos = smallerDeadlineNanos

      val pipe = Pipe(4)
      val underlying = TimeoutClosingSink()

      val underlyingOriginalDeadline = System.nanoTime() + deadlineNanos
      underlying.timeout().deadlineNanoTime(underlyingOriginalDeadline)
      pipe.sink.timeout().clearDeadline()

      pipe.fold(underlying)

      assertDuration(deadlineNanos) {
        pipe.sink.close()
      }
      assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
    }

    test("foldingTwiceThrows") {
      val pipe = Pipe(128)
      pipe.fold(Buffer())
      assertFailsWith<IllegalStateException> {
        pipe.fold(Buffer())
      }
    }

    test("sinkWriteThrowsIOExceptionUnblockBlockedWriter") {
      val pipe = Pipe(4)

      val foldFuture = executorService.schedule(500.milliseconds) {
        val foldFailure = assertFailsWith<IOException> {
          pipe.fold(
            object : ForwardingSink(blackholeSink()) {
              override fun write(source: Buffer, byteCount: Long) {
                throw IOException("boom")
              }
            },
          )
        }
        assertEquals("boom", foldFailure.message)
      }

      val writeFailure = assertFailsWith<IOException> {
        val pipeSink = pipe.sink.buffer()
        pipeSink.writeUtf8("abcdefghij")
        pipeSink.emit() // Block writing 10 bytes to a 4 byte pipe.
      }
      assertEquals("source is closed", writeFailure.message)

      foldFuture.get() // Confirm no unexpected exceptions.
    }

    test("foldHoldsNoLocksWhenForwardingWrites") {
      val pipe = Pipe(4)

      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("abcd")
      pipeSink.emit()

      pipe.fold(
        object : ForwardingSink(blackholeSink()) {
          override fun write(source: Buffer, byteCount: Long) {
            assertFalse(Thread.holdsLock(pipe.buffer))
          }
        },
      )
    }

    /**
     * Flushing the pipe wasn't causing the sink to be flushed when it was later folded. This was
     * causing problems because the folded data was stalled.
     */
    test("foldFlushesWhenThereIsFoldedData") {
      val pipe = Pipe(128)
      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("hello")
      pipeSink.emit()

      val ultimateSink = Buffer()
      val unnecessaryWrapper = (ultimateSink as Sink).buffer()

      pipe.fold(unnecessaryWrapper)

      // Data should not have been flushed through the wrapper to the ultimate sink.
      assertEquals("hello", ultimateSink.readUtf8())
    }

    test("foldDoesNotFlushWhenThereIsNoFoldedData") {
      val pipe = Pipe(128)

      val ultimateSink = Buffer()
      val unnecessaryWrapper = (ultimateSink as Sink).buffer()
      unnecessaryWrapper.writeUtf8("hello")

      pipe.fold(unnecessaryWrapper)

      // Data should not have been flushed through the wrapper to the ultimate sink.
      assertEquals("", ultimateSink.readUtf8())
    }

    test("foldingClosesUnderlyingSinkWhenPipeSinkIsClose") {
      val pipe = Pipe(128)

      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("world")
      pipeSink.close()

      val foldedSinkBuffer = Buffer()
      var foldedSinkClosed = false
      val foldedSink = object : ForwardingSink(foldedSinkBuffer) {
        override fun close() {
          foldedSinkClosed = true
          super.close()
        }
      }

      pipe.fold(foldedSink)
      assertEquals("world", foldedSinkBuffer.readUtf8(5))
      assertTrue(foldedSinkClosed)
    }

    test("cancelPreventsSinkWrite") {
      val pipe = Pipe(8)
      pipe.cancel()

      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("hello world")

      try {
        pipeSink.emit()
        fail()
      } catch (e: IOException) {
        assertEquals("canceled", e.message)
      }
    }

    test("cancelPreventsSinkFlush") {
      val pipe = Pipe(8)
      pipe.cancel()

      try {
        pipe.sink.flush()
        fail()
      } catch (e: IOException) {
        assertEquals("canceled", e.message)
      }
    }

    test("sinkCloseAfterCancelDoesNotThrow") {
      val pipe = Pipe(8)
      pipe.cancel()
      pipe.sink.close()
    }

    test("cancelInterruptsSinkWrite") {
      val pipe = Pipe(8)

      executorService.schedule(smallerTimeoutNanos.nanoseconds) {
        pipe.cancel()
      }

      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("hello world")

      assertDuration(smallerTimeoutNanos) {
        try {
          pipeSink.emit()
          fail()
        } catch (e: IOException) {
          assertEquals("canceled", e.message)
        }
      }
    }

    test("cancelPreventsSourceRead") {
      val pipe = Pipe(8)
      pipe.cancel()

      val pipeSource = pipe.source.buffer()

      try {
        pipeSource.require(1)
        fail()
      } catch (e: IOException) {
        assertEquals("canceled", e.message)
      }
    }

    test("sourceCloseAfterCancelDoesNotThrow") {
      val pipe = Pipe(8)
      pipe.cancel()
      pipe.source.close()
    }

    test("cancelInterruptsSourceRead") {
      val pipe = Pipe(8)

      executorService.schedule(smallerTimeoutNanos.nanoseconds) {
        pipe.cancel()
      }

      val pipeSource = pipe.source.buffer()

      assertDuration(smallerTimeoutNanos) {
        try {
          pipeSource.require(1)
          fail()
        } catch (e: IOException) {
          assertEquals("canceled", e.message)
        }
      }
    }

    test("cancelPreventsSinkFold") {
      val pipe = Pipe(8)
      pipe.cancel()

      var foldedSinkClosed = false
      val foldedSink = object : ForwardingSink(Buffer()) {
        override fun close() {
          foldedSinkClosed = true
          super.close()
        }
      }

      try {
        pipe.fold(foldedSink)
        fail()
      } catch (e: IOException) {
        assertEquals("canceled", e.message)
      }

      // But the fold is still performed so close() closes everything.
      assertFalse(foldedSinkClosed)
      pipe.sink.close()
      assertTrue(foldedSinkClosed)
    }

    test("cancelInterruptsSinkFold") {
      val pipe = Pipe(128)
      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("hello")
      pipeSink.emit()

      var foldedSinkClosed = false
      val foldedSink = object : ForwardingSink(Buffer()) {
        override fun write(source: Buffer, byteCount: Long) {
          assertEquals("hello", source.readUtf8(byteCount))

          // Write bytes to the original pipe so the pipe write doesn't complete!
          pipeSink.writeUtf8("more bytes")
          pipeSink.emit()

          // Cancel while the pipe is writing.
          pipe.cancel()
        }

        override fun close() {
          foldedSinkClosed = true
          super.close()
        }
      }

      try {
        pipe.fold(foldedSink)
        fail()
      } catch (e: IOException) {
        assertEquals("canceled", e.message)
      }

      // But the fold is still performed so close() closes everything.
      assertFalse(foldedSinkClosed)
      pipe.sink.close()
      assertTrue(foldedSinkClosed)
    }
  }
}
