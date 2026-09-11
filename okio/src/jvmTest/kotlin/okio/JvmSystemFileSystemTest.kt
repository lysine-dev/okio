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
import de.infix.testBalloon.framework.core.testSuite
import java.io.InterruptedIOException
import java.nio.file.FileSystems
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.time.Clock
import okio.FileSystem.Companion.asOkioFileSystem

/**
 * This test will run using [NioSystemFileSystem] by default. If [java.nio.file.Files] is not found
 * on the classpath, [JvmSystemFileSystem] will be use instead.
 */
val NioSystemFileSystemTest by testSuite {
  fileSystemTests(
    newFixture = {
      FileSystemFixture(
        clock = Clock.System,
        fileSystem = FileSystem.SYSTEM,
        windowsLimitations = Path.DIRECTORY_SEPARATOR == "\\",
        allowClobberingEmptyDirectories = Path.DIRECTORY_SEPARATOR == "\\",
        allowAtomicMoveFromFileToDirectory = false,
        temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
        closeBehavior = CloseBehavior.DoesNothing,
        variant = FileSystemVariant.System,
      )
    },
  )
}

val JvmSystemFileSystemTest by testSuite {
  fileSystemTests(
    newFixture = {
      FileSystemFixture(
        clock = Clock.System,
        fileSystem = JvmSystemFileSystem(),
        windowsLimitations = Path.DIRECTORY_SEPARATOR == "\\",
        allowClobberingEmptyDirectories = Path.DIRECTORY_SEPARATOR == "\\",
        allowAtomicMoveFromFileToDirectory = false,
        temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
        closeBehavior = CloseBehavior.DoesNothing,
        variant = FileSystemVariant.System,
      )
    },
    extraTests = {
      test("checkInterruptedBeforeDeleting") {
        Thread.currentThread().interrupt()
        try {
          fileSystem.delete(base)
          fail()
        } catch (expected: InterruptedIOException) {
          assertEquals("interrupted", expected.message)
          assertFalse(Thread.interrupted())
        }
      }
    },
  )
}

val NioJimFileSystemWrappingFileSystemTest by testSuite {
  fileSystemTests(
    newFixture = {
      FileSystemFixture(
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
        variant = FileSystemVariant.JimfsWrapping,
      )
    },
  )
}

val NioDefaultFileSystemWrappingFileSystemTest by testSuite {
  fileSystemTests(
    newFixture = {
      FileSystemFixture(
        clock = Clock.System,
        fileSystem = FileSystems.getDefault().asOkioFileSystem(),
        windowsLimitations = false,
        allowClobberingEmptyDirectories = Path.DIRECTORY_SEPARATOR == "\\",
        allowAtomicMoveFromFileToDirectory = false,
        allowRenameWhenTargetIsOpen = Path.DIRECTORY_SEPARATOR != "\\",
        temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
        closeBehavior = CloseBehavior.Unsupported,
        variant = FileSystemVariant.System,
      )
    },
  )
}
