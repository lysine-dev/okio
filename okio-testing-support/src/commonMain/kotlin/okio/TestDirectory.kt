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

import de.infix.testBalloon.framework.core.TestSuiteScope

/**
 * A scope that provides a temporary directory on [fileSystem] that's usable for the current test.
 */
fun TestSuiteScope.testDirectory(
  fileSystem: FileSystem,
  temporaryDirectory: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
) = testFixture { fileSystem.createTestPath(temporaryDirectory) }

/**
 * A scope that provides temporary directories on [fileSystem] that's usable for the current test.
 */
fun TestSuiteScope.testDirectories(
  fileSystem: FileSystem,
  vararg temporaryDirectories: Path,
) = testFixture {
  temporaryDirectories.map { parent ->
    fileSystem.createTestPath(parent)
  }
}

fun FileSystem.createTestPath(directory: Path): Path {
  return (directory / "test-${randomToken(16)}").also { createDirectories(it) }
}
