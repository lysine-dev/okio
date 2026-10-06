/*
 * Copyright (C) 2021 Square, Inc.
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
import java.nio.file.FileSystems
import kotlin.time.Clock
import okio.FileSystem.Companion.asOkioFileSystem

/**
 * Run a regular file system test, but use [FileHandle] for more file system operations than usual.
 * This is intended to increase test coverage for [FileHandle].
 */
val FileHandleFileSystemTest by testSuite {
  fileSystemTests(
    newFixture = {
      FileSystemFixture(
        clock = Clock.System,
        fileSystem = FileHandleTestingFileSystem(FileSystem.SYSTEM),
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

val FileHandleNioJimFileSystemWrapperFileSystemTest by testSuite {
  fileSystemTests(
    newFixture = {
      FileSystemFixture(
        clock = Clock.System,
        fileSystem = FileHandleTestingFileSystem(
          Jimfs
            .newFileSystem(
              when (Path.DIRECTORY_SEPARATOR == "\\") {
                true -> Configuration.windows()
                false -> Configuration.unix()
              },
            ).asOkioFileSystem(),
        ),
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

val FileHandleNioDefaultFileSystemWrapperFileSystemTest by testSuite {
  fileSystemTests(
    newFixture = {
      FileSystemFixture(
        clock = Clock.System,
        fileSystem = FileHandleTestingFileSystem(
          FileSystems.getDefault().asOkioFileSystem(),
        ),
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

/**
 * A testing-only file system that implements all reading and writing operations with
 * [FileHandle]. This is intended to increase test coverage for [FileHandle].
 */
private class FileHandleTestingFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
  override fun source(file: Path): Source {
    val fileHandle = openReadOnly(file)
    return fileHandle.source()
      .also { fileHandle.close() }
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    val fileHandle = openReadWrite(file, mustCreate = mustCreate, mustExist = false)
    fileHandle.resize(0L) // If the file already has data, get rid of it.
    return fileHandle.sink()
      .also { fileHandle.close() }
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    val fileHandle = openReadWrite(file, mustCreate = false, mustExist = mustExist)
    return fileHandle.appendingSink()
      .also { fileHandle.close() }
  }
}
