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
package okio

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import okio.internal.commonCopy
import okio.internal.commonCreateDirectories
import okio.internal.commonDeleteRecursively
import okio.internal.commonExists
import okio.internal.commonListRecursively
import okio.internal.commonMetadata

actual abstract class FileSystem : Closeable {
  @Throws(IOException::class)
  actual abstract fun canonicalize(path: Path): Path

  @Throws(IOException::class)
  actual fun metadata(path: Path): FileMetadata = commonMetadata(path)

  @Throws(IOException::class)
  actual abstract fun metadataOrNull(path: Path): FileMetadata?

  @Throws(IOException::class)
  actual fun exists(path: Path): Boolean = commonExists(path)

  @Throws(IOException::class)
  actual abstract fun list(dir: Path): List<Path>

  actual abstract fun listOrNull(dir: Path): List<Path>?

  actual open fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> =
    commonListRecursively(dir, followSymlinks)

  @Throws(IOException::class)
  actual abstract fun openReadOnly(file: Path): FileHandle

  @Throws(IOException::class)
  actual abstract fun openReadWrite(
    file: Path,
    mustCreate: Boolean,
    mustExist: Boolean,
  ): FileHandle

  @Throws(IOException::class)
  actual abstract fun source(file: Path): Source

  @Throws(IOException::class)
  actual inline fun <T> read(file: Path, readerAction: BufferedSource.() -> T): T {
    contract {
      callsInPlace(readerAction, InvocationKind.EXACTLY_ONCE)
    }

    return source(file).buffer().use {
      it.readerAction()
    }
  }

  @Throws(IOException::class)
  actual abstract fun sink(file: Path, mustCreate: Boolean): Sink

  @Throws(IOException::class)
  actual inline fun <T> write(
    file: Path,
    mustCreate: Boolean,
    writerAction: BufferedSink.() -> T,
  ): T {
    contract {
      callsInPlace(writerAction, InvocationKind.EXACTLY_ONCE)
    }

    return sink(file, mustCreate).buffer().use {
      it.writerAction()
    }
  }

  @Throws(IOException::class)
  actual abstract fun appendingSink(file: Path, mustExist: Boolean): Sink

  @Throws(IOException::class)
  actual abstract fun createDirectory(dir: Path, mustCreate: Boolean)

  @Throws(IOException::class)
  actual fun createDirectories(dir: Path, mustCreate: Boolean): Unit =
    commonCreateDirectories(dir, mustCreate)

  @Throws(IOException::class)
  actual abstract fun atomicMove(source: Path, target: Path)

  @Throws(IOException::class)
  actual open fun copy(source: Path, target: Path): Unit = commonCopy(source, target)

  @Throws(IOException::class)
  actual abstract fun delete(path: Path, mustExist: Boolean)

  @Throws(IOException::class)
  actual open fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean): Unit =
    commonDeleteRecursively(fileOrDirectory, mustExist)

  @Throws(IOException::class)
  actual abstract fun createSymlink(source: Path, target: Path)

  actual override fun close() {
  }

  actual companion object {
    /**
     * The current process's host file system. Use this instance directly, or dependency inject a
     * [FileSystem] to make code testable.
     */
    val SYSTEM: FileSystem = PosixFileSystem

    actual val SYSTEM_TEMPORARY_DIRECTORY: Path = PLATFORM_TEMPORARY_DIRECTORY
  }
}

/*
 * JVM and native platforms do offer a [SYSTEM] [FileSystem], however we cannot refine an 'expect' companion object.
 * Therefore an extension property is provided, which on respective platforms (here JVM) will be shadowed by the
 * original implementation.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
actual inline val FileSystem.Companion.SYSTEM: FileSystem
  get() = SYSTEM
