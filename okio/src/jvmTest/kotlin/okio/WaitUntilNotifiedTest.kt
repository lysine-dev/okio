/*
 * Copyright (C) 2016 Square, Inc.
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

import de.infix.testBalloon.framework.core.testSuite
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import okio.TestUtil.isWindows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

val WaitUntilNotifiedTest by testSuite {
  for (factory in TimeoutFactory.entries) {
    testSuite(factory.name) {
      testFixture { WaitUntilNotifiedFixture(factory) } asContextForEach {

        test("notified") {
          synchronized(this) {
            timeout.timeout(5000, TimeUnit.MILLISECONDS)
            val start = now()
            testExecutor.schedule(1000.milliseconds) {
              synchronized(this) {
                (this as Object).notify()
              }
            }
            timeout.waitUntilNotified(this)
            assertElapsed(1000.0, start)
          }
        }

        test("timeout") {
          if (isWindows()) return@test
          synchronized(this) {
            timeout.timeout(1000, TimeUnit.MILLISECONDS)
            val start = now()
            try {
              timeout.waitUntilNotified(this)
              fail()
            } catch (expected: InterruptedIOException) {
              assertEquals("timeout", expected.message)
            }
            assertElapsed(1000.0, start)
          }
        }

        test("deadline") {
          if (isWindows()) return@test
          synchronized(this) {
            timeout.deadline(1000, TimeUnit.MILLISECONDS)
            val start = now()
            try {
              timeout.waitUntilNotified(this)
              fail()
            } catch (expected: InterruptedIOException) {
              assertEquals("timeout", expected.message)
            }
            assertElapsed(1000.0, start)
          }
        }

        test("deadlineBeforeTimeout") {
          if (isWindows()) return@test
          synchronized(this) {
            timeout.timeout(5000, TimeUnit.MILLISECONDS)
            timeout.deadline(1000, TimeUnit.MILLISECONDS)
            val start = now()
            try {
              timeout.waitUntilNotified(this)
              fail()
            } catch (expected: InterruptedIOException) {
              assertEquals("timeout", expected.message)
            }
            assertElapsed(1000.0, start)
          }
        }

        test("timeoutBeforeDeadline") {
          if (isWindows()) return@test
          synchronized(this) {
            timeout.timeout(1000, TimeUnit.MILLISECONDS)
            timeout.deadline(5000, TimeUnit.MILLISECONDS)
            val start = now()
            try {
              timeout.waitUntilNotified(this)
              fail()
            } catch (expected: InterruptedIOException) {
              assertEquals("timeout", expected.message)
            }
            assertElapsed(1000.0, start)
          }
        }

        test("deadlineAlreadyReached") {
          if (isWindows()) return@test
          synchronized(this) {
            timeout.deadlineNanoTime(System.nanoTime())
            val start = now()
            try {
              timeout.waitUntilNotified(this)
              fail()
            } catch (expected: InterruptedIOException) {
              assertEquals("timeout", expected.message)
            }
            assertElapsed(0.0, start)
          }
        }

        test("threadInterrupted") {
          if (isWindows()) return@test
          synchronized(this) {
            val start = now()
            Thread.currentThread().interrupt()
            try {
              timeout.waitUntilNotified(this)
              fail()
            } catch (expected: InterruptedIOException) {
              assertEquals("interrupted", expected.message)
              assertTrue(Thread.interrupted())
            }
            assertElapsed(0.0, start)
          }
        }

        test("threadInterruptedOnThrowIfReached") {
          if (isWindows()) return@test
          synchronized(this) {
            Thread.currentThread().interrupt()
            try {
              timeout.throwIfReached()
              fail()
            } catch (expected: InterruptedIOException) {
              assertEquals("interrupted", expected.message)
              assertTrue(Thread.interrupted())
            }
          }
        }

        test("cancelBeforeWaitDoesNothing") {
          if (isWindows()) return@test
          synchronized(this) {
            timeout.timeout(1000, TimeUnit.MILLISECONDS)
            timeout.cancel()
            val start = now()
            try {
              timeout.waitUntilNotified(this)
              fail()
            } catch (expected: InterruptedIOException) {
              assertEquals("timeout", expected.message)
            }
            assertElapsed(1000.0, start)
          }
        }

        test("canceledTimeoutDoesNotThrowWhenNotNotifiedOnTime") {
          synchronized(this) {
            timeout.timeout(1000, TimeUnit.MILLISECONDS)
            timeout.cancelLater(500)

            val start = now()
            timeout.waitUntilNotified(this) // Returns early but doesn't throw.
            assertElapsed(1000.0, start)
          }
        }

        test("multipleCancelsAreIdempotent") {
          synchronized(this) {
            timeout.timeout(1000, TimeUnit.MILLISECONDS)
            timeout.cancelLater(250)
            timeout.cancelLater(500)
            timeout.cancelLater(750)

            val start = now()
            timeout.waitUntilNotified(this) // Returns early but doesn't throw.
            assertElapsed(1000.0, start)
          }
        }
      }
    }
  }
}

private class WaitUntilNotifiedFixture(factory: TimeoutFactory) : AutoCloseable {
  val timeout = factory.newTimeout()
  val testExecutor = TestExecutor(0)

  /** Returns the nanotime in milliseconds as a double for measuring timeouts.  */
  fun now() = System.nanoTime() / 1_000_000.0

  /**
   * Fails the test unless the time from start until now is duration, accepting differences in
   * -50..+450 milliseconds.
   */
  fun assertElapsed(duration: Double, start: Double) = assertEquals(duration, now() - start - 200.0, 250.0)

  fun Timeout.cancelLater(delay: Long) = testExecutor.schedule(delay.milliseconds) { cancel() }

  override fun close() = testExecutor.close()
}
