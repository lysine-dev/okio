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

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Manages a `ExecutorService` and shuts it down after the test.
 */
class TestExecutor(corePoolSize: Int = 0) : AutoCloseable {
  private val executorService: ScheduledExecutorService = when {
    isLoom -> Executors.newScheduledThreadPool(corePoolSize, newVirtualThreadFactory())
    else -> Executors.newScheduledThreadPool(corePoolSize)
  }

  fun <T> submit(task: () -> T): Future<T> = executorService.submit<T>(task)

  fun <T> schedule(delay: Duration, command: () -> T): ScheduledFuture<T> =
    executorService.schedule(command, delay.inWholeNanoseconds, TimeUnit.NANOSECONDS)

  override fun close() {
    executorService.shutdown()
  }

  private companion object {
    val isLoom = System.getProperty("loomEnabled").toBoolean()

    fun newVirtualThreadFactory(): ThreadFactory {
      val threadBuilder = Thread::class.java.getMethod("ofVirtual").invoke(null)
      return Class.forName("java.lang.Thread\$Builder").getMethod("factory")
        .invoke(threadBuilder) as ThreadFactory
    }
  }
}
