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

import de.infix.testBalloon.framework.core.testSuite
import kotlin.time.Clock

val NativeSystemFileSystemTest by testSuite {
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
