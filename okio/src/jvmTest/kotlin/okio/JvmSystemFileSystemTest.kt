/*
 * Copyright (C) 2020 Square, Inc.
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

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.io.InterruptedIOException
import java.nio.file.FileSystems
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.time.Clock
import okio.FileSystem.Companion.asOkioFileSystem
import org.junit.jupiter.api.Test

/**
 * This test will run using [NioSystemFileSystem] by default. If [java.nio.file.Files] is not found
 * on the classpath, [JvmSystemFileSystem] will be use instead.
 */
class NioSystemFileSystemTest : AbstractFileSystemTest(
  clock = Clock.System,
  fileSystem = FileSystem.SYSTEM,
  windowsLimitations = Path.DIRECTORY_SEPARATOR == "\\",
  allowClobberingEmptyDirectories = Path.DIRECTORY_SEPARATOR == "\\",
  allowAtomicMoveFromFileToDirectory = false,
  temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
  closeBehavior = CloseBehavior.DoesNothing,
)

class JvmSystemFileSystemTest : AbstractFileSystemTest(
  clock = Clock.System,
  fileSystem = JvmSystemFileSystem(),
  windowsLimitations = Path.DIRECTORY_SEPARATOR == "\\",
  allowClobberingEmptyDirectories = Path.DIRECTORY_SEPARATOR == "\\",
  allowAtomicMoveFromFileToDirectory = false,
  temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
  closeBehavior = CloseBehavior.DoesNothing,
) {

  @Test fun checkInterruptedBeforeDeleting() {
    Thread.currentThread().interrupt()
    try {
      fileSystem.delete(base)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("interrupted", expected.message)
      assertFalse(Thread.interrupted())
    }
  }
}

class NioJimFileSystemWrappingFileSystemTest : AbstractFileSystemTest(
  clock = Clock.System,
  fileSystem = Jimfs
    .newFileSystem(
      when (Path.DIRECTORY_SEPARATOR == "\\") {
        true -> Configuration.windows()
        false -> Configuration.unix()
      },
    ).asOkioFileSystem(),
  windowsLimitations = false,
  allowClobberingEmptyDirectories = true,
  allowAtomicMoveFromFileToDirectory = true,
  temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
  closeBehavior = CloseBehavior.Closes,
)

class NioDefaultFileSystemWrappingFileSystemTest : AbstractFileSystemTest(
  clock = Clock.System,
  fileSystem = FileSystems.getDefault().asOkioFileSystem(),
  windowsLimitations = false,
  allowClobberingEmptyDirectories = Path.DIRECTORY_SEPARATOR == "\\",
  allowAtomicMoveFromFileToDirectory = false,
  allowRenameWhenTargetIsOpen = Path.DIRECTORY_SEPARATOR != "\\",
  temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
  closeBehavior = CloseBehavior.Unsupported,
)
