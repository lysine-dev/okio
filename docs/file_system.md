File System
===========

Okio's file system is designed to be easy, testable, multiplatform, and efficient.

### Easy

Reading and writing files is concise yet flexible.

```kotlin
val path = "README.md".toPath()

val readmeContent = FileSystem.SYSTEM.read(path) {
  readUtf8()
}

val updatedContent = readmeContent.replace("red", "blue")

FileSystem.SYSTEM.write(path) {
  writeUtf8(updatedContent)
}
```


### Testable

It's easy to swap out the real file system with a fake. This makes tests run faster and more
reliably.

```kotlin
val fileSystem = FakeFileSystem()
val userHome = "/Users/sandy".toPath()
val gitConfig = userHome / ".gitconfig"

fileSystem.createDirectories(userHome)
val original = """
    |[user]
    |  email = sandy@example.com
    |""".trimMargin()
fileSystem.write(gitConfig) { writeUtf8(original) }

GitConfigFixer(fileSystem).fix(userHome)

val expected = """
  |[user]
  |  email = sandy@example.com
  |[diff]
  |  renames = true
  |  indentHeuristic = on
  """.trimIndent()
assertEquals(expected, fileSystem.read(gitConfig) { readUtf8() })
```

With `ForwardingFileSystem` you can easily inject faults to confirm your program is graceful even
when the user's disk fills up.


### Multiplatform

Okio’s `Path` class supports Windows-style (like `C:\autoexec.bat`) and UNIX-style paths
(like `/etc/passwd`). It supports manipulating Windows paths on UNIX, and UNIX paths on Windows.

The system `FileSystem` abstracts over these platform APIs:

 * Android API levels <26: [java.io.File](https://developer.android.com/reference/java/io/File)
 * Java and Android API level 26+: [java.nio.file](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystem.html)
 * Linux: [man pages](https://www.kernel.org/doc/man-pages/)
 * UNIX: [stdio.h](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/stdio.h.html)
 * Windows: [fileapi.h](https://docs.microsoft.com/en-us/windows/win32/api/fileapi/)
 * Node.js: [file system](https://nodejs.org/api/fs.html)


### Efficient

Read and write operations integrate with Okio buffers to reduce the number of system calls.

It exposes high-level operations like `atomicMove()` and `metadata` to get the OS to do all the work
when appropriate.


## Known Issues


Okio's implementation is constrained by the capabilities its underlying APIs. This page is an
overview of these limitations.


### All Platforms

 * There are no APIs for file permissions, watches, volume management, memory mapping, or locking.
 * Paths that cannot be represented as UTF-8 strings are unsupported. The underlying APIs that Okio
   calls through, including `java.io.File`, all treat paths as strings.


### Kotlin/JVM

#### On Android, API level less than 26:

 * Creating and accessing symlinks is unsupported.


#### On Windows:

 * `FileSystem.atomicMove()` fails if the target file already exists.


### Kotlin/Native

 * FakeFileSystem does not support concurrent use. We are [holding off on this][fake_fs_concurrency]
   until the upcoming memory model is released.

#### On Windows:

 * Creating and accessing symlinks is unsupported.


### Kotlin/JS

 * NodeJsFileSystem's `source()` and `sink()` cannot access UNIX pipes.
 * Instead of returning null, `NodeJsFileSystem.metadataOrNull()` throws `IOException` if the path
   is invalid. (In the Node.js API there's no mechanism to differentiate between a failure to read
   a valid path and a rejection of an invalid path.)


[fake_fs_concurrency]: https://github.com/lysine-dev/okio/issues/950
