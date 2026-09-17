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
package okio.internal

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import de.infix.testBalloon.framework.core.testSuite
import java.net.URL
import java.net.URLClassLoader
import java.util.Enumeration
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail
import okio.BufferedSource
import okio.ByteString
import okio.FileNotFoundException
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.ZipBuilder
import okio.testDirectory

val ResourceFileSystemTest by testSuite {
  val fileSystem = FileSystem.RESOURCES as ResourceFileSystem
  fun Package.toPath(): Path = name.replace(".", "/").toPath()

  testDirectory(FileSystem.SYSTEM) asParameterForEach {
    test("testResourceA") {
      val path = "okio/resourcefilesystem/a.txt".toPath()

      val metadata = fileSystem.metadataOrNull(path)!!

      assertThat(metadata.size).isEqualTo(1L)
      assertThat(metadata.isRegularFile).isTrue()
      assertThat(metadata.isDirectory).isFalse()

      val content = fileSystem.read(path) { readUtf8() }
      assertThat(fileSystem.metadata(path).isRegularFile).isTrue()

      assertThat(content).isEqualTo("a")
    }

    test("testResourceB") {
      val path = "okio/resourcefilesystem/b/b.txt".toPath()

      val metadata = fileSystem.metadataOrNull(path)!!

      assertThat(metadata.size).isEqualTo(3L)
      assertThat(metadata.isRegularFile).isTrue()
      assertThat(metadata.isDirectory).isFalse()

      val content = fileSystem.read(path) { readUtf8() }
      assertThat(fileSystem.metadata(path).isRegularFile).isTrue()

      assertThat(content).isEqualTo("b/b")
    }

    test("testSingleArchive") { base ->
      val zipPath = ZipBuilder(base)
        .addEntry("hello.txt", "Hello World")
        .addEntry("directory/subdirectory/child.txt", "Another file!")
        .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        .build()
      val resourceFileSystem = ResourceFileSystem(
        classLoader = URLClassLoader(arrayOf(zipPath.toFile().toURI().toURL()), null),
        indexEagerly = false,
      )

      assertThat(resourceFileSystem.read("hello.txt".toPath()) { readUtf8() })
        .isEqualTo("Hello World")
      assertThat(
        resourceFileSystem.metadata("hello.txt".toPath()).isRegularFile,
      ).isTrue()

      assertThat(resourceFileSystem.read("directory/subdirectory/child.txt".toPath()) { readUtf8() })
        .isEqualTo("Another file!")
      assertThat(
        resourceFileSystem.metadata("directory/subdirectory/child.txt".toPath()).isRegularFile,
      ).isTrue()

      assertThat(resourceFileSystem.list("/".toPath())).containsExactlyInAnyOrder(
        "/META-INF".toPath(),
        "/hello.txt".toPath(),
        "/directory".toPath(),
      )
      assertThat(resourceFileSystem.list("/directory".toPath()))
        .containsExactly("/directory/subdirectory".toPath())
      assertThat(resourceFileSystem.list("/directory/subdirectory".toPath()))
        .containsExactly("/directory/subdirectory/child.txt".toPath())

      val metadata = resourceFileSystem.metadata(".".toPath())
      assertThat(metadata.isDirectory).isTrue()
    }

    test("testDirectoryAndJarOverlap") { base ->
      val filesAPath = base / "filesA"
      FileSystem.SYSTEM.createDirectories(filesAPath / "colors")
      FileSystem.SYSTEM.write(filesAPath / "colors" / "red.txt") { writeUtf8("Apples are red") }
      FileSystem.SYSTEM.write(filesAPath / "colors" / "green.txt") { writeUtf8("Grass is green") }
      val zipBPath = ZipBuilder(base)
        .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        .addEntry("colors/blue.txt", "The sky is blue")
        .addEntry("colors/green.txt", "Limes are green")
        .build()

      val resourceFileSystem = ResourceFileSystem(
        classLoader = URLClassLoader(
          arrayOf(
            filesAPath.toFile().toURI().toURL(),
            zipBPath.toFile().toURI().toURL(),
          ),
          null,
        ),
        indexEagerly = false,
      )

      assertThat(resourceFileSystem.read("/colors/red.txt".toPath()) { readUtf8() })
        .isEqualTo("Apples are red")
      assertThat(resourceFileSystem.read("/colors/green.txt".toPath()) { readUtf8() })
        .isEqualTo("Grass is green")
      assertThat(resourceFileSystem.read("/colors/blue.txt".toPath()) { readUtf8() })
        .isEqualTo("The sky is blue")

      assertThat(resourceFileSystem.metadata("/colors/red.txt".toPath()).isRegularFile).isTrue()
      assertThat(resourceFileSystem.metadata("/colors/green.txt".toPath()).isRegularFile).isTrue()
      assertThat(resourceFileSystem.metadata("/colors/blue.txt".toPath()).isRegularFile).isTrue()

      assertThat(resourceFileSystem.list("/".toPath())).containsExactlyInAnyOrder(
        "/META-INF".toPath(),
        "/colors".toPath(),
      )
      assertThat(resourceFileSystem.list("/colors".toPath())).containsExactlyInAnyOrder(
        "/colors/red.txt".toPath(),
        "/colors/green.txt".toPath(),
        "/colors/blue.txt".toPath(),
      )

      assertThat(resourceFileSystem.metadata("/".toPath()).isDirectory).isTrue()
      assertThat(resourceFileSystem.metadata("/colors".toPath()).isDirectory).isTrue()
    }

    test("testDirectoryAndDirectoryOverlap") { base ->
      val filesAPath = base / "filesA"
      FileSystem.SYSTEM.createDirectories(filesAPath / "colors")
      FileSystem.SYSTEM.write(filesAPath / "colors" / "red.txt") { writeUtf8("Apples are red") }
      FileSystem.SYSTEM.write(filesAPath / "colors" / "green.txt") { writeUtf8("Grass is green") }
      val filesBPath = base / "filesB"
      FileSystem.SYSTEM.createDirectories(filesBPath / "colors")
      FileSystem.SYSTEM.write(filesBPath / "colors" / "blue.txt") { writeUtf8("The sky is blue") }
      FileSystem.SYSTEM.write(filesBPath / "colors" / "green.txt") { writeUtf8("Limes are green") }

      val resourceFileSystem = ResourceFileSystem(
        classLoader = URLClassLoader(
          arrayOf(
            filesAPath.toFile().toURI().toURL(),
            filesBPath.toFile().toURI().toURL(),
          ),
          null,
        ),
        indexEagerly = false,
      )

      assertThat(resourceFileSystem.read("/colors/red.txt".toPath()) { readUtf8() })
        .isEqualTo("Apples are red")
      assertThat(resourceFileSystem.read("/colors/green.txt".toPath()) { readUtf8() })
        .isEqualTo("Grass is green")
      assertThat(resourceFileSystem.read("/colors/blue.txt".toPath()) { readUtf8() })
        .isEqualTo("The sky is blue")

      assertThat(resourceFileSystem.metadata("/colors/red.txt".toPath()).isRegularFile).isTrue()
      assertThat(resourceFileSystem.metadata("/colors/green.txt".toPath()).isRegularFile).isTrue()
      assertThat(resourceFileSystem.metadata("/colors/blue.txt".toPath()).isRegularFile).isTrue()

      assertThat(resourceFileSystem.list("/".toPath())).containsExactlyInAnyOrder(
        "/colors".toPath(),
      )
      assertThat(resourceFileSystem.list("/colors".toPath())).containsExactlyInAnyOrder(
        "/colors/red.txt".toPath(),
        "/colors/green.txt".toPath(),
        "/colors/blue.txt".toPath(),
      )

      assertThat(resourceFileSystem.metadata("/".toPath()).isDirectory).isTrue()
      assertThat(resourceFileSystem.metadata("/colors".toPath()).isDirectory).isTrue()
    }

    test("testJarAndJarOverlap") { base ->
      val zipAPath = ZipBuilder(base)
        .addEntry("colors/red.txt", "Apples are red")
        .addEntry("colors/green.txt", "Grass is green")
        .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        .build()
      val zipBPath = ZipBuilder(base)
        .addEntry("colors/blue.txt", "The sky is blue")
        .addEntry("colors/green.txt", "Limes are green")
        .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        .build()
      val resourceFileSystem = ResourceFileSystem(
        classLoader = URLClassLoader(
          arrayOf(
            zipAPath.toFile().toURI().toURL(),
            zipBPath.toFile().toURI().toURL(),
          ),
          null,
        ),
        indexEagerly = false,
      )

      assertThat(resourceFileSystem.read("/colors/red.txt".toPath()) { readUtf8() })
        .isEqualTo("Apples are red")
      assertThat(resourceFileSystem.read("/colors/green.txt".toPath()) { readUtf8() })
        .isEqualTo("Grass is green")
      assertThat(resourceFileSystem.read("/colors/blue.txt".toPath()) { readUtf8() })
        .isEqualTo("The sky is blue")

      assertThat(resourceFileSystem.metadata("/colors/red.txt".toPath()).isRegularFile).isTrue()
      assertThat(resourceFileSystem.metadata("/colors/green.txt".toPath()).isRegularFile).isTrue()
      assertThat(resourceFileSystem.metadata("/colors/blue.txt".toPath()).isRegularFile).isTrue()

      assertThat(resourceFileSystem.list("/".toPath())).containsExactlyInAnyOrder(
        "/META-INF".toPath(),
        "/colors".toPath(),
      )
      assertThat(resourceFileSystem.list("/colors".toPath())).containsExactlyInAnyOrder(
        "/colors/red.txt".toPath(),
        "/colors/green.txt".toPath(),
        "/colors/blue.txt".toPath(),
      )

      assertThat(resourceFileSystem.metadata("/".toPath()).isDirectory).isTrue()
      assertThat(resourceFileSystem.metadata("/colors".toPath()).isDirectory).isTrue()
    }

    test("testResourceMissing") {
      val path = "okio/resourcefilesystem/b/c.txt".toPath()

      assertThat(fileSystem.metadataOrNull(path)).isNull()

      try {
        fileSystem.read(path) { readUtf8() }
        fail()
      } catch (ioe: IOException) {
        assertThat(ioe.message).isEqualTo("file not found: okio/resourcefilesystem/b/c.txt")
      }
    }

    test("testProjectIsListable") {
      val path = "okio/resourcefilesystem/b/".toPath()

      val metadata = fileSystem.metadataOrNull(path)!!

      assertThat(metadata.isDirectory).isTrue()
      assertThat(metadata.createdAtMillis).isNotNull().isGreaterThan(1L)

      assertThat(fileSystem.list(path).map { it.name }).containsExactly("b.txt")
    }

    test("testResourceFromJar") {
      val path = "go/NOTICE".toPath()

      val metadata = fileSystem.metadataOrNull(path)!!

      assertThat(metadata.size).isNotNull().isGreaterThan(100L)
      assertThat(metadata.isRegularFile).isTrue()
      assertThat(metadata.isDirectory).isFalse()

      val content = fileSystem.read(path) { readUtf8Line() }

      assertThat(content).isEqualTo("The files in this directory are copied from Go:")
    }

    test("testClassFilesOmittedFromJar") {
      assertThat(fileSystem.list("/org/junit/jupiter/api/function".toPath())).isEmpty()
      assertThat(fileSystem.metadataOrNull("/org/junit/jupiter/Test.class".toPath())).isNull()
    }

    test("testClassFilesOmittedFromDirectory") { base ->
      val filesPath = base / "files"
      val packagePath = filesPath / "com" / "example" / "project"
      FileSystem.SYSTEM.createDirectories(packagePath)
      FileSystem.SYSTEM.write(packagePath / "Hello.class") { writeUtf8("cafebabe") }

      val resourceFileSystem = ResourceFileSystem(
        classLoader = URLClassLoader(
          arrayOf(filesPath.toFile().toURI().toURL()),
          null,
        ),
        indexEagerly = false,
      )

      assertThat(resourceFileSystem.list("/com/example/project".toPath())).isEmpty()
      assertThat(resourceFileSystem.metadataOrNull("/com/example/project/Hello.class".toPath()))
        .isNull()
      assertFailsWith<FileNotFoundException> {
        resourceFileSystem.source("/com/example/project/Hello.class".toPath())
      }
    }

    test("testDirectoryFromJar") {
      val path = "org/junit/".toPath()

      val metadata = fileSystem.metadataOrNull(path)
      assertThat(metadata?.isDirectory).isNotNull().isTrue()

      val files = fileSystem.list(path).map { it.name }
      assertThat(files).containsAtLeast("jupiter", "platform")
      assertThat(files.filter { it.endsWith(".class") }).isEmpty()
    }

    test("packagePath") {
      val path = ByteString::class.java.`package`.toPath()

      assertThat((path / "a.txt").toString())
        .isEqualTo("okio${Path.DIRECTORY_SEPARATOR}a.txt")
    }

    test("classResource") {
      val path = ByteString::class.packagePath!!

      assertThat((path / "a.txt").toString())
        .isEqualTo("okio${Path.DIRECTORY_SEPARATOR}a.txt")
    }

    /**
     * Confirm that class loaders aren't accessed until the file system is used. This should save
     * resources so that zip files aren't decoded if they're unused.
     */
    test("testIndexLazily") {
      val classLoader = object : ClassLoader() {
        override fun findResources(name: String?): Enumeration<URL> {
          throw Exception("finding a resource")
        }
      }

      val resourceFileSystem = ResourceFileSystem(
        classLoader = classLoader,
        indexEagerly = false,
      )

      assertThat(
        assertFailsWith<Exception> {
          resourceFileSystem.list("/".toPath())
        },
      ).hasMessage("finding a resource")
    }

    test("testIndexEagerly") {
      val classLoader = object : ClassLoader() {
        override fun findResources(name: String?): Enumeration<URL> {
          throw Exception("finding a resource")
        }
      }

      assertThat(
        assertFailsWith<Exception> {
          ResourceFileSystem(
            classLoader = classLoader,
            indexEagerly = true,
          )
        },
      ).hasMessage("finding a resource")
    }

    /** Confirm we can read individual files without triggering indexing. */
    test("testSourceDoesntTriggerIndexing") { base ->
      val processedPaths = mutableSetOf<Path>()
      val recordingFileSystem = object : ForwardingFileSystem(FileSystem.SYSTEM) {
        override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
          processedPaths += path
          return super.onPathParameter(path, functionName, parameterName)
        }
      }

      val zipAPath = ZipBuilder(base)
        .addEntry("colors/red.txt", "Apples are red")
        .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        .build()
      val zipBPath = ZipBuilder(base)
        .addEntry("colors/blue.txt", "The sky is blue")
        .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        .build()
      val resourceFileSystem = ResourceFileSystem(
        classLoader = URLClassLoader(
          arrayOf(
            zipAPath.toFile().toURI().toURL(),
            zipBPath.toFile().toURI().toURL(),
          ),
          null,
        ),
        indexEagerly = false,
        systemFileSystem = recordingFileSystem,
      )

      // Reading paths with source() or read() doesn't index zips.
      assertThat(
        resourceFileSystem.read("/colors/red.txt".toPath()) { readUtf8() },
      ).isEqualTo("Apples are red")
      assertThat(
        resourceFileSystem.read("/colors/blue.txt".toPath()) { readUtf8() },
      ).isEqualTo("The sky is blue")
      assertThat(processedPaths).isEmpty()

      // Calling list() does though.
      assertThat(resourceFileSystem.list("/colors".toPath())).containsExactlyInAnyOrder(
        "/colors/red.txt".toPath(),
        "/colors/blue.txt".toPath(),
      )
      assertThat(processedPaths).containsExactlyInAnyOrder(
        zipAPath,
        zipBPath,
      )
    }

    /**
     * Our resource file system uses [URLClassLoader] internally, which means we need to go back and
     * forth between [File], [URL], and [URI] models for component paths. This is a big hazard for
     * escaping special characters and it's likely that some file paths won't survive the round trip!
     */
    test("fileNameWithSpaceInPath") { base ->
      val zipPath = ZipBuilder(base / "space in directory name")
        .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        .addEntry("hello.txt", "Hello World")
        .build()
      val resourceFileSystem = ResourceFileSystem(
        classLoader = URLClassLoader(arrayOf(zipPath.toFile().toURI().toURL()), null),
        indexEagerly = false,
      )

      assertThat(resourceFileSystem.read("hello.txt".toPath()) { readUtf8() })
        .isEqualTo("Hello World")
      assertThat(resourceFileSystem.metadata("hello.txt".toPath())).isNotNull()
    }

    test("missingResourceSilentlyIgnored") { base ->
      val zipAPath = ZipBuilder(base)
        .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        .addEntry("hello.txt", "Hello World")
        .build()
      val resourceFileSystem = ResourceFileSystem(
        classLoader = URLClassLoader(
          arrayOf(
            zipAPath.toFile().toURI().toURL(),
            (base / "missing.zip").toFile().toURI().toURL(),
          ),
          null,
        ),
        indexEagerly = false,
      )

      assertThat(resourceFileSystem.read("hello.txt".toPath()) { readUtf8() })
        .isEqualTo("Hello World")
    }

    test("listSpecialCharacterNamedFiles") {
      val path = "okio/resourcefilesystem/non-ascii".toPath()

      assertThat(fileSystem.listRecursively(path).toList()).containsExactly(
        "/okio/resourcefilesystem/non-ascii/ギリシア神話".toPath(),
        "/okio/resourcefilesystem/non-ascii/ギリシア神話/Ἰλιάς".toPath(),
      )

      val content = fileSystem.read(
        "/okio/resourcefilesystem/non-ascii/ギリシア神話/Ἰλιάς".toPath(),
        BufferedSource::readUtf8,
      )
      assertEquals("Chante, ô déesse, le courroux du Péléide Achille,\n", content)
    }
  }
}

private val KClass<*>.packagePath: Path?
  get() = qualifiedName?.replace(".", "/")?.toPath()?.parent
