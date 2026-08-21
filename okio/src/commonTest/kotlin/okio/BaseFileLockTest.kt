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

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class BaseFileLockTest {
  abstract val fileSystem: FileSystem
  abstract val file1: Path

  abstract fun isSupported(): Boolean

  abstract fun isSharedLockSupported(): Boolean

  @BeforeTest
  fun createFile() {
    fileSystem.write(file1) {}
  }

  @Test
  fun testLockAndUnlock() {
    if (!isSupported()) {
      return
    }

    val lock = fileSystem.lock(file1, mode = LockMode.Exclusive)

    assertTrue(lock.isValid)
    assertFalse(lock.isShared)

    lock.close()
    assertFalse(lock.isValid)
  }

  @Test
  fun testExclusiveLock() {
    if (!isSupported()) {
      return
    }

    val lock1 = fileSystem.lock(file1, mode = LockMode.Exclusive)

    assertTrue(lock1.isValid)

    val x2 = assertFailsWith<IOException> { fileSystem.lock(file1, mode = LockMode.Exclusive) }
    assertLockFailure(x2, file1)

    val x3 = assertFailsWith<IOException> { fileSystem.lock(file1, mode = LockMode.Shared) }
    assertLockFailure(x3, file1)

    lock1.close()

    val lock4 = fileSystem.lock(file1, mode = LockMode.Exclusive)
    assertTrue(lock4.isValid)
  }

  @Test
  fun testSharedLock() {
    if (!isSupported()) {
      return
    }

    val lock1 = fileSystem.lock(file1, mode = LockMode.Shared)

    assertTrue(lock1.isValid)
    val x2 = assertFailsWith<IOException> { fileSystem.lock(file1, mode = LockMode.Exclusive) }
    assertLockFailure(x2, file1)

    val lock3 = if (isSharedLockSupported()) {
      val lock3 = fileSystem.lock(file1, mode = LockMode.Shared)
      assertTrue(lock3.isValid)
      lock3
    } else {
      val x3 = assertFailsWith<IOException> { fileSystem.lock(file1, mode = LockMode.Shared) }
      assertLockFailure(x3, file1)
      null
    }

    assertTrue(lock1.isValid)

    lock1.close()

    if (lock3 != null) {
      val x4 = assertFailsWith<IOException> { fileSystem.lock(file1, mode = LockMode.Exclusive) }
      assertLockFailure(x4, file1)

      lock3.close()
    }

    val lock5 = fileSystem.lock(file1, mode = LockMode.Exclusive)
    assertTrue(lock5.isValid)
  }

  abstract fun assertLockFailure(ex: Exception, path: Path)
}
