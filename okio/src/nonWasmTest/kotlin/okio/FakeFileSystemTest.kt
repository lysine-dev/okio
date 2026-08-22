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

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeWindowsFileSystemTest : FakeFileSystemTest(fakeConfig("C:\\".toPath()) { emulateWindows() })

class FakeUnixFileSystemTest : FakeFileSystemTest(fakeConfig("/".toPath()) { emulateUnix() })

class StrictFakeFileSystemTest : FakeFileSystemTest(fakeConfig("/".toPath()) {})

private fun fakeConfig(temporaryDirectory: Path, fsBlock: FakeFileSystem.() -> Unit): FakeFileSystemTest.Config {
  val clock = FakeClock()
  return FakeFileSystemTest.Config(clock, FakeFileSystem(clock).apply(fsBlock), temporaryDirectory)
}

abstract class FakeFileSystemTest internal constructor(config: Config) : AbstractFileSystemTest(
  clock = config.fakeFileSystem.clock,
  fileSystem = config.fakeFileSystem,
  windowsLimitations = !config.fakeFileSystem.allowMovingOpenFiles,
  allowClobberingEmptyDirectories = config.fakeFileSystem.allowClobberingEmptyDirectories,
  allowAtomicMoveFromFileToDirectory = false,
  temporaryDirectory = config.temporaryDirectory,
  closeBehavior = CloseBehavior.Closes,
) {

  private val fakeFileSystem = config.fakeFileSystem
  private val fakeClock = config.fakeClock

  @Test
  fun openPathsIncludesOpenSink() {
    val openPath = base / "open-file"
    val sink = fileSystem.sink(openPath)
    assertEquals(openPath, fakeFileSystem.openPaths.single())
    sink.close()
    assertTrue(fakeFileSystem.openPaths.isEmpty())
  }

  @Test
  fun openPathsIncludesOpenSource() {
    val openPath = base / "open-file"
    openPath.writeUtf8("hello, world!")
    assertTrue(fakeFileSystem.openPaths.isEmpty())
    val source = fileSystem.source(openPath)
    assertEquals(openPath, fakeFileSystem.openPaths.single())
    source.close()
    assertTrue(fakeFileSystem.openPaths.isEmpty())
  }

  @Test
  fun openPathsIsOpenOrder() {
    if (!fakeFileSystem.allowWritesWhileWriting) return

    val fileA = base / "a"
    val fileB = base / "b"
    val fileC = base / "c"
    val fileD = base / "d"

    assertEquals(fakeFileSystem.openPaths, listOf())
    val sinkD = fileSystem.sink(fileD)
    assertEquals(fakeFileSystem.openPaths, listOf(fileD))
    val sinkB = fileSystem.sink(fileB)
    assertEquals(fakeFileSystem.openPaths, listOf(fileD, fileB))
    val sinkC = fileSystem.sink(fileC)
    assertEquals(fakeFileSystem.openPaths, listOf(fileD, fileB, fileC))
    val sinkA = fileSystem.sink(fileA)
    assertEquals(fakeFileSystem.openPaths, listOf(fileD, fileB, fileC, fileA))
    val sinkB2 = fileSystem.sink(fileB)
    assertEquals(fakeFileSystem.openPaths, listOf(fileD, fileB, fileC, fileA, fileB))
    sinkD.close()
    assertEquals(fakeFileSystem.openPaths, listOf(fileB, fileC, fileA, fileB))
    sinkB2.close()
    assertEquals(fakeFileSystem.openPaths, listOf(fileB, fileC, fileA))
    sinkB.close()
    assertEquals(fakeFileSystem.openPaths, listOf(fileC, fileA))
    sinkC.close()
    assertEquals(fakeFileSystem.openPaths, listOf(fileA))
    sinkA.close()
    assertEquals(fakeFileSystem.openPaths, listOf())
  }

  @Test
  fun allPathsIncludesFile() {
    val file = base / "all-files-includes-file"
    file.writeUtf8("hello, world!")
    assertEquals(setOf(base, file), fakeFileSystem.allPaths)
  }

  @Test
  fun allPathsIsSorted() {
    val fileA = base / "a"
    val fileB = base / "b"
    val fileC = base / "c"
    val fileD = base / "d"

    // Create files in a different order than the sorted order, so a file system that returns files
    // in creation-order or reverse-creation order won't pass by accident.
    fileD.writeUtf8("fileD")
    fileB.writeUtf8("fileB")
    fileC.writeUtf8("fileC")
    fileA.writeUtf8("fileA")

    assertEquals(listOf(base, fileA, fileB, fileC, fileD), fakeFileSystem.allPaths.toList())
  }

  @Test
  fun allPathsIncludesDirectory() {
    val dir = base / "all-files-includes-directory"
    fileSystem.createDirectory(dir)
    assertEquals(setOf(base, dir), fakeFileSystem.allPaths)
  }

  @Test
  fun allPathsDoesNotIncludeDeletedFile() {
    val file = base / "all-files-does-not-include-deleted-file"
    file.writeUtf8("hello, world!")
    fileSystem.delete(file)
    assertEquals(setOf(base), fakeFileSystem.allPaths)
  }

  @Test
  fun allPathsDoesNotIncludeDeletedOpenFile() {
    if (windowsLimitations) return // Can't delete open files with Windows' limitations.

    val file = base / "all-files-does-not-include-deleted-open-file"
    val sink = fileSystem.sink(file)
    assertEquals(setOf(base, file), fakeFileSystem.allPaths)
    fileSystem.delete(file)
    assertEquals(setOf(base), fakeFileSystem.allPaths)
    sink.close()
  }

  @Test
  fun fileLastAccessedTime() {
    val path = base / "file-last-accessed-time"

    fakeClock.sleep(1.minutes)
    path.writeUtf8("hello, world!")
    val createdAt = clock.now()

    fakeClock.sleep(1.minutes)
    path.writeUtf8("hello again!")
    val modifiedAt = clock.now()

    fakeClock.sleep(1.minutes)
    path.readUtf8()
    val accessedAt = clock.now()

    val metadata = fileSystem.metadata(path)
    assertEquals(createdAt, metadata.createdAt)
    assertEquals(modifiedAt, metadata.lastModifiedAt)
    assertEquals(accessedAt, metadata.lastAccessedAt)
  }

  @Test
  fun directoryLastAccessedTime() {
    val path = base / "directory-last-accessed-time"

    fakeClock.sleep(1.minutes)
    fileSystem.createDirectory(path)
    val createdAt = clock.now()

    fakeClock.sleep(1.minutes)
    (path / "child").writeUtf8("hello world!")
    val modifiedAt = clock.now()

    fakeClock.sleep(1.minutes)
    fileSystem.list(path)
    val accessedAt = clock.now()

    val metadata = fileSystem.metadata(path)
    assertEquals(createdAt, metadata.createdAt)
    assertEquals(modifiedAt, metadata.lastModifiedAt)
    assertEquals(accessedAt, metadata.lastAccessedAt)
  }

  @Test
  fun checkNoOpenFilesThrowsOnOpenSource() {
    val path = base / "check-no-open-files-open-source"
    path.writeUtf8("hello, world!")
    val exception = fileSystem.source(path).use { source ->
      assertFailsWith<IllegalStateException> {
        fakeFileSystem.checkNoOpenFiles()
      }
    }

    assertEquals(
      """
      |expected 0 open files, but found:
      |    $path
      """.trimMargin(),
      exception.message,
    )
    assertEquals("file opened for READ here", exception.cause?.message)

    // Now that the source is closed this is safe.
    fakeFileSystem.checkNoOpenFiles()
  }

  @Test
  fun checkNoOpenFilesThrowsOnOpenSink() {
    val path = base / "check-no-open-files-open-sink"
    val exception = fileSystem.sink(path).use { source ->
      assertFailsWith<IllegalStateException> {
        fakeFileSystem.checkNoOpenFiles()
      }
    }

    assertEquals(
      """
      |expected 0 open files, but found:
      |    $path
      """.trimMargin(),
      exception.message,
    )
    assertEquals("file opened for WRITE here", exception.cause?.message)

    // Now that the source is closed this is safe.
    fakeFileSystem.checkNoOpenFiles()
  }

  @Test
  fun createDirectoriesForVolumeLetterRoot() {
    val path = "X:\\".toPath()
    fileSystem.createDirectories(path)
    assertTrue(fileSystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForChildOfVolumeLetterRoot() {
    val path = "X:\\path".toPath()
    fileSystem.createDirectories(path)
    assertTrue(fileSystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForUnixRoot() {
    val path = "/".toPath()
    fileSystem.createDirectories(path)
    assertTrue(fileSystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForChildOfUnixRoot() {
    val path = "/path".toPath()
    fileSystem.createDirectories(path)
    assertTrue(fileSystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForUncRoot() {
    val path = "\\\\server".toPath()
    fileSystem.createDirectories(path)
    assertTrue(fileSystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForChildOfUncRoot() {
    val path = "\\\\server\\project".toPath()
    fileSystem.createDirectories(path)
    assertTrue(fileSystem.metadata(path).isDirectory)
  }

  @Test
  fun workingDirectoryMustBeAbsolute() {
    val exception = assertFailsWith<IllegalArgumentException> {
      fakeFileSystem.workingDirectory = "some/relative/path".toPath()
    }
    assertEquals("expected an absolute path but was some/relative/path", exception.message)
  }

  @Test
  fun metadataForRootsGeneratedOnDemand() {
    assertTrue(fileSystem.metadata("X:\\".toPath()).isDirectory)
    assertTrue(fileSystem.metadata("/".toPath()).isDirectory)
    assertTrue(fileSystem.metadata("\\\\server".toPath()).isDirectory)
  }

  @Test
  fun startWriteWhileWritingNotAllowedWhenStrict() {
    val path = base / "write-write"
    path.writeUtf8("hello world!")
    fileSystem.sink(path).use {
      try {
        fileSystem.sink(path).use {
        }
        assertTrue(fakeFileSystem.allowWritesWhileWriting)
      } catch (_: IOException) {
        assertFalse(fakeFileSystem.allowWritesWhileWriting)
      }
    }
  }

  @Test
  fun startReadWhileWritingNotAllowedWhenStrict() {
    val path = base / "write-read"
    path.writeUtf8("hello world!")
    fileSystem.sink(path).use {
      try {
        fileSystem.source(path).use {
        }
        assertTrue(fakeFileSystem.allowReadsWhileWriting)
      } catch (_: IOException) {
        assertFalse(fakeFileSystem.allowReadsWhileWriting)
      }
    }
  }

  @Test
  fun startWriteWhileReadingNotAllowedWhenStrict() {
    val path = base / "read-write"
    path.writeUtf8("hello world!")
    fileSystem.source(path).use {
      try {
        fileSystem.sink(path).use {
        }
        assertTrue(fakeFileSystem.allowReadsWhileWriting)
      } catch (_: IOException) {
        assertFalse(fakeFileSystem.allowReadsWhileWriting)
      }
    }
  }

  @Test
  fun startReadWhileReadingAllowedWhenStrict() {
    val path = base / "read-read"
    path.writeUtf8("hello world!")
    fileSystem.source(path).use {
      fileSystem.source(path).use {
      }
    }
  }

  @Test
  fun symlinkCanBeUsedAfterSettingAllowSymlinksToFalse() {
    if (!supportsSymlink()) return

    val target = base / "symlink-target"
    val source = base / "symlink-source"
    fileSystem.createSymlink(source, target)
    fakeFileSystem.allowSymlinks = false
    target.writeUtf8("I am the target file")
    assertEquals("I am the target file", source.readUtf8())
  }

  @Test
  fun symlinkCannotBeCreatedAfterSettingAllowSymlinksToFalse() {
    fakeFileSystem.allowSymlinks = false
    val target = base / "symlink-target"
    val source = base / "symlink-source"
    assertFailsWith<IOException> {
      fileSystem.createSymlink(source, target)
    }
  }

  @Test
  fun fileExtras() {
    val path = base / "a.txt"
    path.writeUtf8("hello")
    fakeFileSystem.setExtra(path, ContentTypeExtra::class, ContentTypeExtra("text/plain"))
    val metadata = fileSystem.metadata(path)
    assertEquals(ContentTypeExtra("text/plain"), metadata.extra(ContentTypeExtra::class))
  }

  @Test
  fun directoryExtras() {
    val path = base / "a.txt"
    fileSystem.createDirectory(path)
    fakeFileSystem.setExtra(path, ContentTypeExtra::class, ContentTypeExtra("text/plain"))
    val metadata = fileSystem.metadata(path)
    assertEquals(ContentTypeExtra("text/plain"), metadata.extra(ContentTypeExtra::class))
  }

  @Test
  fun symlinkExtras() {
    if (!supportsSymlink()) return

    val pathA = base / "a.txt"
    val pathB = base / "b.txt"
    fileSystem.createSymlink(pathA, pathB)
    fakeFileSystem.setExtra(pathA, ContentTypeExtra::class, ContentTypeExtra("text/plain"))
    val metadata = fileSystem.metadata(pathA)
    assertEquals(ContentTypeExtra("text/plain"), metadata.extra(ContentTypeExtra::class))
  }

  @Test
  fun deleteExtra() {
    val path = base / "a.txt"
    path.writeUtf8("hello")
    fakeFileSystem.setExtra(path, ContentTypeExtra::class, ContentTypeExtra("text/plain"))
    fakeFileSystem.setExtra(path, ContentTypeExtra::class, null)
    val metadata = fileSystem.metadata(path)
    assertNull(metadata.extra(ContentTypeExtra::class))
    assertEquals(mapOf(), metadata.extras)
  }

  @Test
  fun extraIsNotCopiedByFileCopy() {
    val pathA = base / "a.txt"
    val pathB = base / "b.txt"
    pathA.writeUtf8("hello")
    fakeFileSystem.setExtra(pathA, ContentTypeExtra::class, ContentTypeExtra("text/plain"))
    fileSystem.copy(pathA, pathB)
    val metadata = fileSystem.metadata(pathB)
    assertNull(metadata.extra(ContentTypeExtra::class))
  }

  @Test
  fun extraIsMovedByAtomicMove() {
    val pathA = base / "a.txt"
    val pathB = base / "b.txt"
    pathA.writeUtf8("hello")
    fakeFileSystem.setExtra(pathA, ContentTypeExtra::class, ContentTypeExtra("text/plain"))
    fileSystem.atomicMove(pathA, pathB)
    val metadata = fileSystem.metadata(pathB)
    assertEquals(ContentTypeExtra("text/plain"), metadata.extra(ContentTypeExtra::class))
  }

  @Test
  fun extrasHappyPath() {
    val metadata = FileMetadata(
      isRegularFile = true,
      size = 10L,
      extras = mapOf(ContentTypeExtra::class to ContentTypeExtra("text/plain")),
    )
    assertEquals(ContentTypeExtra("text/plain"), metadata.extra(ContentTypeExtra::class))
  }

  @Test
  fun createExtrasDefensiveCopy() {
    val extras = mutableMapOf<KClass<*>, Any>(
      ContentTypeExtra::class to ContentTypeExtra("text/plain"),
    )
    val metadata = FileMetadata(
      isRegularFile = true,
      size = 10L,
      extras = extras,
    )
    extras.clear()
    assertEquals(ContentTypeExtra("text/plain"), metadata.extra(ContentTypeExtra::class))
  }

  @Test
  fun getExtraAbsent() {
    val metadata = FileMetadata(
      isRegularFile = true,
      size = 10L,
      extras = mapOf(),
    )
    assertNull(metadata.extra(ContentTypeExtra::class))
  }

  @Test
  fun getExtraWrongType() {
    val metadata = FileMetadata(
      isRegularFile = true,
      size = 10L,
      extras = mapOf(ContentTypeExtra::class to "hello"),
    )
    assertFailsWith<ClassCastException> {
      metadata.extra(ContentTypeExtra::class)
    }
  }

  internal data class ContentTypeExtra(
    val contentType: String,
  )

  internal class Config(val fakeClock: FakeClock, val fakeFileSystem: FakeFileSystem, val temporaryDirectory: Path)
}
