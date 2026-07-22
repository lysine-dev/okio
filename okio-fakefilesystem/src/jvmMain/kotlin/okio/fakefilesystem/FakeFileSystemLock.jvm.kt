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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package okio.fakefilesystem

import java.util.concurrent.locks.StampedLock
import okio.FileLock
import okio.IOException
import okio.LockMode
import okio.Path

actual class FakeFileSystemLock actual constructor(
  private val path: Path,
) {
  private val readWriteLock = StampedLock()

  actual fun lock(mode: LockMode): FileLock {
    val stamp = when (mode) {
      LockMode.Shared -> readWriteLock.tryReadLock()
      LockMode.Exclusive -> readWriteLock.tryWriteLock()
    }

    if (stamp == 0L) {
      throw IOException("Could not lock $path")
    }

    return object : FileLock {
      override val isShared: Boolean
        get() = mode == LockMode.Shared

      override val isValid: Boolean
        get() = readWriteLock.validate(stamp)

      override fun close() {
        readWriteLock.unlock(stamp)
      }
    }
  }
}
