/*
 * Copyright (C) 2023 Square, Inc. and others.
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

import java.net.URLClassLoader
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.walk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import okio.internal.ResourceFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val PROC_SELF_FD = "/proc/self/fd"

class FileLeakTest {

  private lateinit var fakeFileSystem: FakeFileSystem
  private val fakeZip = "/test.zip".toPath()
  private val fakeEntry = "some.file".toPath()
  private val fakeDirectory = "/another/".toPath()
  private val fakeEntry2 = fakeDirectory / "another.file"

  @BeforeEach
  fun setup() {
    fakeFileSystem = FakeFileSystem()
    with(fakeFileSystem) {
      write(fakeZip) {
        writeZip {
          putEntry(fakeEntry.name) {
            writeUtf8("FooBar")
          }
          try {
            putNextEntry(ZipEntry(fakeDirectory.name).apply { time = 0L })
          } finally {
            closeEntry()
          }
          putEntry(fakeEntry2.toString()) {
            writeUtf8("SomethingElse")
          }
        }
      }
    }
  }

  @AfterEach
  fun tearDown() {
    fakeFileSystem.checkNoOpenFiles()
  }

  @Test
  fun zipFileSystemExistsTest() {
    val zipFileSystem = fakeFileSystem.openZip(fakeZip)
    assertTrue(zipFileSystem.exists(fakeEntry))
  }

  @Test
  fun zipFileSystemMetadataTest() {
    val zipFileSystem = fakeFileSystem.openZip(fakeZip)
    assertNotNull(zipFileSystem.metadataOrNull(fakeEntry))
  }

  @Test
  fun zipFileSystemSourceTest() {
    val zipFileSystem = fakeFileSystem.openZip(fakeZip)
    zipFileSystem.source(fakeEntry).use { source ->
      assertEquals("FooBar", source.buffer().readUtf8())
    }
  }

  @Test
  fun zipFileSystemListRecursiveTest() {
    val zipFileSystem = fakeFileSystem.openZip(fakeZip)
    zipFileSystem.listRecursively("/".toPath()).toList()
    fakeFileSystem.delete(fakeZip)
  }

  @Test
  fun fileLeakInResourceFileSystemTest() {
    Assumptions.assumeTrue(Path(PROC_SELF_FD).exists(), "File descriptor symbolic link available only on Linux")
    // Create a test file that will be opened and cached by the classloader
    val zipPath = ZipBuilder(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / randomToken(16))
      .addEntry("test.txt", "I'm part of a test!")
      .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
      .build()

    // Create a custom class loader
    val urlClassLoader = URLClassLoader.newInstance(arrayOf(zipPath.toFile().toURI().toURL()))

    // Create a resource file system using the given a custom class loader
    val resourceFileSystem = ResourceFileSystem(
      classLoader = urlClassLoader,
      indexEagerly = false,
    )

    // Trigger the read of the classloader
    resourceFileSystem.source("test.txt".toPath()).use { it.buffer().readUtf8() }

    // Classloader needs to be closed in order to close the file descriptor to the JAR file
    urlClassLoader.close()

    // Ensure the underlying URLConnection to the JAR file was not cached
    zipPath.toNioPath().assetFileNotOpen()
  }
}

/**
 * Writes a ZIP file to a [BufferedSink].
 */
private inline fun <R> BufferedSink.writeZip(action: ZipOutputStream.() -> R): R {
  return ZipOutputStream(outputStream()).use(action)
}

/**
 * Adds a new ZIP entry named [name], populates it with [action], and closes the entry.
 */
private inline fun <R> ZipOutputStream.putEntry(name: String, action: BufferedSink.() -> R): R {
  putNextEntry(ZipEntry(name).apply { time = 0L })
  val sink = sink().buffer()
  return try {
    sink.action()
  } finally {
    sink.flush()
    closeEntry()
  }
}

// This is a Linux only test for open file descriptors on the current process
@OptIn(ExperimentalPathApi::class)
private fun Path.assetFileNotOpen() {
  val fds = Path(PROC_SELF_FD)
  if (fds.isDirectory()) {
    // Linux: verify that path is not open
    assertTrue("Resource remained opened: $this") {
      fds.walk()
        .filter { it.isSymbolicLink() }
        .map { it.readSymbolicLink() }
        .none { it == this }
    }
  }
}
