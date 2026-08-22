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

import app.cash.burst.InterceptTest
import kotlin.test.Ignore
import okio.TestUtil.randomSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Ignore("These tests are flaky and fail on slower hardware, need to be improved")
class ThrottlerTest {
  private val size = 1024L * 80L // 80 KiB
  private val source = randomSource(size)

  private val throttler = Throttler()
  private val throttlerSlow = Throttler()

  private val threads = 4

  @InterceptTest
  private val executorService = TestExecutor(threads)
  private var stopwatch = Stopwatch()

  @BeforeEach fun setup() {
    throttler.bytesPerSecond(4 * size, 4096, 8192)
    throttlerSlow.bytesPerSecond(2 * size, 4096, 8192)
    stopwatch = Stopwatch()
  }

  @Test fun source() {
    throttler.source(source).buffer().readAll(blackholeSink())
    stopwatch.assertElapsed(0.25)
  }

  @Test fun sink() {
    source.buffer().readAll(throttler.sink(blackholeSink()))
    stopwatch.assertElapsed(0.25)
  }

  @Test fun doubleSourceThrottle() {
    throttler.source(throttler.source(source)).buffer().readAll(blackholeSink())
    stopwatch.assertElapsed(0.5)
  }

  @Test fun doubleSinkThrottle() {
    source.buffer().readAll(throttler.sink(throttler.sink(blackholeSink())))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun singleSourceMultiThrottleSlowerThenSlow() {
    source.buffer().readAll(throttler.sink(throttlerSlow.sink(blackholeSink())))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun singleSourceMultiThrottleSlowThenSlower() {
    source.buffer().readAll(throttlerSlow.sink(throttler.sink(blackholeSink())))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun slowSourceSlowerSink() {
    throttler.source(source).buffer().readAll(throttlerSlow.sink(blackholeSink()))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun slowSinkSlowerSource() {
    throttlerSlow.source(source).buffer().readAll(throttler.sink(blackholeSink()))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun parallel() {
    val futures = List(threads) {
      executorService.submit {
        val source = randomSource(size)
        source.buffer().readAll(throttler.sink(blackholeSink()))
      }
    }
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(1.0)
  }

  @Test fun parallelFastThenSlower() {
    val futures = List(threads) {
      executorService.submit {
        val source = randomSource(size)
        source.buffer().readAll(throttler.sink(blackholeSink()))
      }
    }
    Thread.sleep(500)
    throttler.bytesPerSecond(2 * size)
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(1.5)
  }

  @Test fun parallelSlowThenFaster() {
    val futures = List(threads) {
      executorService.submit {
        val source = randomSource(size)
        source.buffer().readAll(throttlerSlow.sink(blackholeSink()))
      }
    }
    Thread.sleep(1_000)
    throttlerSlow.bytesPerSecond(4 * size)
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(1.5)
  }

  @Test fun parallelIndividualThrottle() {
    val futures = List(threads) {
      executorService.submit {
        val throttlerLocal = Throttler()
        throttlerLocal.bytesPerSecond(4 * size, maxByteCount = 8192)

        val source = randomSource(size)
        source.buffer().readAll(throttlerLocal.sink(blackholeSink()))
      }
    }
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(0.25)
  }

  @Test fun parallelGroupAndIndividualThrottle() {
    val futures = List(threads) {
      executorService.submit {
        val throttlerLocal = Throttler()
        throttlerLocal.bytesPerSecond(4 * size, maxByteCount = 8192)

        val source = randomSource(size)
        source.buffer().readAll(throttler.sink(throttlerLocal.sink(blackholeSink())))
      }
    }
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(1.0)
  }
}
