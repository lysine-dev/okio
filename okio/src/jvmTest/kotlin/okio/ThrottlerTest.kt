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
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import okio.TestUtil.randomSource

// Disabled because these tests are flaky and fail on slower hardware, need to be improved
val ThrottlerTest by testSuite(testConfig = TestConfig.disable()) {
  testFixture { ThrottleFixture() } asContextForEach {
    test("source") {
      throttler.source(source).buffer().readAll(blackholeSink())
      stopwatch.assertElapsed(0.25)
    }

    test("sink") {
      source.buffer().readAll(throttler.sink(blackholeSink()))
      stopwatch.assertElapsed(0.25)
    }

    test("doubleSourceThrottle") {
      throttler.source(throttler.source(source)).buffer().readAll(blackholeSink())
      stopwatch.assertElapsed(0.5)
    }

    test("doubleSinkThrottle") {
      source.buffer().readAll(throttler.sink(throttler.sink(blackholeSink())))
      stopwatch.assertElapsed(0.5)
    }

    test("singleSourceMultiThrottleSlowerThenSlow") {
      source.buffer().readAll(throttler.sink(throttlerSlow.sink(blackholeSink())))
      stopwatch.assertElapsed(0.5)
    }

    test("singleSourceMultiThrottleSlowThenSlower") {
      source.buffer().readAll(throttlerSlow.sink(throttler.sink(blackholeSink())))
      stopwatch.assertElapsed(0.5)
    }

    test("slowSourceSlowerSink") {
      throttler.source(source).buffer().readAll(throttlerSlow.sink(blackholeSink()))
      stopwatch.assertElapsed(0.5)
    }

    test("slowSinkSlowerSource") {
      throttlerSlow.source(source).buffer().readAll(throttler.sink(blackholeSink()))
      stopwatch.assertElapsed(0.5)
    }

    test("parallel") {
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

    test("parallelFastThenSlower") {
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

    test("parallelSlowThenFaster") {
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

    test("parallelIndividualThrottle") {
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

    test("parallelGroupAndIndividualThrottle") {
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
}

private class ThrottleFixture : AutoCloseable {
  val source = randomSource(size)
  val throttler = Throttler().apply { bytesPerSecond(4 * size, 4096, 8192) }
  val throttlerSlow = Throttler().apply { bytesPerSecond(2 * size, 4096, 8192) }
  val executorService = TestExecutor(threads)
  val stopwatch = Stopwatch()
  override fun close() = executorService.close()
}

private const val size = 1024L * 80L // 80 KiB
private const val threads = 4
