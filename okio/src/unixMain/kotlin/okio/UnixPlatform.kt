/*
 * Copyright (C) 2026 Square, Inc.
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

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.errno
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock

actual class Lock : Closeable {
  val mutex = nativeHeap.alloc<pthread_mutex_t>().apply {
    if (pthread_mutex_init(ptr, null) != 0) {
      throw errnoToIOException(errno)
    }
  }

  override fun close() {
    pthread_mutex_destroy(mutex.ptr)
    nativeHeap.free(mutex)
  }
}

internal actual fun newLock(): Lock = Lock()
internal actual inline fun Lock.destroy() = close()

actual inline fun <T> Lock.withLock(action: () -> T): T {
  contract {
    callsInPlace(action, InvocationKind.EXACTLY_ONCE)
  }

  try {
    pthread_mutex_lock(mutex.ptr)
    return action()
  } finally {
    pthread_mutex_unlock(mutex.ptr)
  }
}
