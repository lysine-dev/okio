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

import de.infix.testBalloon.framework.core.Test.ExecutionScope
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuiteScope
import de.infix.testBalloon.framework.core.disable
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath

/** This test assumes that okio-files/ is the current working directory when executed. */
fun <T : FileSystemFixture> TestSuiteScope.fileSystemTests(
  newFixture: () -> T,
  extraTests: TestFixture.Scope<suspend T.(ExecutionScope) -> Unit>.() -> Unit = {},
) {
  testFixture { newFixture() } asContextForEach {
    test("doesNotExistsWithInvalidPathDoesNotThrow") {
      if (isNodeJsFileSystemOnWindows()) return@test

      val slash = Path.DIRECTORY_SEPARATOR
      // We are testing: `\\127.0.0.1\..\localhost\c$\Windows`.
      val file =
        "${slash}${slash}127.0.0.1$slash..${slash}localhost${slash}c\$${slash}Windows".toPath()

      assertFalse(fileSystem.exists(file))
    }
    test("canonicalizeDotReturnsCurrentWorkingDirectory") {
      if (fileSystem.isFakeFileSystem || fileSystem is ForwardingFileSystem) return@test
      val cwd = fileSystem.canonicalize(".".toPath())
      val cwdString = cwd.toString()
      val slash = Path.DIRECTORY_SEPARATOR
      assertTrue(cwdString) {
        if (isWrappingJimFileSystem) {
          cwdString.endsWith("work")
        } else if (isWasiFileSystem) {
          cwdString.endsWith("/tmp")
        } else {
          cwdString.endsWith("okio${slash}okio") ||
            cwdString.endsWith("${slash}okio-parent-okio-nodefilesystem-test") ||
            cwdString.contains("/CoreSimulator/Devices/") || // iOS simulator.
            cwdString == "/" // Android emulator.
        }
      }
    }

    test("currentWorkingDirectoryIsADirectory") {
      val metadata = fileSystem.metadata(".".toPath())
      assertTrue(metadata.isDirectory)
      assertFalse(metadata.isRegularFile)
    }

    test("canonicalizeAbsolutePathNoSuchFile") {
      assertFailsWith<FileNotFoundException> {
        fileSystem.canonicalize(base / "no-such-file")
      }
    }

    test("canonicalizeRelativePathNoSuchFile") {
      assertFailsWith<FileNotFoundException> {
        fileSystem.canonicalize("no-such-file".toPath())
      }
    }

    test("canonicalizeFollowsSymlinkDirectories") {
      if (!supportsSymlink()) return@test
      val base = fileSystem.canonicalize(base)

      fileSystem.createDirectory(base / "real-directory")

      val expected = base / "real-directory" / "real-file.txt"
      expected.writeUtf8("hello")

      fileSystem.createSymlink(base / "symlink-directory", base / "real-directory")

      val canonicalPath = fileSystem.canonicalize(base / "symlink-directory" / "real-file.txt")
      assertEquals(expected, canonicalPath)
    }

    test("canonicalizeFollowsSymlinkFiles") {
      if (!supportsSymlink()) return@test
      val base = fileSystem.canonicalize(base)

      fileSystem.createDirectory(base / "real-directory")

      val expected = base / "real-directory" / "real-file.txt"
      expected.writeUtf8("hello")

      fileSystem.createSymlink(
        base / "real-directory" / "symlink-file.txt",
        expected,
      )

      val canonicalPath = fileSystem.canonicalize(base / "real-directory" / "symlink-file.txt")
      assertEquals(expected, canonicalPath)
    }

    test("canonicalizeFollowsMultipleDirectoriesAndMultipleFiles") {
      if (!supportsSymlink()) return@test
      val base = fileSystem.canonicalize(base)

      fileSystem.createDirectory(base / "real-directory")

      val expected = base / "real-directory" / "real-file.txt"
      expected.writeUtf8("hello")

      fileSystem.createSymlink(
        base / "real-directory" / "one-symlink-file.txt",
        expected,
      )

      fileSystem.createSymlink(
        base / "real-directory" / "two-symlink-file.txt",
        base / "real-directory" / "one-symlink-file.txt",
      )

      fileSystem.createSymlink(
        base / "one-symlink-directory",
        base / "real-directory",
      )

      fileSystem.createSymlink(
        base / "two-symlink-directory",
        base / "one-symlink-directory",
      )

      assertEquals(
        expected,
        fileSystem.canonicalize(base / "two-symlink-directory" / "two-symlink-file.txt"),
      )
      assertEquals(
        expected,
        fileSystem.canonicalize(base / "two-symlink-directory" / "one-symlink-file.txt"),
      )
      assertEquals(
        expected,
        fileSystem.canonicalize(base / "two-symlink-directory" / "real-file.txt"),
      )

      assertEquals(
        expected,
        fileSystem.canonicalize(base / "one-symlink-directory" / "two-symlink-file.txt"),
      )
      assertEquals(
        expected,
        fileSystem.canonicalize(base / "one-symlink-directory" / "one-symlink-file.txt"),
      )
      assertEquals(
        expected,
        fileSystem.canonicalize(base / "one-symlink-directory" / "real-file.txt"),
      )

      assertEquals(
        expected,
        fileSystem.canonicalize(base / "real-directory" / "two-symlink-file.txt"),
      )
      assertEquals(
        expected,
        fileSystem.canonicalize(base / "real-directory" / "one-symlink-file.txt"),
      )
      assertEquals(
        expected,
        fileSystem.canonicalize(expected),
      )
    }

    test("canonicalizeReturnsDeeperPath") {
      if (!supportsSymlink()) return@test
      val base = fileSystem.canonicalize(base)

      fileSystem.createDirectories(base / "a" / "b" / "c")

      val expected = base / "a" / "b" / "c" / "d.txt"
      expected.writeUtf8("hello")

      fileSystem.createSymlink(
        base / "e.txt",
        "a".toPath() / "b" / "c" / "d.txt",
      )

      assertEquals(
        expected,
        fileSystem.canonicalize(base / "e.txt"),
      )
    }

    test("canonicalizeReturnsShallowerPath") {
      if (!supportsSymlink()) return@test
      val base = fileSystem.canonicalize(base)

      val expected = base / "a.txt"
      expected.writeUtf8("hello")

      fileSystem.createDirectories(base / "b" / "c" / "d")
      fileSystem.createSymlink(
        base / "b" / "c" / "d" / "e.txt",
        "../".toPath() / ".." / ".." / "a.txt",
      )

      assertEquals(
        expected,
        fileSystem.canonicalize(base / "b" / "c" / "d" / "e.txt"),
      )
    }

    test("list") {
      val target = base / "list"
      target.writeUtf8("hello, world!")
      val entries = fileSystem.list(base)
      assertTrue(entries.toString()) { target in entries }
    }

    test("listOnRelativePathReturnsRelativePaths") {
      // Make sure there's always at least one file so our assertion is useful.
      if (fileSystem.isFakeFileSystem) {
        val workingDirectory = "/directory".toPath()
        fileSystem.createDirectory(workingDirectory)
        fileSystem.workingDirectory = workingDirectory
        fileSystem.write("a.txt".toPath()) {
          writeUtf8("hello, world!")
        }
      } else if (isWrappingJimFileSystem || isWasiFileSystem) {
        fileSystem.write("a.txt".toPath()) {
          writeUtf8("hello, world!")
        }
      }

      val entries = fileSystem.list(".".toPath())
      assertTrue(entries.toString()) { entries.isNotEmpty() && entries.all { it.isRelative } }
    }

    test("listOnRelativePathWhichIsNotDotReturnsRelativePaths") {
      if (isNodeJsFileSystem) return@test

      val apiDir = "api".toPath()
      val expectedFiles = listOf(
        apiDir / "okio.api",
        apiDir / "okio.klib.api",
      )

      // Make sure there's always at least one file so our assertion is useful. We copy the first 2
      // entries of the real working directory of the JVM to validate the results on all environment.
      val isFakeFileSystem = fileSystem.isFakeFileSystem ||
        fileSystem is ForwardingFileSystem && fileSystem.delegate.isFakeFileSystem
      if (isFakeFileSystem) {
        val workingDirectory = "/directory".toPath()
        fileSystem.createDirectory(workingDirectory)
        fileSystem.workingDirectory = workingDirectory
      }
      if (isFakeFileSystem || isWrappingJimFileSystem || isWasiFileSystem) {
        fileSystem.createDirectory(apiDir)
        for (path in expectedFiles) {
          fileSystem.write(path) {
            writeUtf8("hello, world!")
          }
        }
      }

      try {
        assertEquals(
          expectedFiles,
          fileSystem.list("api".toPath()),
          // List some entries to help debugging.
          fileSystem.listRecursively(".".toPath()).take(5).toList().joinToString(),
        )
      } catch (e: Throwable) {
        if (e !is AssertionError && e !is FileNotFoundException) {
          throw e
        }

        // Non JVM environments.
        val firstChild = fileSystem.list("Library".toPath()).first()
        assertTrue(
          // List some entries to help debugging.
          fileSystem.listRecursively(".".toPath()).take(5).toList().joinToString(),
        ) {
          // To avoid relying too much on the environment we check that the path contains its parent
          // once and that it's relative.
          firstChild.isRelative &&
            firstChild.toString().startsWith("Library") &&
            firstChild.toString().split("Library").size == 2
        }
      }
    }

    test("listOrNullOnRelativePathWhichIsNotDotReturnsRelativePaths") {
      if (isNodeJsFileSystem) return@test

      val apiDir = "api".toPath()
      val expectedFiles = listOf(
        apiDir / "okio.api",
        apiDir / "okio.klib.api",
      )

      // Make sure there's always at least one file so our assertion is useful. We copy the first 2
      // entries of the real working directory of the JVM to validate the results on all environment.
      val isFakeFileSystem = fileSystem.isFakeFileSystem ||
        fileSystem is ForwardingFileSystem && fileSystem.delegate.isFakeFileSystem
      if (isFakeFileSystem) {
        val workingDirectory = "/directory".toPath()
        fileSystem.createDirectory(workingDirectory)
        fileSystem.workingDirectory = workingDirectory
      }
      if (isFakeFileSystem || isWrappingJimFileSystem) {
        fileSystem.createDirectory(apiDir)
        for (path in expectedFiles) {
          fileSystem.write(path) {
            writeUtf8("hello, world!")
          }
        }
      }

      try {
        assertEquals(
          expectedFiles,
          fileSystem.listOrNull("api".toPath()),
          // List some entries to help debugging.
          fileSystem.listRecursively(".".toPath()).take(5).toList().joinToString(),
        )
      } catch (e: Throwable) {
        if (e !is AssertionError && e !is FileNotFoundException) {
          throw e
        }

        // Non JVM environments.
        val firstChild = fileSystem.list("Library".toPath()).first()
        assertTrue(
          // List some entries to help debugging.
          fileSystem.listRecursively(".".toPath()).take(5).toList().joinToString(),
        ) {
          // To avoid relying too much on the environment we check that the path contains its parent
          // once and that it's relative.
          firstChild.isRelative &&
            firstChild.toString().startsWith("Library") &&
            firstChild.toString().split("Library").size == 2
        }
      }
    }

    test("listResultsAreSorted") {
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

      val entries = fileSystem.list(base)
      assertEquals(entries, listOf(fileA, fileB, fileC, fileD))
    }

    test("listNoSuchDirectory") {
      assertFailsWith<FileNotFoundException> {
        fileSystem.list(base / "no-such-directory")
      }
    }

    test("listFile") {
      val target = base / "list"
      target.writeUtf8("hello, world!")
      val exception = assertFailsWith<IOException> {
        fileSystem.list(target)
      }
      assertTrue(exception !is FileNotFoundException)
    }

    test("listOrNull") {
      val target = base / "list"
      target.writeUtf8("hello, world!")
      val entries = fileSystem.listOrNull(base)!!
      assertTrue(entries.toString()) { target in entries }
    }

    test("listOrNullOnRelativePathReturnsRelativePaths") {
      // Make sure there's always at least one file so our assertion is useful.
      if (fileSystem.isFakeFileSystem) {
        val workingDirectory = "/directory".toPath()
        fileSystem.createDirectory(workingDirectory)
        fileSystem.workingDirectory = workingDirectory
        fileSystem.write("a.txt".toPath()) {
          writeUtf8("hello, world!")
        }
      } else if (isWrappingJimFileSystem) {
        fileSystem.write("a.txt".toPath()) {
          writeUtf8("hello, world!")
        }
      }

      val entries = fileSystem.listOrNull(".".toPath())
      assertTrue(entries.toString()) { entries!!.isNotEmpty() && entries.all { it.isRelative } }
    }

    test("listOrNullResultsAreSorted") {
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

      val entries = fileSystem.listOrNull(base)
      assertEquals(entries, listOf(fileA, fileB, fileC, fileD))
    }

    test("listOrNullNoSuchDirectory") {
      assertNull(fileSystem.listOrNull(base / "no-such-directory"))
    }

    test("listOrNullFile") {
      val target = base / "list"
      target.writeUtf8("hello, world!")
      assertNull(fileSystem.listOrNull(target))
    }

    test("listRecursivelyReturnsEmpty") {
      val entries = fileSystem.listRecursively(base)
      assertEquals(entries.toList(), listOf())
    }

    test("listRecursivelyReturnsSingleFile") {
      val baseA = base / "a"
      baseA.writeUtf8("a")
      val entries = fileSystem.listRecursively(base)
      assertEquals(entries.toList(), listOf(baseA))
    }

    test("listRecursivelyRecurses") {
      val baseA = base / "a"
      val baseAB = baseA / "b"
      baseA.createDirectory()
      baseAB.writeUtf8("ab")
      val entries = fileSystem.listRecursively(base)
      assertEquals(listOf(baseA, baseAB), entries.toList())
    }

    test("listRecursivelyNoSuchFile") {
      val baseA = base / "a"
      val sequence = fileSystem.listRecursively(baseA)
      assertFailsWith<FileNotFoundException> {
        sequence.first()
      }
    }

    /**
     * Note that this is different from `Files.walk` in java.nio which returns the argument even if
     * it is not a directory.
     */
    test("listRecursivelyNotADirectory") {
      val baseA = base / "a"
      baseA.writeUtf8("a")
      val sequence = fileSystem.listRecursively(baseA)
      val exception = assertFailsWith<IOException> {
        sequence.first()
      }
      assertTrue(exception !is FileNotFoundException)
    }

    test("listRecursivelyIsDepthFirst") {
      val baseA = base / "a"
      val baseB = base / "b"
      val baseA1 = baseA / "1"
      val baseA2 = baseA / "2"
      val baseB1 = baseB / "1"
      val baseB2 = baseB / "2"
      baseA.createDirectory()
      baseB.createDirectory()
      baseA1.writeUtf8("a1")
      baseA2.writeUtf8("a2")
      baseB1.writeUtf8("b1")
      baseB2.writeUtf8("b2")
      val entries = fileSystem.listRecursively(base)
      assertEquals(listOf(baseA, baseA1, baseA2, baseB, baseB1, baseB2), entries.toList())
    }

    test("listRecursivelyIsLazy") {
      val baseA = base / "a"
      val baseB = base / "b"
      baseA.createDirectory()
      baseB.createDirectory()
      val entries = fileSystem.listRecursively(base).iterator()

      // This call will enqueue up the children of base, baseA and baseB.
      assertEquals(baseA, entries.next())
      val baseA1 = baseA / "1"
      val baseA2 = baseA / "2"
      baseA1.writeUtf8("a1")
      baseA2.writeUtf8("a2")

      // This call will enqueue the children of baseA, baseA1 and baseA2.
      assertEquals(baseA1, entries.next())
      assertEquals(baseA2, entries.next())
      assertEquals(baseB, entries.next())

      // This call will enqueue the children of baseB, baseB1 and baseB2.
      val baseB1 = baseB / "1"
      val baseB2 = baseB / "2"
      baseB1.writeUtf8("b1")
      baseB2.writeUtf8("b2")
      assertEquals(baseB1, entries.next())
      assertEquals(baseB2, entries.next())
      assertFalse(entries.hasNext())
    }

    /**
     * This test creates directories that should be listed lazily, and then deletes them! The test
     * wants to confirm that the sequence is resilient to such changes.
     */
    test("listRecursivelySilentlyIgnoresListFailures") {
      val baseA = base / "a"
      val baseB = base / "b"
      baseA.createDirectory()
      baseB.createDirectory()
      val entries = fileSystem.listRecursively(base).iterator()
      assertEquals(baseA, entries.next())
      assertEquals(baseB, entries.next())
      fileSystem.delete(baseA)
      fileSystem.delete(baseB)
      assertFalse(entries.hasNext())
    }

    test("listRecursivelySequenceIterationsAreIndependent") {
      val sequence = fileSystem.listRecursively(base)
      val iterator1 = sequence.iterator()
      assertFalse(iterator1.hasNext())
      val baseA = base / "a"
      baseA.writeUtf8("a")
      val iterator2 = sequence.iterator()
      assertEquals(baseA, iterator2.next())
      assertFalse(iterator2.hasNext())
    }

    test("listRecursivelyFollowsSymlinks") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAA = baseA / "a"
      val baseB = base / "b"
      val baseBA = baseB / "a"
      baseA.createDirectory()
      baseAA.writeUtf8("aa")
      fileSystem.createSymlink(baseB, baseA)

      val sequence = fileSystem.listRecursively(base, followSymlinks = true)
      assertEquals(listOf(baseA, baseAA, baseB, baseBA), sequence.toList())
    }

    test("listRecursivelyDoesNotFollowSymlinks") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAA = baseA / "a"
      val baseB = base / "b"
      baseA.createDirectory()
      baseAA.writeUtf8("aa")
      fileSystem.createSymlink(baseB, baseA)

      val sequence = fileSystem.listRecursively(base, followSymlinks = false)
      assertEquals(listOf(baseA, baseAA, baseB), sequence.toList())
    }

    test("listRecursivelyOnSymlink") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAA = baseA / "a"
      val baseB = base / "b"
      val baseBA = baseB / "a"

      baseA.createDirectory()
      baseAA.writeUtf8("aa")
      fileSystem.createSymlink(baseB, baseA)

      val sequence = fileSystem.listRecursively(baseB, followSymlinks = false)
      assertEquals(listOf(baseBA), sequence.toList())
    }

    test("listRecursiveWithSpecialCharacterNamedFiles") {
      val baseA = base / "ä"
      val baseASuperSaiyan = baseA / "超サイヤ人"
      val baseB = base / "ß"
      val baseBIliad = baseB / "Ἰλιάς"

      baseA.createDirectory()
      baseASuperSaiyan.writeUtf8("カカロットよ！")
      baseB.createDirectory()
      baseBIliad.writeUtf8("μῆνιν ἄειδε θεὰ Πηληϊάδεω Ἀχιλῆος")

      val sequence = fileSystem.listRecursively(base)
      assertEquals(listOf(baseB, baseBIliad, baseA, baseASuperSaiyan), sequence.toList())
    }

    test("listRecursiveOnSymlinkWithSpecialCharacterNamedFiles") {
      if (!supportsSymlink()) return@test

      val baseA = base / "ä"
      val baseASuperSaiyan = baseA / "超サイヤ人"
      val baseB = base / "ß"
      val baseBSuperSaiyan = baseB / "超サイヤ人"

      baseA.createDirectory()
      baseASuperSaiyan.writeUtf8("aa")
      fileSystem.createSymlink(baseB, baseA)

      val sequence = fileSystem.listRecursively(baseB, followSymlinks = false)
      assertEquals(listOf(baseBSuperSaiyan), sequence.toList())
    }

    test("listRecursivelyOnSymlinkCycleThrows") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAB = baseA / "b"
      val baseAC = baseA / "c"

      baseA.createDirectory()
      baseAB.writeUtf8("ab")
      fileSystem.createSymlink(baseAC, baseA)

      val iterator = fileSystem.listRecursively(base, followSymlinks = true).iterator()
      assertEquals(baseA, iterator.next())
      assertEquals(baseAB, iterator.next())
      assertEquals(baseAC, iterator.next())
      val exception = assertFailsWith<IOException> {
        iterator.next() // This would fail because 'c' refers to a path we've already visited.
      }
      assertEquals("symlink cycle at $baseAC", exception.message)
    }

    test("listRecursivelyDoesNotFollowRelativeSymlink") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAA = baseA / "a"
      val baseB = base / "b"
      baseA.createDirectory()
      baseAA.writeUtf8("aa")
      fileSystem.createSymlink(baseB, ".".toPath()) // Symlink to enclosing directory!

      val iterator = fileSystem.listRecursively(base, followSymlinks = true).iterator()
      assertEquals(baseA, iterator.next())
      assertEquals(baseAA, iterator.next())
      assertEquals(baseB, iterator.next())
      val exception = assertFailsWith<IOException> {
        iterator.next()
      }
      assertEquals("symlink cycle at $baseB", exception.message)
    }

    test("fileSourceNoSuchDirectory") {
      assertFailsWith<FileNotFoundException> {
        fileSystem.source(base / "no-such-directory" / "file")
      }
    }

    test("fileSource") {
      val path = base / "file-source"
      path.writeUtf8("hello, world!")

      val source = fileSystem.source(path)
      val buffer = Buffer()
      assertTrue(source.read(buffer, 100L) == 13L)
      assertEquals(-1L, source.read(buffer, 100L))
      assertEquals("hello, world!", buffer.readUtf8())
      source.close()
    }

    test("readPath") {
      val path = base / "read-path"
      val string = "hello, read with a Path"
      path.writeUtf8(string)

      val result = fileSystem.read(path) {
        assertEquals("hello", readUtf8(5))
        assertEquals(", read with ", readUtf8(12))
        assertEquals("a Path", readUtf8())
        return@read "success"
      }
      assertEquals("success", result)
    }

    test("fileSink") {
      val path = base / "file-sink"
      val sink = fileSystem.sink(path)
      val buffer = Buffer().writeUtf8("hello, world!")
      sink.write(buffer, buffer.size)
      sink.close()
      assertTrue(path in fileSystem.list(base))
      assertEquals(0, buffer.size)
      assertEquals("hello, world!", path.readUtf8())
    }

    test("fileSinkWithSpecialCharacterNamedFiles") {
      val path = base / "Ἰλιάς"
      val sink = fileSystem.sink(path)
      val buffer = Buffer().writeUtf8("μῆνιν ἄειδε θεὰ Πηληϊάδεω Ἀχιλῆος")
      sink.write(buffer, buffer.size)
      sink.close()
      assertTrue(path in fileSystem.list(base))
      assertEquals(0, buffer.size)
      assertEquals("μῆνιν ἄειδε θεὰ Πηληϊάδεω Ἀχιλῆος", path.readUtf8())
    }

    test("fileSinkMustCreate") {
      val path = base / "file-sink"
      val sink = fileSystem.sink(path, mustCreate = true)
      val buffer = Buffer().writeUtf8("hello, world!")
      sink.write(buffer, buffer.size)
      sink.close()
      assertTrue(path in fileSystem.list(base))
      assertEquals(0, buffer.size)
      assertEquals("hello, world!", path.readUtf8())
    }

    test("fileSinkMustCreateThrowsIfAlreadyExists") {
      val path = base / "file-sink"
      path.writeUtf8("First!")
      assertFailsWith<IOException> {
        fileSystem.sink(path, mustCreate = true)
      }
    }

    /**
     * Write a file by concatenating three mechanisms, then read it in its entirety using three other
     * mechanisms. This is attempting to defend against unwanted use of Windows text mode.
     *
     * https://docs.microsoft.com/en-us/cpp/c-runtime-library/reference/fopen-wfopen?view=msvc-160
     */
    test("fileSinkSpecialCharacters") {
      val path = base / "file-sink-special-characters"
      val content = "[ctrl-z: \u001A][newline: \n][crlf: \r\n]".encodeUtf8()

      fileSystem.write(path) {
        writeUtf8("FileSystem.write()\n")
        write(content)
      }

      fileSystem.openReadWrite(path).use { handle ->
        handle.sink(fileOffset = handle.size()).buffer().use { sink ->
          sink.writeUtf8("FileSystem.openReadWrite()\n")
          sink.write(content)
        }
      }

      fileSystem.appendingSink(path).buffer().use { sink ->
        sink.writeUtf8("FileSystem.appendingSink()\n")
        sink.write(content)
      }

      fileSystem.read(path) {
        assertEquals("FileSystem.write()", readUtf8LineStrict())
        assertEquals(content, readByteString(content.size.toLong()))
        assertEquals("FileSystem.openReadWrite()", readUtf8LineStrict())
        assertEquals(content, readByteString(content.size.toLong()))
        assertEquals("FileSystem.appendingSink()", readUtf8LineStrict())
        assertEquals(content, readByteString(content.size.toLong()))
        assertTrue(exhausted())
      }

      fileSystem.openReadWrite(path).use { handle ->
        handle.source().buffer().use { source ->
          assertEquals("FileSystem.write()", source.readUtf8LineStrict())
          assertEquals(content, source.readByteString(content.size.toLong()))
          assertEquals("FileSystem.openReadWrite()", source.readUtf8LineStrict())
          assertEquals(content, source.readByteString(content.size.toLong()))
          assertEquals("FileSystem.appendingSink()", source.readUtf8LineStrict())
          assertEquals(content, source.readByteString(content.size.toLong()))
          assertTrue(source.exhausted())
        }
      }

      fileSystem.openReadOnly(path).use { handle ->
        handle.source().buffer().use { source ->
          assertEquals("FileSystem.write()", source.readUtf8LineStrict())
          assertEquals(content, source.readByteString(content.size.toLong()))
          assertEquals("FileSystem.openReadWrite()", source.readUtf8LineStrict())
          assertEquals(content, source.readByteString(content.size.toLong()))
          assertEquals("FileSystem.appendingSink()", source.readUtf8LineStrict())
          assertEquals(content, source.readByteString(content.size.toLong()))
          assertTrue(source.exhausted())
        }
      }
    }

    test("writePath") {
      val path = base / "write-path"
      val content = fileSystem.write(path) {
        val string = "hello, write with a Path"
        writeUtf8(string)
        return@write string
      }
      assertTrue(path in fileSystem.list(base))
      assertEquals(content, path.readUtf8())
    }

    test("writePathMustCreate") {
      val path = base / "write-path"
      val content = fileSystem.write(path, mustCreate = true) {
        val string = "hello, write with a Path"
        writeUtf8(string)
        return@write string
      }
      assertTrue(path in fileSystem.list(base))
      assertEquals(content, path.readUtf8())
    }

    test("writePathMustCreateThrowsIfAlreadyExists") {
      val path = base / "write-path"
      path.writeUtf8("First!")
      assertFailsWith<IOException> {
        fileSystem.write(path, mustCreate = true) {}
      }
    }

    test("appendingSinkAppendsToExistingFile") {
      val path = base / "appending-sink-appends-to-existing-file"
      path.writeUtf8("hello, world!\n")
      val sink = fileSystem.appendingSink(path)
      val buffer = Buffer().writeUtf8("this is added later!")
      sink.write(buffer, buffer.size)
      sink.close()
      assertTrue(path in fileSystem.list(base))
      assertEquals("hello, world!\nthis is added later!", path.readUtf8())
    }

    test("appendingSinkDoesNotImpactExistingFile") {
      if (fileSystem.isFakeFileSystem && !fileSystem.allowReadsWhileWriting) return@test

      val path = base / "appending-sink-does-not-impact-existing-file"
      path.writeUtf8("hello, world!\n")
      val sink = fileSystem.appendingSink(path)
      assertEquals("hello, world!\n", path.readUtf8())
      sink.close()
      assertEquals("hello, world!\n", path.readUtf8())
    }

    test("appendingSinkCreatesNewFile") {
      val path = base / "appending-sink-creates-new-file"
      val sink = fileSystem.appendingSink(path)
      val buffer = Buffer().writeUtf8("this is all there is!")
      sink.write(buffer, buffer.size)
      sink.close()
      assertTrue(path in fileSystem.list(base))
      assertEquals("this is all there is!", path.readUtf8())
    }

    test("appendingSinkExistingFileMustExist") {
      val path = base / "appending-sink-creates-new-file"
      path.writeUtf8("Hey, ")

      val sink = fileSystem.appendingSink(path, mustExist = true)
      val buffer = Buffer().writeUtf8("this is all there is!")
      sink.write(buffer, buffer.size)
      sink.close()
      assertTrue(path in fileSystem.list(base))
      assertEquals("Hey, this is all there is!", path.readUtf8())
    }

    test("appendingSinkMustExistThrowsIfAbsent") {
      val path = base / "appending-sink-creates-new-file"
      assertFailsWith<IOException> {
        fileSystem.appendingSink(path, mustExist = true)
      }
    }

    test("fileSinkFlush") {
      if (fileSystem.isFakeFileSystem && !fileSystem.allowReadsWhileWriting) return@test

      val path = base / "file-sink"
      val sink = fileSystem.sink(path)

      val buffer = Buffer().writeUtf8("hello,")
      sink.write(buffer, buffer.size)
      sink.flush()
      assertEquals("hello,", path.readUtf8())

      buffer.writeUtf8(" world!")
      sink.write(buffer, buffer.size)
      sink.close()
      assertEquals("hello, world!", path.readUtf8())
    }

    test("fileSinkNoSuchDirectory") {
      assertFailsWith<FileNotFoundException> {
        fileSystem.sink(base / "no-such-directory" / "file")
      }
    }

    test("createDirectory") {
      val path = base / "create-directory"
      fileSystem.createDirectory(path)
      assertTrue(path in fileSystem.list(base))
    }

    test("createDirectoryMustCreate") {
      val path = base / "create-directory"
      fileSystem.createDirectory(path, mustCreate = true)
      assertTrue(path in fileSystem.list(base))
    }

    test("createDirectoryAlreadyExists") {
      val path = base / "already-exists"
      fileSystem.createDirectory(path)
      fileSystem.createDirectory(path)
    }

    test("createDirectoryAlreadyExistsMustCreateThrows") {
      val path = base / "already-exists"
      fileSystem.createDirectory(path)
      val exception = assertFailsWith<IOException> {
        fileSystem.createDirectory(path, mustCreate = true)
      }
      assertTrue(exception !is FileNotFoundException)
    }

    test("createDirectoryParentDirectoryDoesNotExist") {
      val path = base / "no-such-directory" / "created"
      assertFailsWith<IOException> {
        fileSystem.createDirectory(path)
      }
    }

    test("createDirectoriesSingle") {
      val path = base / "create-directories-single"
      fileSystem.createDirectories(path)
      assertTrue(path in fileSystem.list(base))
      assertTrue(fileSystem.metadata(path).isDirectory)
    }

    test("createDirectoriesAlreadyExists") {
      val path = base / "already-exists"
      fileSystem.createDirectory(path)
      fileSystem.createDirectories(path)
      assertTrue(fileSystem.metadata(path).isDirectory)
    }

    test("createDirectoriesAlreadyExistsMustCreateThrows") {
      val path = base / "already-exists"
      fileSystem.createDirectory(path)
      val exception = assertFailsWith<IOException> {
        fileSystem.createDirectories(path, mustCreate = true)
      }
      assertTrue(exception !is FileNotFoundException)
      assertTrue(fileSystem.metadata(path).isDirectory)
    }

    test("createDirectoriesParentDirectoryDoesNotExist") {
      fileSystem.createDirectories(base / "a" / "b" / "c")
      assertTrue(base / "a" in fileSystem.list(base))
      assertTrue(base / "a" / "b" in fileSystem.list(base / "a"))
      assertTrue(base / "a" / "b" / "c" in fileSystem.list(base / "a" / "b"))
      assertTrue(fileSystem.metadata(base / "a" / "b" / "c").isDirectory)
    }

    test("createDirectoriesParentIsFile") {
      val file = base / "simple-file"
      file.writeUtf8("just a file")
      assertFailsWith<IOException> {
        fileSystem.createDirectories(file / "child")
      }
    }

    test("atomicMoveFile") {
      val source = base / "source"
      source.writeUtf8("hello, world!")
      val target = base / "target"
      fileSystem.atomicMove(source, target)
      assertEquals("hello, world!", target.readUtf8())
      assertTrue(source !in fileSystem.list(base))
      assertTrue(target in fileSystem.list(base))
    }

    test("atomicMoveDirectory") {
      val source = base / "source"
      fileSystem.createDirectory(source)
      val target = base / "target"
      fileSystem.atomicMove(source, target)
      assertTrue(source !in fileSystem.list(base))
      assertTrue(target in fileSystem.list(base))
    }

    test("atomicMoveSourceIsTarget") {
      val source = base / "source"
      source.writeUtf8("hello, world!")
      fileSystem.atomicMove(source, source)
      assertEquals("hello, world!", source.readUtf8())
      assertTrue(source in fileSystem.list(base))
    }

    test("atomicMoveClobberExistingFile") {
      // `java.io` on Windows doesn't allow file renaming if the target already exists.
      if (isJvmFileSystemOnWindows()) return@test

      val source = base / "source"
      source.writeUtf8("hello, world!")
      val target = base / "target"
      target.writeUtf8("this file will be clobbered!")
      fileSystem.atomicMove(source, target)
      assertEquals("hello, world!", target.readUtf8())
      assertTrue(source !in fileSystem.list(base))
      assertTrue(target in fileSystem.list(base))
    }

    test("atomicMoveSourceDoesNotExist") {
      val source = base / "source"
      val target = base / "target"
      if (fileSystem::class.simpleName == "JvmSystemFileSystem") {
        assertFailsWith<IOException> {
          fileSystem.atomicMove(source, target)
        }
      } else {
        assertFailsWith<FileNotFoundException> {
          fileSystem.atomicMove(source, target)
        }
      }
    }

    test("atomicMoveSourceIsFileAndTargetIsDirectory") {
      val source = base / "source"
      source.writeUtf8("hello, world!")
      val target = base / "target"
      fileSystem.createDirectory(target)

      if (allowAtomicMoveFromFileToDirectory) {
        fileSystem.atomicMove(source, target)
        assertEquals("hello, world!", target.readUtf8())
      } else {
        val exception = assertFailsWith<IOException> {
          fileSystem.atomicMove(source, target)
        }
        assertTrue(exception !is FileNotFoundException)
      }
    }

    test("atomicMoveSourceIsDirectoryAndTargetIsFile") {
      // `java.io` on Windows doesn't allow file renaming if the target already exists.
      if (isJvmFileSystemOnWindows()) return@test

      val source = base / "source"
      fileSystem.createDirectory(source)
      val target = base / "target"
      target.writeUtf8("hello, world!")
      try {
        fileSystem.atomicMove(source, target)
        assertTrue(allowClobberingEmptyDirectories)
      } catch (e: IOException) {
        assertFalse(allowClobberingEmptyDirectories)
      }
    }

    test("copyFile") {
      val source = base / "source"
      source.writeUtf8("hello, world!")
      val target = base / "target"
      fileSystem.copy(source, target)
      assertTrue(target in fileSystem.list(base))
      assertEquals("hello, world!", source.readUtf8())
      assertEquals("hello, world!", target.readUtf8())
    }

    test("copySourceDoesNotExist") {
      val source = base / "source"
      val target = base / "target"
      assertFailsWith<FileNotFoundException> {
        fileSystem.copy(source, target)
      }
      assertFalse(target in fileSystem.list(base))
    }

    test("copyTargetIsClobbered") {
      val source = base / "source"
      source.writeUtf8("hello, world!")
      val target = base / "target"
      target.writeUtf8("this file will be clobbered!")
      fileSystem.copy(source, target)
      assertTrue(target in fileSystem.list(base))
      assertEquals("hello, world!", target.readUtf8())
    }

    test("deleteFile") {
      val path = base / "delete-file"
      path.writeUtf8("delete me")
      fileSystem.delete(path)
      assertTrue(path !in fileSystem.list(base))
    }

    test("deleteFileMustExist") {
      val path = base / "delete-file"
      path.writeUtf8("delete me")
      fileSystem.delete(path, mustExist = true)
      assertTrue(path !in fileSystem.list(base))
    }

    test("deleteEmptyDirectory") {
      val path = base / "delete-empty-directory"
      fileSystem.createDirectory(path)
      fileSystem.delete(path)
      assertTrue(path !in fileSystem.list(base))
    }

    test("deleteEmptyDirectoryMustExist") {
      val path = base / "delete-empty-directory"
      fileSystem.createDirectory(path)
      fileSystem.delete(path, mustExist = true)
      assertTrue(path !in fileSystem.list(base))
    }

    test("deleteDoesNotExist") {
      val path = base / "no-such-file"
      fileSystem.delete(path)
    }

    test("deleteFailsOnNoSuchFileIfMustExist") {
      val path = base / "no-such-file"
      assertFailsWith<FileNotFoundException> {
        fileSystem.delete(path, mustExist = true)
      }
    }

    test("deleteFailsOnNonEmptyDirectory") {
      val path = base / "non-empty-directory"
      fileSystem.createDirectory(path)
      (path / "file.txt").writeUtf8("inside directory")
      val exception = assertFailsWith<IOException> {
        fileSystem.delete(path)
      }
      assertTrue(exception !is FileNotFoundException)
    }

    test("deleteFailsOnNonEmptyDirectoryMustExist") {
      val path = base / "non-empty-directory"
      fileSystem.createDirectory(path)
      (path / "file.txt").writeUtf8("inside directory")
      val exception = assertFailsWith<IOException> {
        fileSystem.delete(path, mustExist = true)
      }
      assertTrue(exception !is FileNotFoundException)
    }

    test("deleteRecursivelyFile") {
      val path = base / "delete-recursively-file"
      path.writeUtf8("delete me")
      fileSystem.deleteRecursively(path)
      assertTrue(path !in fileSystem.list(base))
    }

    test("deleteRecursivelyFileMustExist") {
      val path = base / "delete-recursively-file"
      path.writeUtf8("delete me")
      fileSystem.deleteRecursively(path, mustExist = true)
      assertTrue(path !in fileSystem.list(base))
    }

    test("deleteRecursivelyEmptyDirectory") {
      val path = base / "delete-recursively-empty-directory"
      fileSystem.createDirectory(path)
      fileSystem.deleteRecursively(path)
      assertTrue(path !in fileSystem.list(base))
    }

    test("deleteRecursivelyEmptyDirectoryMustExist") {
      val path = base / "delete-recursively-empty-directory"
      fileSystem.createDirectory(path)
      fileSystem.deleteRecursively(path, mustExist = true)
      assertTrue(path !in fileSystem.list(base))
    }

    test("deleteRecursivelyNoSuchFile") {
      val path = base / "no-such-file"
      fileSystem.deleteRecursively(path)
    }

    test("deleteRecursivelyMustExistFailsOnNoSuchFile") {
      val path = base / "no-such-file"
      assertFailsWith<IOException> {
        fileSystem.deleteRecursively(path, mustExist = true)
      }
    }

    test("deleteRecursivelyNonEmptyDirectory") {
      val path = base / "delete-recursively-non-empty-directory"
      fileSystem.createDirectory(path)
      (path / "file.txt").writeUtf8("inside directory")
      fileSystem.deleteRecursively(path)
      assertTrue(path !in fileSystem.list(base))
      assertTrue((path / "file.txt") !in fileSystem.list(base))
    }

    test("deleteRecursivelyNonEmptyDirectoryMustExist") {
      val path = base / "delete-recursively-non-empty-directory"
      fileSystem.createDirectory(path)
      (path / "file.txt").writeUtf8("inside directory")
      fileSystem.deleteRecursively(path, mustExist = true)
      assertTrue(path !in fileSystem.list(base))
      assertTrue((path / "file.txt") !in fileSystem.list(base))
    }

    test("deleteRecursivelyDeepHierarchy") {
      fileSystem.createDirectory(base / "a")
      fileSystem.createDirectory(base / "a" / "b")
      fileSystem.createDirectory(base / "a" / "b" / "c")
      (base / "a" / "b" / "c" / "d.txt").writeUtf8("inside deep hierarchy")
      fileSystem.deleteRecursively(base / "a")
      assertEquals(listOf(), fileSystem.list(base))
    }

    test("deleteRecursivelyDeepHierarchyMustExist") {
      fileSystem.createDirectory(base / "a")
      fileSystem.createDirectory(base / "a" / "b")
      fileSystem.createDirectory(base / "a" / "b" / "c")
      (base / "a" / "b" / "c" / "d.txt").writeUtf8("inside deep hierarchy")
      fileSystem.deleteRecursively(base / "a", mustExist = true)
      assertEquals(listOf(), fileSystem.list(base))
    }

    test("deleteRecursivelyOnSymlinkToFileDeletesOnlyThatSymlink") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseB = base / "b"
      baseB.writeUtf8("b")
      fileSystem.createSymlink(baseA, baseB)
      fileSystem.deleteRecursively(baseA)
      assertEquals("b", baseB.readUtf8())
    }

    test("deleteRecursivelyOnSymlinkToFileDeletesOnlyThatSymlinkMustExist") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseB = base / "b"
      baseB.writeUtf8("b")
      fileSystem.createSymlink(baseA, baseB)
      fileSystem.deleteRecursively(baseA, mustExist = true)
      assertEquals("b", baseB.readUtf8())
    }

    test("deleteRecursivelyOnSymlinkToDirectoryDeletesOnlyThatSymlink") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseB = base / "b"
      val baseBC = base / "b" / "c"
      fileSystem.createDirectory(baseB)
      baseBC.writeUtf8("c")
      fileSystem.createSymlink(baseA, baseB)
      fileSystem.deleteRecursively(baseA)
      assertEquals("c", baseBC.readUtf8())
    }

    test("deleteRecursivelyOnSymlinkToDirectoryDeletesOnlyThatSymlinkMustExist") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseB = base / "b"
      val baseBC = base / "b" / "c"
      fileSystem.createDirectory(baseB)
      baseBC.writeUtf8("c")
      fileSystem.createSymlink(baseA, baseB)
      fileSystem.deleteRecursively(baseA, mustExist = true)
      assertEquals("c", baseBC.readUtf8())
    }

    test("deleteRecursivelyOnSymlinkCycleSucceeds") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAB = baseA / "b"
      val baseAC = baseA / "c"

      baseA.createDirectory()
      baseAB.writeUtf8("ab")
      fileSystem.createSymlink(baseAC, baseA)

      fileSystem.deleteRecursively(base)
      assertFalse(fileSystem.exists(base))
    }

    test("deleteRecursivelyOnSymlinkCycleSucceedsMustExist") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAB = baseA / "b"
      val baseAC = baseA / "c"

      baseA.createDirectory()
      baseAB.writeUtf8("ab")
      fileSystem.createSymlink(baseAC, baseA)

      fileSystem.deleteRecursively(base, mustExist = true)
      assertFalse(fileSystem.exists(base))
    }

    test("deleteRecursivelyOnSymlinkToEnclosingDirectorySucceeds") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      fileSystem.createSymlink(baseA, ".".toPath())

      fileSystem.deleteRecursively(baseA)
      assertFalse(fileSystem.exists(baseA))
      assertTrue(fileSystem.exists(base))
    }

    test("deleteRecursivelyOnSymlinkToEnclosingDirectorySucceedsMustExist") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      fileSystem.createSymlink(baseA, ".".toPath())

      fileSystem.deleteRecursively(baseA, mustExist = true)
      assertFalse(fileSystem.exists(baseA))
      assertTrue(fileSystem.exists(base))
    }

    test("fileMetadata") {
      val minTime = clock.now()
      val path = base / "file-metadata"
      path.writeUtf8("hello, world!")
      val maxTime = clock.now()

      val metadata = fileSystem.metadata(path)
      assertTrue(metadata.isRegularFile)
      assertFalse(metadata.isDirectory)
      assertEquals(13, metadata.size)
      assertInRange(metadata.createdAt, minTime, maxTime)
      assertInRange(metadata.lastModifiedAt, minTime, maxTime)
      assertInRange(metadata.lastAccessedAt, minTime, maxTime)
    }

    /** https://github.com/lysine-dev/okio/issues/1755 */
    test("fileMetadataTimestampsAreDistinct") {
      if (fileSystem.isFakeFileSystem) return@test
      if (fileSystem is ForwardingFileSystem) return@test
      if (isJimFileSystem()) return@test
      if (!fileSystemHasGoodMetadata) return@test

      // These timestamps are hardcoded in the following Gradle tasks:
      //   :okio-testing-support:touchAbstractFileSystemTestFilesCreatedAt
      //   :okio-testing-support:touchAbstractFileSystemTestFilesModifiedAt
      val createdAt = fromIso8601String("2026-01-01T01:01:01Z")
      val lastModifiedAt = fromIso8601String("2026-02-02T02:02:02Z")

      val path = okioRoot / "okio-testing-support" / "build/AbstractFileSystemTestFiles/metadata.txt"
      val metadata = fileSystem.metadata(path)
      assertTrue(metadata.isRegularFile)
      assertInRange(metadata.createdAt, createdAt, createdAt)
      assertInRange(metadata.lastModifiedAt, lastModifiedAt, lastModifiedAt)
    }

    test("directoryMetadata") {
      val minTime = clock.now()
      val path = base / "directory-metadata"
      fileSystem.createDirectory(path)
      val maxTime = clock.now()

      val metadata = fileSystem.metadata(path)
      assertFalse(metadata.isRegularFile)
      assertTrue(metadata.isDirectory)
      // Note that the size check is omitted; we'd expect null but the JVM returns values like 64.
      assertInRange(metadata.createdAt, minTime, maxTime)
      assertInRange(metadata.lastModifiedAt, minTime, maxTime)
      assertInRange(metadata.lastAccessedAt, minTime, maxTime)
    }

    test("fileMetadataWithSpecialCharacterNamedFiles") {
      val minTime = clock.now()
      val path = base / "超サイヤ人"
      path.writeUtf8("カカロットよ！")
      val maxTime = clock.now()

      val metadata = fileSystem.metadata(path)
      assertTrue(metadata.isRegularFile)
      assertFalse(metadata.isDirectory)
      assertEquals(21, metadata.size)
      assertInRange(metadata.createdAt, minTime, maxTime)
      assertInRange(metadata.lastModifiedAt, minTime, maxTime)
      assertInRange(metadata.lastAccessedAt, minTime, maxTime)
    }

    test("directoryMetadataWithSpecialCharacterNamedFiles") {
      val minTime = clock.now()
      val path = base / "Ἰλιάς"
      fileSystem.createDirectory(path)
      val maxTime = clock.now()

      val metadata = fileSystem.metadata(path)
      assertFalse(metadata.isRegularFile)
      assertTrue(metadata.isDirectory)
      // Note that the size check is omitted; we'd expect null but the JVM returns values like 64.
      assertInRange(metadata.createdAt, minTime, maxTime)
      assertInRange(metadata.lastModifiedAt, minTime, maxTime)
      assertInRange(metadata.lastAccessedAt, minTime, maxTime)
    }

    test("absentMetadataOrNull") {
      val path = base / "no-such-file"
      assertNull(fileSystem.metadataOrNull(path))
    }

    test("absentParentDirectoryMetadataOrNull") {
      val path = base / "no-such-directory" / "no-such-file"
      assertNull(fileSystem.metadataOrNull(path))
    }

    test("parentDirectoryIsFileMetadataOrNull") {
      val parent = base / "regular-file"
      val path = parent / "no-such-file"
      parent.writeUtf8("just a regular file")
      // This returns null on Windows and throws IOException on other platforms.
      try {
        assertNull(fileSystem.metadataOrNull(path))
      } catch (e: IOException) {
        // Also okay.
      }
    }

    test("inaccessibleMetadata", testConfig = TestConfig.disable()) {
      // TODO(swankjesse): configure a test directory in CI that exists, but that this process doesn't
      //     have permission to read metadata of. Perhaps a file in another user's /home directory?
    }

    test("absentMetadata") {
      val path = base / "no-such-file"
      assertFailsWith<FileNotFoundException> {
        fileSystem.metadata(path)
      }
    }

    test("fileExists") {
      val path = base / "file-exists"
      assertFalse(fileSystem.exists(path))
      path.writeUtf8("hello, world!")
      assertTrue(fileSystem.exists(path))
    }

    test("directoryExists") {
      val path = base / "directory-exists"
      assertFalse(fileSystem.exists(path))
      fileSystem.createDirectory(path)
      assertTrue(fileSystem.exists(path))
    }

    test("deleteOpenForWritingFailsOnWindows") {
      val file = base / "file.txt"
      expectIOExceptionOnWindows(exceptJs = true) {
        fileSystem.sink(file).use {
          fileSystem.delete(file)
        }
      }
    }

    test("deleteOpenForReadingFailsOnWindows") {
      val file = base / "file.txt"
      file.writeUtf8("abc")
      expectIOExceptionOnWindows(exceptJs = true) {
        fileSystem.source(file).use {
          fileSystem.delete(file)
        }
      }
    }

    test("renameSourceIsOpenFailsOnWindows") {
      val from = base / "from.txt"
      val to = base / "to.txt"
      from.writeUtf8("source file")
      to.writeUtf8("target file")
      expectIOExceptionOnWindows(exceptJs = true) {
        fileSystem.source(from).use {
          fileSystem.atomicMove(from, to)
        }
      }
    }

    test("renameTargetIsOpenFailsOnWindows") {
      val from = base / "from.txt"
      val to = base / "to.txt"
      from.writeUtf8("source file")
      to.writeUtf8("target file")

      val expectCrash = !allowRenameWhenTargetIsOpen
      try {
        fileSystem.source(to).use {
          fileSystem.atomicMove(from, to)
        }
        assertFalse(expectCrash)
      } catch (_: IOException) {
        assertTrue(expectCrash)
      }
    }

    test("deleteContentsOfParentOfFileOpenForReadingFailsOnWindows") {
      val parentA = (base / "a")
      fileSystem.createDirectory(parentA)
      val parentAB = parentA / "b"
      fileSystem.createDirectory(parentAB)
      val parentABC = parentAB / "c"
      fileSystem.createDirectory(parentABC)
      val file = parentABC / "file.txt"
      file.writeUtf8("child file")
      expectIOExceptionOnWindows {
        fileSystem.source(file).use {
          fileSystem.delete(file)
          fileSystem.delete(parentABC)
          fileSystem.delete(parentAB)
          fileSystem.delete(parentA)
        }
      }
    }

    test("fileHandleWriteAndRead") {
      val path = base / "file-handle-write-and-read"
      fileSystem.openReadWrite(path).use { handle ->

        handle.sink().buffer().use { sink ->
          sink.writeUtf8("abcdefghijklmnop")
        }

        handle.source().buffer().use { source ->
          assertEquals("abcde", source.readUtf8(5))
          assertEquals("fghijklmnop", source.readUtf8())
        }
      }
    }

    test("fileHandleWriteAndOverwrite") {
      val path = base / "file-handle-write-and-overwrite"
      fileSystem.openReadWrite(path).use { handle ->

        handle.sink().buffer().use { sink ->
          sink.writeUtf8("abcdefghij")
        }

        handle.sink(fileOffset = handle.size() - 3).buffer().use { sink ->
          sink.writeUtf8("HIJKLMNOP")
        }

        handle.source().buffer().use { source ->
          assertEquals("abcdefgHIJKLMNOP", source.readUtf8())
        }
      }
    }

    test("fileHandleWriteBeyondEnd") {
      val path = base / "file-handle-write-beyond-end"
      fileSystem.openReadWrite(path).use { handle ->

        handle.sink(fileOffset = 10).buffer().use { sink ->
          sink.writeUtf8("klmnop")
        }

        handle.source().buffer().use { source ->
          assertEquals("00000000000000000000", source.readByteString(10).hex())
          assertEquals("klmnop", source.readUtf8())
        }
      }
    }

    test("fileHandleResizeSmaller") {
      val path = base / "file-handle-resize-smaller"
      fileSystem.openReadWrite(path).use { handle ->

        handle.sink().buffer().use { sink ->
          sink.writeUtf8("abcdefghijklmnop")
        }

        handle.resize(10)

        handle.source().buffer().use { source ->
          assertEquals("abcdefghij", source.readUtf8())
        }
      }
    }

    test("fileHandleResizeLarger") {
      val path = base / "file-handle-resize-larger"
      fileSystem.openReadWrite(path).use { handle ->

        handle.sink().buffer().use { sink ->
          sink.writeUtf8("abcde")
        }

        handle.resize(15)

        handle.source().buffer().use { source ->
          assertEquals("abcde", source.readUtf8(5))
          assertEquals("00000000000000000000", source.readByteString().hex())
        }
      }
    }

    test("fileHandleFlush") {
      if (windowsLimitations) return@test // Open for reading and writing simultaneously.

      val path = base / "file-handle-flush"
      fileSystem.openReadWrite(path).use { handleA ->
        handleA.sink().buffer().use { sink ->
          sink.writeUtf8("abcde")
        }
        handleA.flush()

        fileSystem.openReadWrite(path).use { handleB ->
          handleB.source().buffer().use { source ->
            assertEquals("abcde", source.readUtf8())
          }
        }
      }
    }

    test("fileHandleLargeBufferedWriteAndRead") {
      if (isBrowser()) return@test // This test errors on browsers in CI.

      val data = randomBytes(1024 * 1024 * 8)

      val path = base / "file-handle-large-buffered-write-and-read"
      fileSystem.openReadWrite(path).use { handle ->
        handle.sink().buffer().use { sink ->
          sink.write(data)
        }
      }

      fileSystem.openReadWrite(path).use { handle ->
        handle.source().buffer().use { source ->
          assertEquals(data, source.readByteString())
        }
      }
    }

    test("fileHandleLargeArrayWriteAndRead") {
      if (isBrowser()) return@test // This test errors on browsers in CI.

      val path = base / "file-handle-large-array-write-and-read"

      val writtenBytes = randomBytes(1024 * 1024 * 8)
      fileSystem.openReadWrite(path).use { handle ->
        handle.write(0, writtenBytes.toByteArray(), 0, writtenBytes.size)
      }

      val readBytes = fileSystem.openReadWrite(path).use { handle ->
        val byteArray = ByteArray(writtenBytes.size)
        handle.read(0, byteArray, 0, byteArray.size)
        return@use byteArray.toByteString(0, byteArray.size) // Parameters necessary for issue 910.
      }

      assertEquals(writtenBytes, readBytes)
    }

    test("fileHandleEmptyArrayWriteAndRead") {
      val path = base / "file-handle-empty-array-write-and-read"

      val writtenBytes = ByteArray(0)
      fileSystem.openReadWrite(path).use { handle ->
        handle.write(0, writtenBytes, 0, writtenBytes.size)
      }

      val readBytes = fileSystem.openReadWrite(path).use { handle ->
        val byteArray = ByteArray(writtenBytes.size)
        handle.read(0, byteArray, 0, byteArray.size)
        return@use byteArray
      }

      assertContentEquals(writtenBytes, readBytes)
    }

    test("fileHandleSinkPosition") {
      val path = base / "file-handle-sink-position"

      fileSystem.openReadWrite(path).use { handle ->
        handle.sink().use { sink ->
          sink.write(Buffer().writeUtf8("abcde"), 5)
          assertEquals(5, handle.position(sink))
          sink.write(Buffer().writeUtf8("fghijklmno"), 10)
          assertEquals(15, handle.position(sink))
        }

        handle.sink(200).use { sink ->
          sink.write(Buffer().writeUtf8("abcde"), 5)
          assertEquals(205, handle.position(sink))
          sink.write(Buffer().writeUtf8("fghijklmno"), 10)
          assertEquals(215, handle.position(sink))
        }
      }
    }

    test("fileHandleBufferedSinkPosition") {
      val path = base / "file-handle-buffered-sink-position"

      fileSystem.openReadWrite(path).use { handle ->
        handle.sink().buffer().use { sink ->
          sink.writeUtf8("abcde")
          assertEquals(5, handle.position(sink))
          sink.writeUtf8("fghijklmno")
          assertEquals(15, handle.position(sink))
        }

        handle.sink(200).buffer().use { sink ->
          sink.writeUtf8("abcde")
          assertEquals(205, handle.position(sink))
          sink.writeUtf8("fghijklmno")
          assertEquals(215, handle.position(sink))
        }
      }
    }

    test("fileHandleSinkReposition") {
      val path = base / "file-handle-sink-reposition"

      fileSystem.openReadWrite(path).use { handle ->
        handle.sink().use { sink ->
          sink.write(Buffer().writeUtf8("abcdefghij"), 10)
          handle.reposition(sink, 5)
          assertEquals(5, handle.position(sink))
          sink.write(Buffer().writeUtf8("KLM"), 3)
          assertEquals(8, handle.position(sink))

          handle.reposition(sink, 200)
          sink.write(Buffer().writeUtf8("ABCDEFGHIJ"), 10)
          handle.reposition(sink, 205)
          assertEquals(205, handle.position(sink))
          sink.write(Buffer().writeUtf8("klm"), 3)
          assertEquals(208, handle.position(sink))
        }

        Buffer().also {
          handle.read(fileOffset = 0, sink = it, byteCount = 10)
          assertEquals("abcdeKLMij", it.readUtf8())
        }

        Buffer().also {
          handle.read(fileOffset = 200, sink = it, byteCount = 15)
          assertEquals("ABCDEklmIJ", it.readUtf8())
        }
      }
    }

    test("fileHandleBufferedSinkReposition") {
      val path = base / "file-handle-buffered-sink-reposition"

      fileSystem.openReadWrite(path).use { handle ->
        handle.sink().buffer().use { sink ->
          sink.write(Buffer().writeUtf8("abcdefghij"), 10)
          handle.reposition(sink, 5)
          assertEquals(5, handle.position(sink))
          sink.write(Buffer().writeUtf8("KLM"), 3)
          assertEquals(8, handle.position(sink))

          handle.reposition(sink, 200)
          sink.write(Buffer().writeUtf8("ABCDEFGHIJ"), 10)
          handle.reposition(sink, 205)
          assertEquals(205, handle.position(sink))
          sink.write(Buffer().writeUtf8("klm"), 3)
          assertEquals(208, handle.position(sink))
        }

        Buffer().also {
          handle.read(fileOffset = 0, sink = it, byteCount = 10)
          assertEquals("abcdeKLMij", it.readUtf8())
        }

        Buffer().also {
          handle.read(fileOffset = 200, sink = it, byteCount = 15)
          assertEquals("ABCDEklmIJ", it.readUtf8())
        }
      }
    }

    test("fileHandleSourceHappyPath") {
      val path = base / "file-handle-source"
      fileSystem.write(path) {
        writeUtf8("abcdefghijklmnop")
      }

      fileSystem.openReadOnly(path).use { handle ->
        assertEquals(16L, handle.size())
        val buffer = Buffer()

        handle.source().use { source ->
          assertEquals(0L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals("abcd", buffer.readUtf8())
          assertEquals(4L, handle.position(source))
        }

        handle.source(fileOffset = 8L).use { source ->
          assertEquals(8L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals("ijkl", buffer.readUtf8())
          assertEquals(12L, handle.position(source))
        }

        handle.source(fileOffset = 16L).use { source ->
          assertEquals(16L, handle.position(source))
          assertEquals(-1L, source.read(buffer, 4L))
          assertEquals("", buffer.readUtf8())
          assertEquals(16L, handle.position(source))
        }
      }
    }

    test("fileHandleSourceReposition") {
      val path = base / "file-handle-source-reposition"
      fileSystem.write(path) {
        writeUtf8("abcdefghijklmnop")
      }

      fileSystem.openReadOnly(path).use { handle ->
        assertEquals(16L, handle.size())
        val buffer = Buffer()

        handle.source().use { source ->
          handle.reposition(source, 12L)
          assertEquals(12L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals("mnop", buffer.readUtf8())
          assertEquals(-1L, source.read(buffer, 4L))
          assertEquals("", buffer.readUtf8())
          assertEquals(16L, handle.position(source))

          handle.reposition(source, 0L)
          assertEquals(0L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals("abcd", buffer.readUtf8())
          assertEquals(4L, handle.position(source))

          handle.reposition(source, 8L)
          assertEquals(8L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals("ijkl", buffer.readUtf8())
          assertEquals(12L, handle.position(source))

          handle.reposition(source, 16L)
          assertEquals(16L, handle.position(source))
          assertEquals(-1L, source.read(buffer, 4L))
          assertEquals("", buffer.readUtf8())
          assertEquals(16L, handle.position(source))
        }
      }
    }

    test("fileHandleBufferedSourceReposition") {
      val path = base / "file-handle-buffered-source-reposition"
      fileSystem.write(path) {
        writeUtf8("abcdefghijklmnop")
      }

      fileSystem.openReadOnly(path).use { handle ->
        assertEquals(16L, handle.size())
        val buffer = Buffer()

        handle.source().buffer().use { source ->
          handle.reposition(source, 12L)
          assertEquals(0L, source.buffer.size)
          assertEquals(12L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals(0L, source.buffer.size)
          assertEquals("mnop", buffer.readUtf8())
          assertEquals(-1L, source.read(buffer, 4L))
          assertEquals("", buffer.readUtf8())
          assertEquals(16L, handle.position(source))

          handle.reposition(source, 0L)
          assertEquals(0L, source.buffer.size)
          assertEquals(0L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals(12L, source.buffer.size) // Buffered bytes accumulated.
          assertEquals("abcd", buffer.readUtf8())
          assertEquals(4L, handle.position(source))

          handle.reposition(source, 8L)
          assertEquals(8L, source.buffer.size) // Buffered bytes preserved.
          assertEquals(8L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals(4L, source.buffer.size)
          assertEquals("ijkl", buffer.readUtf8())
          assertEquals(12L, handle.position(source))

          handle.reposition(source, 16L)
          assertEquals(0L, source.buffer.size)
          assertEquals(16L, handle.position(source))
          assertEquals(-1L, source.read(buffer, 4L))
          assertEquals(0L, source.buffer.size)
          assertEquals("", buffer.readUtf8())
          assertEquals(16L, handle.position(source))
        }
      }
    }

    test("fileHandleSourceSeekBackwards") {
      val path = base / "file-handle-source-backwards"
      fileSystem.write(path) {
        writeUtf8("abcdefghijklmnop")
      }
      fileSystem.openReadOnly(path).use { handle ->
        assertEquals(16L, handle.size())
        val buffer = Buffer()

        handle.source().use { source ->
          assertEquals(0L, handle.position(source))
          assertEquals(16L, source.read(buffer, 16L))
          assertEquals("abcdefghijklmnop", buffer.readUtf8())
          assertEquals(16L, handle.position(source))
        }

        handle.source(0L).use { source ->
          assertEquals(0L, handle.position(source))
          assertEquals(16L, source.read(buffer, 16L))
          assertEquals("abcdefghijklmnop", buffer.readUtf8())
          assertEquals(16L, handle.position(source))
        }
      }
    }

    test("bufferedFileHandleSourceHappyPath") {
      val path = base / "file-handle-source"
      fileSystem.write(path) {
        writeUtf8("abcdefghijklmnop")
      }

      fileSystem.openReadOnly(path).use { handle ->
        assertEquals(16L, handle.size())
        val buffer = Buffer()

        handle.source().buffer().use { source ->
          assertEquals(0L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals("abcd", buffer.readUtf8())
          assertEquals(4L, handle.position(source))
        }

        handle.source(fileOffset = 8L).buffer().use { source ->
          assertEquals(8L, handle.position(source))
          assertEquals(4L, source.read(buffer, 4L))
          assertEquals("ijkl", buffer.readUtf8())
          assertEquals(12L, handle.position(source))
        }

        handle.source(fileOffset = 16L).buffer().use { source ->
          assertEquals(16L, handle.position(source))
          assertEquals(-1L, source.read(buffer, 4L))
          assertEquals("", buffer.readUtf8())
          assertEquals(16L, handle.position(source))
        }
      }
    }

    test("bufferedFileHandleSourceSeekBackwards") {
      val path = base / "file-handle-source-backwards"
      fileSystem.write(path) {
        writeUtf8("abcdefghijklmnop")
      }
      fileSystem.openReadOnly(path).use { handle ->
        assertEquals(16L, handle.size())
        val buffer = Buffer()

        handle.source().buffer().use { source ->
          assertEquals(0L, handle.position(source))
          assertEquals(16L, source.read(buffer, 16L))
          assertEquals("abcdefghijklmnop", buffer.readUtf8())
          assertEquals(16L, handle.position(source))
        }

        handle.source(0L).buffer().use { source ->
          assertEquals(0L, handle.position(source))
          assertEquals(16L, source.read(buffer, 16L))
          assertEquals("abcdefghijklmnop", buffer.readUtf8())
          assertEquals(16L, handle.position(source))
        }
      }
    }

    test("openReadOnlyThrowsOnAttemptToWrite") {
      val path = base / "file-handle-source"
      fileSystem.write(path) {
        writeUtf8("abcdefghijklmnop")
      }

      fileSystem.openReadOnly(path).use { handle ->
        try {
          handle.sink()
          fail()
        } catch (_: IllegalStateException) {
        }

        try {
          handle.flush()
          fail()
        } catch (_: IllegalStateException) {
        }

        try {
          handle.resize(0L)
          fail()
        } catch (_: IllegalStateException) {
        }

        try {
          handle.write(0L, Buffer().writeUtf8("hello"), 5L)
          fail()
        } catch (_: IllegalStateException) {
        }
      }
    }

    test("openReadOnlyFailsOnAbsentFile") {
      val path = base / "file-handle-source"

      try {
        fileSystem.openReadOnly(path)
        fail()
      } catch (_: IOException) {
      }
    }

    test("openReadWriteCreatesAbsentFile") {
      val path = base / "file-handle-source"

      fileSystem.openReadWrite(path).use {
      }

      assertEquals("", path.readUtf8())
    }

    test("openReadWriteCreatesAbsentFileMustCreate") {
      val path = base / "file-handle-source"

      fileSystem.openReadWrite(path, mustCreate = true).use {
      }

      assertEquals("", path.readUtf8())
    }

    test("openReadWriteMustCreateThrowsIfAlreadyExists") {
      val path = base / "file-handle-source"
      path.writeUtf8("First!")

      assertFailsWith<IOException> {
        fileSystem.openReadWrite(path, mustCreate = true).use {}
      }
    }

    test("openReadWriteMustExist") {
      val path = base / "file-handle-source"
      path.writeUtf8("one")

      fileSystem.openReadWrite(path, mustExist = true).use { handle ->
        handle.write(3L, Buffer().writeUtf8(" two"), 4L)
      }

      assertEquals("one two", path.readUtf8())
    }

    test("openReadWriteMustExistThrowsIfAbsent") {
      val path = base / "file-handle-source"

      assertFailsWith<IOException> {
        fileSystem.openReadWrite(path, mustExist = true).use {}
      }
    }

    test("openReadWriteThrowsIfBothMustCreateAndMustExist") {
      val path = base / "file-handle-source"

      assertFailsWith<IllegalArgumentException> {
        fileSystem.openReadWrite(path, mustCreate = true, mustExist = true).use {}
      }
    }

    test("sinkPositionFailsAfterClose") {
      val path = base / "sink-position-fails-after-close"

      fileSystem.openReadWrite(path).use { handle ->
        val sink = handle.sink()
        sink.close()
        try {
          handle.position(sink)
          fail()
        } catch (_: IllegalStateException) {
        }
        try {
          handle.position(sink.buffer())
          fail()
        } catch (_: IllegalStateException) {
        }
      }
    }

    test("sinkRepositionFailsAfterClose") {
      val path = base / "sink-reposition-fails-after-close"

      fileSystem.openReadWrite(path).use { handle ->
        val sink = handle.sink()
        sink.close()
        try {
          handle.reposition(sink, 1L)
          fail()
        } catch (_: IllegalStateException) {
        }
        try {
          handle.reposition(sink.buffer(), 1L)
          fail()
        } catch (_: IllegalStateException) {
        }
      }
    }

    test("sourcePositionFailsAfterClose") {
      val path = base / "source-position-fails-after-close"

      fileSystem.openReadWrite(path).use { handle ->
        val source = handle.source()
        source.close()
        try {
          handle.position(source)
          fail()
        } catch (_: IllegalStateException) {
        }
        try {
          handle.position(source.buffer())
          fail()
        } catch (_: IllegalStateException) {
        }
      }
    }

    test("sourceRepositionFailsAfterClose") {
      val path = base / "source-reposition-fails-after-close"

      fileSystem.openReadWrite(path).use { handle ->
        val source = handle.source()
        source.close()
        try {
          handle.reposition(source, 1L)
          fail()
        } catch (_: IllegalStateException) {
        }
        try {
          handle.reposition(source.buffer(), 1L)
          fail()
        } catch (_: IllegalStateException) {
        }
      }
    }

    test("sizeFailsAfterClose") {
      val path = base / "size-fails-after-close"

      val handle = fileSystem.openReadWrite(path)
      handle.close()
      try {
        handle.size()
        fail()
      } catch (_: IllegalStateException) {
      }
    }

    test("absoluteSymlinkMetadata") {
      if (!supportsSymlink()) return@test

      val target = base / "symlink-target"
      val source = base / "symlink-source"

      val minTime = clock.now()
      fileSystem.createSymlink(source, target)
      val maxTime = clock.now()

      val sourceMetadata = fileSystem.metadata(source)
      // Okio's WasiFileSystem only creates relative symlinks.
      assertEquals(
        when {
          isWasiFileSystem -> target.relativeTo(source.parent!!)
          else -> target
        },
        sourceMetadata.symlinkTarget,
      )
      assertInRange(sourceMetadata.createdAt, minTime, maxTime)
    }

    test("relativeSymlinkMetadata") {
      if (!supportsSymlink()) return@test

      val target = "symlink-target".toPath()
      val source = base / "symlink-source"

      val minTime = clock.now()
      fileSystem.createSymlink(source, target)
      val maxTime = clock.now()

      val sourceMetadata = fileSystem.metadata(source)
      assertEquals(target, sourceMetadata.symlinkTarget)
      assertInRange(sourceMetadata.createdAt, minTime, maxTime)
    }

    test("createSymlinkSourceAlreadyExists") {
      if (!supportsSymlink()) return@test

      val target = base / "symlink-target"
      val source = base / "symlink-source"
      source.writeUtf8("hello")
      val exception = assertFailsWith<IOException> {
        fileSystem.createSymlink(source, target)
      }
      assertTrue(exception !is FileNotFoundException)
    }

    test("createSymlinkParentDirectoryDoesNotExist") {
      if (!supportsSymlink()) return@test

      val source = base / "no-such-directory" / "source"
      val target = base / "target"
      val e = assertFailsWith<IOException> {
        fileSystem.createSymlink(source, target)
      }
      assertTrue(e !is FileNotFoundException)
    }

    test("openSymlinkSource") {
      if (!supportsSymlink()) return@test

      val target = base / "symlink-target"
      val source = base / "symlink-source"
      fileSystem.createSymlink(source, target)
      target.writeUtf8("I am the target file")
      val sourceContent = fileSystem.source(source).buffer().use { it.readUtf8() }
      assertEquals("I am the target file", sourceContent)
    }

    test("openSymlinkSink") {
      if (!supportsSymlink()) return@test
      if (isJimFileSystem()) return@test

      val target = base / "symlink-target"
      val source = base / "symlink-source"
      fileSystem.createSymlink(source, target)
      fileSystem.sink(source).buffer().use {
        it.writeUtf8("This writes to the the source file")
      }
      assertEquals("This writes to the the source file", target.readUtf8())
    }

    test("openFileWithDirectorySymlink") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAA = base / "a" / "a"
      val baseB = base / "b"
      val baseBA = base / "b" / "a"
      fileSystem.createDirectory(baseA)
      baseAA.writeUtf8("aa")
      fileSystem.createSymlink(baseB, baseA)
      assertEquals("aa", baseAA.readUtf8())
      assertEquals("aa", baseBA.readUtf8())
    }

    test("openSymlinkFileHandle") {
      if (!supportsSymlink()) return@test

      val target = base / "symlink-target"
      val source = base / "symlink-source"
      fileSystem.createSymlink(source, target)
      target.writeUtf8("I am the target file")
      val sourceContent = fileSystem.openReadOnly(source).use { fileHandle ->
        fileHandle.source().buffer().use { it.readUtf8() }
      }
      assertEquals("I am the target file", sourceContent)
    }

    test("listSymlinkDirectory") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAA = base / "a" / "a"
      val baseAB = base / "a" / "b"
      val baseB = base / "b"
      val baseBA = base / "b" / "a"
      val baseBB = base / "b" / "b"
      fileSystem.createDirectory(baseA)
      baseAA.writeUtf8("aa")
      baseAB.writeUtf8("ab")
      fileSystem.createSymlink(baseB, baseA)
      assertEquals(listOf(baseBA, baseBB), fileSystem.list(baseB))
    }

    test("symlinkFileLastAccessedAt") {
      if (!supportsSymlink()) return@test

      val target = base / "symlink-target"
      val source = base / "symlink-source"
      target.writeUtf8("a")
      fileSystem.createSymlink(source, target)
      tryAdvanceTime()
      val minTime = clock.now()
      assertEquals("a", source.readUtf8())
      val maxTime = clock.now()
      assertInRange(fileSystem.metadata(source).lastAccessedAt, minTime, maxTime)
    }

    test("symlinkDirectoryLastAccessedAt") {
      if (!supportsSymlink()) return@test

      val baseA = base / "a"
      val baseAA = base / "a" / "a"
      val baseB = base / "b"
      val baseBA = base / "b" / "a"
      fileSystem.createDirectory(baseA)
      baseAA.writeUtf8("aa")
      fileSystem.createSymlink(baseB, baseA)
      tryAdvanceTime()
      val minTime = clock.now()
      assertEquals("aa", baseBA.readUtf8())
      val maxTime = clock.now()
      assertInRange(fileSystem.metadata(baseB).lastAccessedAt, minTime, maxTime)
    }

    test("deleteSymlinkDoesntDeleteTargetFile") {
      if (!supportsSymlink()) return@test

      val target = base / "symlink-target"
      val source = base / "symlink-source"
      target.writeUtf8("I am the target file")
      fileSystem.createSymlink(source, target)
      fileSystem.delete(source)
      assertEquals("I am the target file", target.readUtf8())
    }

    test("moveSymlinkDoesntMoveTargetFile") {
      if (!supportsSymlink()) return@test

      val target = base / "symlink-target"
      val source1 = base / "symlink-source-1"
      val source2 = base / "symlink-source-2"
      target.writeUtf8("I am the target file")
      fileSystem.createSymlink(source1, target)
      fileSystem.atomicMove(source1, source2)
      assertEquals("I am the target file", target.readUtf8())
      assertEquals("I am the target file", source2.readUtf8())
      // Okio's WasiFileSystem only creates relative symlinks.
      assertEquals(
        when {
          isWasiFileSystem -> target.relativeTo(source1.parent!!)
          else -> target
        },
        fileSystem.metadata(source2).symlinkTarget,
      )
    }

    test("symlinkCanBeRelative") {
      if (!supportsSymlink()) return@test

      val relativeTarget = "symlink-target".toPath()
      val absoluteTarget = base / relativeTarget
      val source = base / "symlink-source"
      absoluteTarget.writeUtf8("I am the target file")
      fileSystem.createSymlink(source, relativeTarget)
      assertEquals("I am the target file", source.readUtf8())
    }

    test("symlinkCanBeRelativeWithDotDots") {
      if (!supportsSymlink()) return@test

      val relativeTarget = "../b/symlink-target".toPath()
      val absoluteTarget = base / "b" / "symlink-target"
      val absoluteSource = base / "a" / "symlink-source"
      fileSystem.createDirectory(absoluteSource.parent!!)
      fileSystem.createDirectory(absoluteTarget.parent!!)
      absoluteTarget.writeUtf8("I am the target file")
      fileSystem.createSymlink(absoluteSource, relativeTarget)
      assertEquals("I am the target file", absoluteSource.readUtf8())
    }

    test("followingRecursiveSymlinksIsOkay") {
      if (!supportsSymlink()) return@test

      val pathA = base / "symlink-a"
      val pathB = base / "symlink-b"
      val pathC = base / "symlink-c"
      val target = base / "symlink-target"
      fileSystem.createSymlink(pathA, pathB)
      fileSystem.createSymlink(pathB, pathC)
      fileSystem.createSymlink(pathC, target)
      target.writeUtf8("I am the target file")
      assertEquals("I am the target file", pathC.readUtf8())
      assertEquals("I am the target file", pathB.readUtf8())
      assertEquals("I am the target file", pathA.readUtf8())
    }

    test("symlinkCycle") {
      if (!supportsSymlink()) return@test

      val pathA = base / "symlink-a"
      val pathB = base / "symlink-b"
      fileSystem.createSymlink(pathA, pathB)
      fileSystem.createSymlink(pathB, pathA)
      assertFailsWith<IOException> {
        pathB.writeUtf8("This should not work")
      }
      assertFailsWith<IOException> {
        pathB.readUtf8()
      }
    }

    test("readAfterFileSystemClose") {
      val path = base / "file"

      path.writeUtf8("hello, world!")

      when (closeBehavior) {
        CloseBehavior.Closes -> {
          fileSystem.close()

          assertFailsWith<IllegalStateException> {
            fileSystem.canonicalize(path)
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.exists(path)
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.metadata(path)
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.openReadOnly(path)
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.source(path)
          }
        }

        CloseBehavior.DoesNothing -> {
          fileSystem.close()
          fileSystem.canonicalize(path)
          fileSystem.exists(path)
          fileSystem.metadata(path)
          fileSystem.openReadOnly(path).use {
          }
          fileSystem.source(path).use {
          }
        }

        CloseBehavior.Unsupported -> {
          assertFailsWith<UnsupportedOperationException> {
            fileSystem.close()
          }
        }
      }
    }

    test("writeAfterFileSystemClose") {
      val path = base / "file"

      when (closeBehavior) {
        CloseBehavior.Closes -> {
          fileSystem.close()

          assertFailsWith<IllegalStateException> {
            fileSystem.appendingSink(path)
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.atomicMove(path, base / "file2")
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.createDirectory(base / "directory")
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.delete(path)
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.openReadWrite(path)
          }
          assertFailsWith<IllegalStateException> {
            fileSystem.sink(path)
          }
          if (supportsSymlink()) {
            assertFailsWith<IllegalStateException> {
              fileSystem.createSymlink(base / "symlink", base)
            }
          }
        }

        CloseBehavior.DoesNothing -> {
          fileSystem.close()

          fileSystem.appendingSink(path).use {
          }
          fileSystem.atomicMove(path, base / "file2")
          fileSystem.createDirectory(base / "directory")
          fileSystem.delete(path)
          fileSystem.sink(path).use {
          }
          fileSystem.openReadWrite(path).use {
          }
          if (supportsSymlink()) {
            fileSystem.createSymlink(base / "symlink", base)
          }
        }

        CloseBehavior.Unsupported -> {
          assertFailsWith<UnsupportedOperationException> {
            fileSystem.close()
          }
        }
      }
    }
    extraTests()
  }
}

enum class FileSystemVariant { System, Fake, NodeJs, Wasi, JimfsWrapping }

open class FileSystemFixture(
  val clock: Clock,
  val fileSystem: FileSystem,
  val windowsLimitations: Boolean,
  val allowClobberingEmptyDirectories: Boolean,
  val allowAtomicMoveFromFileToDirectory: Boolean,
  val allowRenameWhenTargetIsOpen: Boolean = !windowsLimitations,
  val closeBehavior: CloseBehavior,
  val temporaryDirectory: Path,
  val variant: FileSystemVariant,
) {
  val base = fileSystem.createTestPath(temporaryDirectory)
  internal val isNodeJsFileSystem get() = variant == FileSystemVariant.NodeJs
  internal val isWasiFileSystem get() = variant == FileSystemVariant.Wasi
  internal val isWrappingJimFileSystem = variant == FileSystemVariant.JimfsWrapping

  fun supportsSymlink(): Boolean {
    if (fileSystem.isFakeFileSystem) return fileSystem.allowSymlinks
    if (windowsLimitations) return false
    return when (fileSystem::class.simpleName) {
      "JvmSystemFileSystem",
      -> false

      else -> true
    }
  }

  internal fun expectIOExceptionOnWindows(
    exceptJs: Boolean = false,
    block: () -> Unit,
  ) {
    val expectCrash = windowsLimitations && (!isNodeJsFileSystem || !exceptJs)
    try {
      block()
      assertFalse(expectCrash)
    } catch (_: IOException) {
      assertTrue(expectCrash)
    }
  }

  fun Path.readUtf8(): String {
    return fileSystem.source(this).buffer().use {
      it.readUtf8()
    }
  }

  fun Path.writeUtf8(string: String) {
    fileSystem.sink(this).buffer().use {
      it.writeUtf8(string)
    }
  }

  fun Path.createDirectory() {
    fileSystem.createDirectory(this)
  }

  /**
   * Returns the earliest file system time that could be recorded for an event occurring at this
   * instant. This truncates fractional seconds because most host file systems do not use precise
   * timestamps for file metadata.
   *
   * It also pads the result by 200 milliseconds because the host and file system may use different
   * clocks, allowing the time on the CPU to drift ahead of the time on the file system.
   */
  internal fun Instant.minFileSystemTime(): Instant {
    val paddedInstant = minus(200.milliseconds)
    return Instant.fromEpochSeconds(paddedInstant.epochSeconds)
  }

  /**
   * Returns the latest file system time that could be recorded for an event occurring at this
   * instant. This adds 2 seconds and truncates fractional seconds because file systems may defer
   * assigning the timestamp.
   *
   * It also pads the result by 200 milliseconds because the host and file system may use different
   * clocks, allowing the time on the CPU to drift behind the time on the file system.
   *
   * https://docs.microsoft.com/en-us/windows/win32/sysinfo/file-times
   */
  internal fun Instant.maxFileSystemTime(): Instant {
    val paddedInstant = plus(200.milliseconds)
    return Instant.fromEpochSeconds(paddedInstant.plus(2.seconds).epochSeconds)
  }

  /**
   * Attempt to advance the clock so that any already-issued timestamps will not collide with
   * timestamps yet to be issued.
   */
  internal fun tryAdvanceTime() {
    if (clock is FakeClock) clock.sleep(1.minutes)
  }

  internal fun assertInRange(sampled: Instant?, minTime: Instant, maxTime: Instant) {
    if (sampled == null) return
    val minFsTime = minTime.minFileSystemTime()
    val maxFsTime = maxTime.maxFileSystemTime()
    assertTrue("expected $sampled in $minFsTime..$maxFsTime (relaxed from $minTime..$maxTime)") {
      sampled in minFsTime..maxFsTime
    }
  }

  internal fun isJvmFileSystemOnWindows(): Boolean {
    return windowsLimitations && fileSystem::class.simpleName == "JvmSystemFileSystem"
  }

  internal fun isJimFileSystem(): Boolean {
    return "JimfsFileSystem" in fileSystem.toString()
  }

  internal fun isNodeJsFileSystemOnWindows(): Boolean {
    return windowsLimitations && fileSystem::class.simpleName == "NodeJsFileSystem"
  }
}
