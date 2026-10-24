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

import app.cash.burst.InterceptTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test

class ZipFileSystemJavaTest {
  private val fileSystem = FileSystem.SYSTEM

  @InterceptTest
  private val baseTestDirectory = TestDirectory(fileSystem)
  private val base: Path get() = baseTestDirectory.path

  @Test
  fun zipFileSystemApi() {
    val zipPath = ZipBuilder(base)
      .addEntry("hello.txt", "Hello World")
      .build()
    val zipFileSystem = fileSystem.openZip(zipPath)
    zipFileSystem.source("hello.txt".toPath(false)).buffer().use { source ->
      val content = source.readUtf8()
      assertThat(content).isEqualTo("Hello World")
    }
  }
}
