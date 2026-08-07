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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import okio.Path.Companion.toPath
import okio.internal.toPath
import platform.posix.ENOENT
import platform.posix.FILE
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_RDWR
import platform.posix.PATH_MAX
import platform.posix.S_IFLNK
import platform.posix.S_IFMT
import platform.posix.closedir
import platform.posix.dirent
import platform.posix.errno
import platform.posix.fdopen
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.free
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.open
import platform.posix.opendir
import platform.posix.pread
import platform.posix.pwrite
import platform.posix.readdir
import platform.posix.readlink
import platform.posix.realpath
import platform.posix.remove
import platform.posix.rename
import platform.posix.set_posix_errno
import platform.posix.symlink
import platform.posix.timespec

internal actual val PLATFORM_TEMPORARY_DIRECTORY: Path
  get() {
    val tmpdir = getenv("TMPDIR")
    if (tmpdir != null) return tmpdir.toKString().toPath()

    return "/tmp".toPath()
  }

internal actual val PLATFORM_DIRECTORY_SEPARATOR = "/"

internal actual fun PosixFileSystem.variantDelete(path: Path, mustExist: Boolean) {
  val result = remove(path.toString())
  if (result != 0) {
    if (errno == ENOENT) {
      if (mustExist) {
        throw FileNotFoundException("no such file: $path")
      } else {
        return
      }
    }
    throw errnoToIOException(errno)
  }
}

internal actual fun PosixFileSystem.variantList(dir: Path, throwOnFailure: Boolean): List<Path>? {
  val opendir = opendir(dir.toString())
    ?: if (throwOnFailure) throw errnoToIOException(errno) else return null

  try {
    val result = mutableListOf<Path>()
    val buffer = Buffer()

    set_posix_errno(0) // If readdir() returns null it's either the end or an error.
    while (true) {
      val dirent: CPointer<dirent> = readdir(opendir) ?: break
      val childPath = buffer.writeNullTerminated(
        bytes = dirent[0].d_name,
      ).toPath(normalize = true)

      if (childPath == SELF_DIRECTORY_ENTRY || childPath == PARENT_DIRECTORY_ENTRY) {
        continue // exclude '.' and '..' from the results.
      }

      result += dir / childPath
    }

    if (errno != 0) {
      if (throwOnFailure) {
        throw errnoToIOException(errno)
      } else {
        return null
      }
    }

    result.sort()
    return result
  } finally {
    closedir(opendir) // Ignore errno from closedir.
  }
}

@OptIn(UnsafeNumber::class)
internal actual fun PosixFileSystem.variantMkdir(dir: Path): Int {
  return mkdir(dir.toString(), 0b111111111u.convert() /* octal 777 */)
}

internal actual fun PosixFileSystem.variantCanonicalize(path: Path): Path {
  // Note that realpath() fails if the file doesn't exist.
  val fullpath = realpath(path.toString(), null)
    ?: throw errnoToIOException(errno)
  try {
    return Buffer().writeNullTerminated(fullpath).toPath(normalize = true)
  } finally {
    free(fullpath)
  }
}

internal actual fun PosixFileSystem.variantMove(
  source: Path,
  target: Path,
) {
  val result = rename(source.toString(), target.toString())
  if (result != 0) {
    throw errnoToIOException(errno)
  }
}

internal actual fun PosixFileSystem.variantSource(file: Path): Source {
  val openFile: CPointer<FILE> = fopen(file.toString(), "r")
    ?: throw errnoToIOException(errno)
  return FileSource(openFile)
}

internal actual fun PosixFileSystem.variantSink(file: Path, mustCreate: Boolean): Sink {
  val openFile: CPointer<FILE> = fopen(file.toString(), if (mustCreate) "wx" else "w")
    ?: throw errnoToIOException(errno)
  return FileSink(openFile)
}

internal actual fun PosixFileSystem.variantAppendingSink(file: Path, mustExist: Boolean): Sink {
  // There is a `r+` flag which we could have used to force existence of [file] but this flag
  // doesn't allow opening for appending, and we don't currently have a way to move the cursor to
  // the end of the file. We are then forcing existence non-atomically.
  if (mustExist && !exists(file)) throw IOException("$file doesn't exist.")
  val openFile: CPointer<FILE> = fopen(file.toString(), "a")
    ?: throw errnoToIOException(errno)
  return FileSink(openFile)
}

internal actual fun PosixFileSystem.variantOpenReadOnly(file: Path): FileHandle {
  val openFile: CPointer<FILE> = fopen(file.toString(), "r")
    ?: throw errnoToIOException(errno)
  return UnixFileHandle(false, openFile)
}

internal const val DEFFILEMODE = 0b110110110 /* octal 666 */

internal actual fun PosixFileSystem.variantOpenReadWrite(
  file: Path,
  mustCreate: Boolean,
  mustExist: Boolean,
): FileHandle {
  // Note that we're using open() followed by fdopen() rather than fopen() because this way we
  // can pass exactly the flags we want. Note that there's no string mode that opens for reading
  // and writing that creates if necessary. ("a+" has features but can't do random access).
  val flags = when {
    mustCreate && mustExist ->
      throw IllegalArgumentException("Cannot require mustCreate and mustExist at the same time.")
    mustCreate -> O_RDWR or O_CREAT or O_EXCL
    mustExist -> O_RDWR
    else -> O_RDWR or O_CREAT
  }

  val fid = open(file.toString(), flags, DEFFILEMODE)
  if (fid == -1) throw errnoToIOException(errno)

  // Use 'r+' to get reading and writing on the FILE, which is all we need.
  val openFile = fdopen(fid, "r+") ?: throw errnoToIOException(errno)

  return UnixFileHandle(true, openFile)
}

internal actual fun PosixFileSystem.variantCreateSymlink(source: Path, target: Path) {
  if (source.parent == null || !exists(source.parent!!)) {
    throw IOException("parent directory does not exist: ${source.parent}")
  }

  if (exists(source)) {
    throw IOException("already exists: $source")
  }

  val result = symlink(target.toString(), source.toString())
  if (result != 0) {
    throw errnoToIOException(errno)
  }
}

@OptIn(UnsafeNumber::class)
internal fun variantPread(
  file: CPointer<FILE>,
  target: CValuesRef<*>,
  byteCount: Int,
  offset: Long,
): Int = pread(fileno(file), target, byteCount.convert(), offset.convert()).convert()

@OptIn(UnsafeNumber::class)
internal fun variantPwrite(
  file: CPointer<FILE>,
  source: CValuesRef<*>,
  byteCount: Int,
  offset: Long,
): Int = pwrite(fileno(file), source, byteCount.convert(), offset.convert()).convert()

@OptIn(UnsafeNumber::class)
internal val timespec.epochMillis: Long
  get() = tv_sec * 1000L + tv_nsec / 1_000_000L

@OptIn(UnsafeNumber::class)
internal fun symlinkTarget(mode: Int, path: Path): Path? {
  if (mode and S_IFMT != S_IFLNK) return null

  // `path` is a symlink, let's resolve its target.
  memScoped {
    val buffer = allocArray<ByteVar>(PATH_MAX)
    val byteCount = readlink(path.toString(), buffer, PATH_MAX.convert())
    if (byteCount.convert<Int>() == -1) {
      throw errnoToIOException(errno)
    }
    return buffer.toKString().toPath()
  }
}
