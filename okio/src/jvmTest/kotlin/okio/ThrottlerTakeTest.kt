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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test

class ThrottlerTakeTest {
  private var nowNanos = 0L
  private var elapsedNanos = 0L
  private val throttler = Throttler(allocatedUntil = nowNanos)

  @Test fun takeByByteCount() {
    throttler.bytesPerSecond(bytesPerSecond = 20, waitByteCount = 5, maxByteCount = 10)

    // We get the first 10 bytes immediately (that's maxByteCount).
    assertThat(take(100L)).isEqualTo(10L)
    assertElapsed(0L)

    // Wait a quarter second for each subsequent 5 bytes (that's waitByteCount).
    assertThat(take(100L)).isEqualTo(5L)
    assertElapsed(250L)

    assertThat(take(100L)).isEqualTo(5L)
    assertElapsed(250L)

    // Wait three quarters of a second to build up 15 bytes of potential.
    // Since maxByteCount = 10, there will only be 10 bytes of potential.
    sleep(750L)
    assertElapsed(750L)

    // We get 10 bytes immediately (that's maxByteCount again).
    assertThat(take(100L)).isEqualTo(10L)
    assertElapsed(0L)

    // Wait a quarter second for each subsequent 5 bytes (that's waitByteCount again).
    assertThat(take(100L)).isEqualTo(5L)
    assertElapsed(250L)
  }

  @Test fun takeFullyTimeElapsed() {
    throttler.bytesPerSecond(bytesPerSecond = 20, waitByteCount = 5, maxByteCount = 10)

    // We write the first 10 bytes immediately (that's maxByteCount again).
    takeFully(10L)
    assertElapsed(0L)

    // Wait a quarter second for each subsequent 5 bytes (that's waitByteCount).
    takeFully(5L)
    assertElapsed(250L)

    // Wait a half second for 10 bytes.
    takeFully(10L)
    assertElapsed(500L)

    // Wait a three quarters of a second to build up 15 bytes of potential.
    // Since maxByteCount = 10, there will only be 10 bytes of potential.
    sleep(750L)
    assertElapsed(750L)

    // We write the first 10 bytes immediately (that's maxByteCount again).
    // Wait a quarter second for each subsequent 5 bytes (that's waitByteCount again).
    takeFully(15L)
    assertElapsed(250L)
  }

  @Test fun takeFullyWhenSaturated() {
    throttler.bytesPerSecond(400L, 5L, 10L)

    // Saturate the throttler.
    assertThat(take(10L)).isEqualTo(10L)
    assertElapsed(0L)

    // At 400 bytes per second it takes 250 ms to read 100 bytes.
    takeFully(100L)
    assertElapsed(250L)
  }

  @Test fun takeFullyNoLimit() {
    throttler.bytesPerSecond(0L, 5L, 10L)
    takeFully(100L)
    assertElapsed(0L)
  }

  /**
   * We had a bug where integer division truncation would cause us to call wait() for 0 nanos. We
   * fixed it by minimizing integer division generally, and by handling that case specifically.
   */
  @Test fun infiniteWait() {
    throttler.bytesPerSecond(3, maxByteCount = 4, waitByteCount = 4)
    takeFully(7)
    assertElapsed(1000L)
  }

  /** Take at least the minimum and up to `byteCount` bytes, sleeping once if necessary. */
  private fun take(byteCount: Long): Long {
    val byteCountOrWaitNanos = throttler.byteCountOrWaitNanos(nowNanos, byteCount)
    if (byteCountOrWaitNanos >= 0L) return byteCountOrWaitNanos

    nowNanos += -byteCountOrWaitNanos

    val resultAfterWait = throttler.byteCountOrWaitNanos(nowNanos, byteCount)
    assertThat(resultAfterWait).isGreaterThan(0L)
    return resultAfterWait
  }

  /** Take all of `byteCount` bytes, advancing the clock until they're all taken. */
  private fun takeFully(byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0L) {
      val byteCountOrWaitNanos = throttler.byteCountOrWaitNanos(nowNanos, remaining)
      if (byteCountOrWaitNanos >= 0L) {
        remaining -= byteCountOrWaitNanos
      } else {
        nowNanos += -byteCountOrWaitNanos
      }
    }
  }

  private fun assertElapsed(millis: Long) {
    elapsedNanos += TimeUnit.MILLISECONDS.toNanos(millis)
    assertThat(nowNanos).isEqualTo(elapsedNanos)
  }

  private fun sleep(millis: Long) {
    nowNanos += TimeUnit.MILLISECONDS.toNanos(millis)
  }
}
