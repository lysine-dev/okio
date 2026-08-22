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
import platform.windows.CloseHandle
import platform.windows.CreateMutexA
import platform.windows.INFINITE
import platform.windows.ReleaseMutex
import platform.windows.WaitForSingleObject

actual class Lock : Closeable {
  val mutex = CreateMutexA(
    null,
    0,
    null
  ) ?: throw lastErrorToIOException()

  override fun close() {
    CloseHandle(mutex)
  }
}

internal actual fun newLock(): Lock = Lock()
internal actual inline fun Lock.destroy() = close()

actual inline fun <T> Lock.withLock(action: () -> T): T {
  contract {
    callsInPlace(action, InvocationKind.EXACTLY_ONCE)
  }

  try {
    WaitForSingleObject(mutex, INFINITE)
    return action()
  } finally {
    ReleaseMutex(mutex)
  }
}
