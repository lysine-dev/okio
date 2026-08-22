/*
 * Copyright (C) 2021 Square, Inc.
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

import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(5, unit = TimeUnit.SECONDS)
class TimeoutTest {

  @Test fun intersectWithReturnsAValue() {
    val timeoutA = Timeout()
    val timeoutB = Timeout()

    val s = timeoutA.intersectWith(timeoutB) { "hello" }
    assertEquals("hello", s)
  }

  @Test fun intersectWithPrefersSmallerTimeout() {
    val timeoutA = Timeout()
    timeoutA.timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)

    val timeoutB = Timeout()
    timeoutB.timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)

    timeoutA.intersectWith(timeoutB) {
      assertEquals(smallerTimeoutNanos, timeoutA.timeoutNanos())
      assertEquals(biggerTimeoutNanos, timeoutB.timeoutNanos())
    }
    timeoutB.intersectWith(timeoutA) {
      assertEquals(smallerTimeoutNanos, timeoutA.timeoutNanos())
      assertEquals(smallerTimeoutNanos, timeoutB.timeoutNanos())
    }
    assertEquals(smallerTimeoutNanos, timeoutA.timeoutNanos())
    assertEquals(biggerTimeoutNanos, timeoutB.timeoutNanos())
  }

  @Test fun intersectWithPrefersNonZeroTimeout() {
    val timeoutA = Timeout()

    val timeoutB = Timeout()
    timeoutB.timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)

    timeoutA.intersectWith(timeoutB) {
      assertEquals(biggerTimeoutNanos, timeoutA.timeoutNanos())
      assertEquals(biggerTimeoutNanos, timeoutB.timeoutNanos())
    }
    timeoutB.intersectWith(timeoutA) {
      assertEquals(0L, timeoutA.timeoutNanos())
      assertEquals(biggerTimeoutNanos, timeoutB.timeoutNanos())
    }
    assertEquals(0L, timeoutA.timeoutNanos())
    assertEquals(biggerTimeoutNanos, timeoutB.timeoutNanos())
  }

  @Test fun intersectWithPrefersSmallerDeadline() {
    val timeoutA = Timeout()
    timeoutA.deadlineNanoTime(smallerDeadlineNanos)

    val timeoutB = Timeout()
    timeoutB.deadlineNanoTime(biggerDeadlineNanos)

    timeoutA.intersectWith(timeoutB) {
      assertEquals(smallerDeadlineNanos, timeoutA.deadlineNanoTime())
      assertEquals(biggerDeadlineNanos, timeoutB.deadlineNanoTime())
    }
    timeoutB.intersectWith(timeoutA) {
      assertEquals(smallerDeadlineNanos, timeoutA.deadlineNanoTime())
      assertEquals(smallerDeadlineNanos, timeoutB.deadlineNanoTime())
    }
    assertEquals(smallerDeadlineNanos, timeoutA.deadlineNanoTime())
    assertEquals(biggerDeadlineNanos, timeoutB.deadlineNanoTime())
  }

  @Test fun intersectWithPrefersNonZeroDeadline() {
    val timeoutA = Timeout()

    val timeoutB = Timeout()
    timeoutB.deadlineNanoTime(biggerDeadlineNanos)

    timeoutA.intersectWith(timeoutB) {
      assertEquals(biggerDeadlineNanos, timeoutA.deadlineNanoTime())
      assertEquals(biggerDeadlineNanos, timeoutB.deadlineNanoTime())
    }
    timeoutB.intersectWith(timeoutA) {
      assertFalse(timeoutA.hasDeadline())
      assertEquals(biggerDeadlineNanos, timeoutB.deadlineNanoTime())
    }
    assertFalse(timeoutA.hasDeadline())
    assertEquals(biggerDeadlineNanos, timeoutB.deadlineNanoTime())
  }

  companion object {
    val smallerTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(500L)
    val biggerTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(1500L)

    val smallerDeadlineNanos = TimeUnit.MILLISECONDS.toNanos(500L)
    val biggerDeadlineNanos = TimeUnit.MILLISECONDS.toNanos(1500L)
  }
}
