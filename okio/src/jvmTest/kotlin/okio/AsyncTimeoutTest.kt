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
import java.io.InterruptedIOException
import java.util.Random
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import okio.ByteString.Companion.of
import okio.TestUtil.bufferWithRandomSegmentLayout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * This test uses four timeouts of varying durations: 250ms, 500ms, 750ms and
 * 1000ms, named 'a', 'b', 'c' and 'd'.
 */
class AsyncTimeoutTest {
  private val timedOut = LinkedBlockingDeque<AsyncTimeout>()
  private val a = RecordingAsyncTimeout()
  private val b = RecordingAsyncTimeout()
  private val c = RecordingAsyncTimeout()
  private val d = RecordingAsyncTimeout()

  @BeforeEach
  fun setUp() {
    a.timeout(250, TimeUnit.MILLISECONDS)
    b.timeout(500, TimeUnit.MILLISECONDS)
    c.timeout(750, TimeUnit.MILLISECONDS)
    d.timeout(1000, TimeUnit.MILLISECONDS)
  }

  @Test
  fun zeroTimeoutIsNoTimeout() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(0, TimeUnit.MILLISECONDS)
    timeout.enter()
    Thread.sleep(250)
    assertFalse(timeout.exit())
    assertTimedOut()
  }

  @Test
  fun singleInstanceTimedOut() {
    a.enter()
    Thread.sleep(500)
    assertTrue(a.exit())
    assertTimedOut(a)
  }

  @Test
  fun singleInstanceNotTimedOut() {
    b.enter()
    Thread.sleep(250)
    b.exit()
    assertFalse(b.exit())
    assertTimedOut()
  }

  @Test
  fun instancesAddedAtEnd() {
    a.enter()
    b.enter()
    c.enter()
    d.enter()
    Thread.sleep(1250)
    assertTrue(a.exit())
    assertTrue(b.exit())
    assertTrue(c.exit())
    assertTrue(d.exit())
    assertTimedOut(a, b, c, d)
  }

  @Test
  fun instancesAddedAtFront() {
    d.enter()
    c.enter()
    b.enter()
    a.enter()
    Thread.sleep(1250)
    assertTrue(d.exit())
    assertTrue(c.exit())
    assertTrue(b.exit())
    assertTrue(a.exit())
    assertTimedOut(a, b, c, d)
  }

  @Test
  fun instancesRemovedAtFront() {
    a.enter()
    b.enter()
    c.enter()
    d.enter()
    assertFalse(a.exit())
    assertFalse(b.exit())
    assertFalse(c.exit())
    assertFalse(d.exit())
    assertTimedOut()
  }

  @Test
  fun instancesRemovedAtEnd() {
    a.enter()
    b.enter()
    c.enter()
    d.enter()
    assertFalse(d.exit())
    assertFalse(c.exit())
    assertFalse(b.exit())
    assertFalse(a.exit())
    assertTimedOut()
  }

  @Test
  fun doubleEnter() {
    a.enter()
    try {
      a.enter()
      fail()
    } catch (expected: IllegalStateException) {
    }
  }

  @Test
  fun reEnter() {
    a.timeout(10, TimeUnit.SECONDS)
    a.enter()
    assertFalse(a.exit())
    a.enter()
    assertFalse(a.exit())
  }

  @Test
  fun reEnterAfterTimeout() {
    a.timeout(1, TimeUnit.MILLISECONDS)
    a.enter()
    Assertions.assertSame(a, timedOut.take())
    assertTrue(a.exit())
    a.enter()
    assertFalse(a.exit())
  }

  @Test
  fun deadlineOnly() {
    val timeout = RecordingAsyncTimeout()
    timeout.deadline(250, TimeUnit.MILLISECONDS)
    timeout.enter()
    Thread.sleep(500)
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  @Test
  fun deadlineBeforeTimeout() {
    val timeout = RecordingAsyncTimeout()
    timeout.deadline(250, TimeUnit.MILLISECONDS)
    timeout.timeout(750, TimeUnit.MILLISECONDS)
    timeout.enter()
    Thread.sleep(500)
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  @Test
  fun deadlineAfterTimeout() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    timeout.deadline(750, TimeUnit.MILLISECONDS)
    timeout.enter()
    Thread.sleep(500)
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  @Test
  fun deadlineStartsBeforeEnter() {
    val timeout = RecordingAsyncTimeout()
    timeout.deadline(500, TimeUnit.MILLISECONDS)
    Thread.sleep(500)
    timeout.enter()
    Thread.sleep(250)
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  @Test
  fun deadlineInThePast() {
    val timeout = RecordingAsyncTimeout()
    timeout.deadlineNanoTime(System.nanoTime() - 1)
    timeout.enter()
    Thread.sleep(250)
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  @Test
  fun wrappedSinkTimesOut() {
    val sink: Sink = object : ForwardingSink(Buffer()) {
      override fun write(source: Buffer, byteCount: Long) {
        Thread.sleep(500)
      }
    }
    val timeout = AsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    val timeoutSink = timeout.sink(sink)
    val data = Buffer().writeUtf8("a")
    try {
      timeoutSink.write(data, 1)
      fail()
    } catch (expected: InterruptedIOException) {
    }
  }

  @Test
  fun wrappedSinkFlushTimesOut() {
    val sink: Sink = object : ForwardingSink(Buffer()) {
      override fun flush() {
        Thread.sleep(500)
      }
    }
    val timeout = AsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    val timeoutSink = timeout.sink(sink)
    try {
      timeoutSink.flush()
      fail()
    } catch (expected: InterruptedIOException) {
    }
  }

  @Test
  fun wrappedSinkCloseTimesOut() {
    val sink: Sink = object : ForwardingSink(Buffer()) {
      override fun close() {
        Thread.sleep(500)
      }
    }
    val timeout = AsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    val timeoutSink = timeout.sink(sink)
    try {
      timeoutSink.close()
      fail()
    } catch (expected: InterruptedIOException) {
    }
  }

  @Test
  fun wrappedSourceTimesOut() {
    val source: Source = object : ForwardingSource(Buffer()) {
      override fun read(sink: Buffer, byteCount: Long): Long {
        Thread.sleep(500)
        return -1
      }
    }
    val timeout = AsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    val timeoutSource = timeout.source(source)
    try {
      timeoutSource.read(Buffer(), 0)
      fail()
    } catch (expected: InterruptedIOException) {
    }
  }

  @Test
  fun wrappedSourceCloseTimesOut() {
    val source: Source = object : ForwardingSource(Buffer()) {
      override fun close() {
        Thread.sleep(500)
      }
    }
    val timeout = AsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    val timeoutSource = timeout.source(source)
    try {
      timeoutSource.close()
      fail()
    } catch (expected: InterruptedIOException) {
    }
  }

  @Test
  fun wrappedThrowsWithTimeout() {
    val sink: Sink = object : ForwardingSink(Buffer()) {
      override fun write(source: Buffer, byteCount: Long) {
        Thread.sleep(500)
        throw IOException("exception and timeout")
      }
    }
    val timeout = AsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    val timeoutSink = timeout.sink(sink)
    val data = Buffer().writeUtf8("a")
    try {
      timeoutSink.write(data, 1)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
      assertEquals("exception and timeout", expected.cause!!.message)
    }
  }

  @Test
  fun wrappedThrowsWithoutTimeout() {
    val sink: Sink = object : ForwardingSink(Buffer()) {
      override fun write(source: Buffer, byteCount: Long) {
        throw IOException("no timeout occurred")
      }
    }
    val timeout = AsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    val timeoutSink = timeout.sink(sink)
    val data = Buffer().writeUtf8("a")
    try {
      timeoutSink.write(data, 1)
      fail()
    } catch (expected: IOException) {
      assertEquals("no timeout occurred", expected.message)
    }
  }

  /**
   * We had a bug where writing a very large buffer would fail with an
   * unexpected timeout because although the sink was making steady forward
   * progress, doing it all as a single write caused a timeout.
   */
  @Disabled("Flaky")
  @Test
  fun sinkSplitsLargeWrites() {
    val data = ByteArray(512 * 1024)
    val dice = Random(0)
    dice.nextBytes(data)
    val source = bufferWithRandomSegmentLayout(dice, data)
    val target = Buffer()
    val sink: Sink = object : ForwardingSink(Buffer()) {
      override fun write(source: Buffer, byteCount: Long) {
        Thread.sleep(byteCount / 500) // ~500 KiB/s.
        target.write(source, byteCount)
      }
    }

    // Timeout after 250 ms of inactivity.
    val timeout = AsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    val timeoutSink = timeout.sink(sink)

    // Transmit 500 KiB of data, which should take ~1 second. But expect no timeout!
    timeoutSink.write(source, source.size)

    // The data should all have arrived.
    assertEquals(of(*data), target.readByteString())
  }

  @Test
  fun enterCancelSleepExit() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    timeout.enter()
    timeout.cancel()
    Thread.sleep(500)

    // Call didn't time out because the timeout was canceled.
    assertFalse(timeout.exit())
    assertTimedOut()
  }

  @Test
  fun enterCancelCancelSleepExit() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    timeout.enter()
    timeout.cancel()
    timeout.cancel()
    Thread.sleep(500)

    // Call didn't time out because the timeout was canceled.
    assertFalse(timeout.exit())
    assertTimedOut()
  }

  @Test
  fun enterSleepCancelExit() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    timeout.enter()
    Thread.sleep(500)
    timeout.cancel()

    // Call timed out because the cancel was too late.
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  @Test
  fun enterSleepCancelCancelExit() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    timeout.enter()
    Thread.sleep(500)
    timeout.cancel()
    timeout.cancel()

    // Call timed out because both cancels were too late.
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  @Test
  fun enterCancelSleepCancelExit() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    timeout.enter()
    timeout.cancel()
    Thread.sleep(500)
    timeout.cancel()

    // Call didn't time out because the timeout was canceled.
    assertFalse(timeout.exit())
    assertTimedOut()
  }

  @Test
  fun cancelEnterSleepExit() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    timeout.cancel()
    timeout.enter()
    Thread.sleep(500)

    // Call timed out because the cancel was too early.
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  @Test
  fun cancelEnterCancelSleepExit() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)
    timeout.cancel()
    timeout.enter()
    timeout.cancel()
    Thread.sleep(500)

    // Call didn't time out because the timeout was canceled.
    assertFalse(timeout.exit())
    assertTimedOut()
  }

  @Test
  fun enterCancelSleepExitEnterSleepExit() {
    val timeout = RecordingAsyncTimeout()
    timeout.timeout(250, TimeUnit.MILLISECONDS)

    // First call doesn't time out because we cancel it.
    timeout.enter()
    timeout.cancel()
    Thread.sleep(500)
    assertFalse(timeout.exit())
    assertTimedOut()

    // Second call does time out because it isn't canceled a second time.
    timeout.enter()
    Thread.sleep(500)
    assertTrue(timeout.exit())
    assertTimedOut(timeout)
  }

  /** Asserts which timeouts fired, and in which order.  */
  private fun assertTimedOut(vararg expected: Timeout) {
    assertEquals(expected.toList(), timedOut.toList())
    timedOut.clear()
  }

  internal inner class RecordingAsyncTimeout : AsyncTimeout() {
    override fun timedOut() {
      timedOut.add(this)
    }
  }
}
