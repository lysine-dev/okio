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

import kotlin.test.assertContains
import okio.Path.Companion.toOkioPath
import okio.TestUtil.isWindows
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class JvmFileLockTest() : BaseFileLockTest() {
  @JvmField
  @Rule
  var temporaryFolder = TemporaryFolder()

  override val fileSystem: FileSystem
    get() = FileSystem.SYSTEM

  override val file1: Path by lazy { temporaryFolder.newFile("file1").toOkioPath() }

  override fun isSupported(): Boolean = true

  override fun isSharedLockSupported(): Boolean = !isWindows()

  override fun assertLockFailure(ex: Exception, path: Path) {
    assertContains(ex.message!!, "Could not lock $path")
  }
}
