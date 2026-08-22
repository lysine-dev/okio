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

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isIn
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.io.File
import java.nio.file.Paths
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test

class FileSystemJavaTest {
  @Test
  fun pathApi() {
    val path = "/home/jesse/todo.txt".toPath(false)
    assertThat("/home/jesse".toPath(false).div("todo.txt")).isEqualTo(path)
    assertThat("/home/jesse/todo.txt".toPath(false)).isEqualTo(path)
    assertThat(path.isAbsolute).isTrue()
    assertThat(path.isRelative).isFalse()
    assertThat(path.isRoot).isFalse()
    assertThat(path.name).isEqualTo("todo.txt")
    assertThat(path.nameBytes).isEqualTo("todo.txt".encodeUtf8())
    assertThat(path.parent).isEqualTo("/home/jesse".toPath(false))
    assertThat(path.volumeLetter).isNull()
  }

  @Test
  fun directorySeparator() {
    assertThat(Path.DIRECTORY_SEPARATOR).isIn("/", "\\")
  }

  /** Like the same test in JvmTest, but this is using the Java APIs.  */
  @Test
  fun javaIoFileToOkioPath() {
    val string = "/foo/bar/baz".replace("/", Path.DIRECTORY_SEPARATOR)
    val javaIoFile = File(string)
    val okioPath: Path = string.toPath(false)
    assertThat(javaIoFile.toOkioPath(false)).isEqualTo(okioPath)
    assertThat(okioPath.toFile()).isEqualTo(javaIoFile)
  }

  /** Like the same test in JvmTest, but this is using the Java APIs.  */
  @Test
  fun nioPathToOkioPath() {
    val string = "/foo/bar/baz".replace("/", Path.DIRECTORY_SEPARATOR)
    val nioPath = Paths.get(string)
    val okioPath: Path = string.toPath(false)
    assertThat(nioPath.toOkioPath(false)).isEqualTo(okioPath)
    assertThat(okioPath.toNioPath() as Any).isEqualTo(nioPath)
  }

  // Just confirm these APIs exist; don't invoke them
  @Suppress("unused")
  fun fileSystemApi() {
    val fileSystem = FileSystem.SYSTEM
    val pathA: Path = "a.txt".toPath()
    val pathB: Path = "b.txt".toPath()
    fileSystem.canonicalize(pathA)
    fileSystem.metadata(pathA)
    fileSystem.metadataOrNull(pathA)
    fileSystem.exists(pathA)
    fileSystem.list(pathA)
    fileSystem.listOrNull(pathA)
    fileSystem.listRecursively(pathA, false)
    fileSystem.listRecursively(pathA)
    fileSystem.openReadOnly(pathA)
    fileSystem.openReadWrite(pathA, mustCreate = false, mustExist = false)
    fileSystem.openReadWrite(pathA)
    fileSystem.source(pathA)
    // Note that FileSystem.read() isn't available to Java callers.
    fileSystem.sink(pathA, false)
    fileSystem.sink(pathA)
    // Note that FileSystem.write() isn't available to Java callers.
    fileSystem.appendingSink(pathA, false)
    fileSystem.appendingSink(pathA)
    fileSystem.createDirectory(pathA)
    fileSystem.createDirectories(pathA)
    fileSystem.atomicMove(pathA, pathB)
    fileSystem.copy(pathA, pathB)
    fileSystem.delete(pathA)
    fileSystem.deleteRecursively(pathA)
    fileSystem.createSymlink(pathA, pathB)
  }

  @Test
  fun fakeFileSystemApi() {
    val fakeFileSystem = FakeFileSystem()
    assertThat(fakeFileSystem.clock).isNotNull()
    assertThat(fakeFileSystem.allPaths).isEmpty()
    assertThat(fakeFileSystem.openPaths).isEmpty()
    fakeFileSystem.checkNoOpenFiles()
  }

  @Test
  fun forwardingFileSystemApi() {
    val fakeFileSystem = FakeFileSystem()
    val log: MutableList<String> = ArrayList()
    val forwardingFileSystem: ForwardingFileSystem = object : ForwardingFileSystem(fakeFileSystem) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        log.add("$functionName($parameterName=$path)")
        return path
      }
    }
    forwardingFileSystem.metadataOrNull("/".toPath(false))
    assertThat(log).containsExactly("metadataOrNull(path=/)")
  }
}
