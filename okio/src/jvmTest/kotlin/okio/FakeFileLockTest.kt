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

import kotlin.test.assertEquals
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeFileLockTest : BaseFileLockTest() {
  override val fileSystem: FileSystem = FakeFileSystem()

  override val file1: Path
    get() = "/file1".toPath()

  override fun isSupported(): Boolean = true

  override fun isSharedLockSupported(): Boolean = true

  override fun assertLockFailure(ex: Exception, path: Path) {
    assertEquals("Could not lock $path", ex.message)
  }
}
