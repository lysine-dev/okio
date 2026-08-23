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

import java.nio.file.FileSystem as JavaNioFileSystem
import kotlin.Throws
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import okio.Path.Companion.toPath
import okio.internal.ResourceFileSystem
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

  fun listRecursively(dir: Path): Sequence<Path> = listRecursively(dir, followSymlinks = false)

  @Throws(IOException::class)
  actual abstract fun openReadOnly(file: Path): FileHandle

  @Throws(IOException::class)
  actual abstract fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle

  @Throws(IOException::class)
  fun openReadWrite(file: Path): FileHandle =
    openReadWrite(file, mustCreate = false, mustExist = false)

  @Throws(IOException::class)
  actual abstract fun source(file: Path): Source

  @Throws(IOException::class)
  @JvmName("-read")
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
  fun sink(file: Path): Sink = sink(file, mustCreate = false)

  @Throws(IOException::class)
  @JvmName("-write")
  actual inline fun <T> write(
    file: Path,
    mustCreate: Boolean,
    writerAction: BufferedSink.() -> T,
  ): T {
    contract {
      callsInPlace(writerAction, InvocationKind.EXACTLY_ONCE)
    }

    return sink(file, mustCreate = mustCreate).buffer().use {
      it.writerAction()
    }
  }

  @Throws(IOException::class)
  actual abstract fun appendingSink(file: Path, mustExist: Boolean): Sink

  @Throws(IOException::class)
  fun appendingSink(file: Path): Sink = appendingSink(file, mustExist = false)

  @Throws(IOException::class)
  actual abstract fun createDirectory(dir: Path, mustCreate: Boolean)

  @Throws(IOException::class)
  fun createDirectory(dir: Path) = createDirectory(dir, mustCreate = false)

  @Throws(IOException::class)
  actual fun createDirectories(dir: Path, mustCreate: Boolean): Unit =
    commonCreateDirectories(dir, mustCreate)

  @Throws(IOException::class)
  fun createDirectories(dir: Path): Unit = createDirectories(dir, mustCreate = false)

  @Throws(IOException::class)
  actual abstract fun atomicMove(source: Path, target: Path)

  @Throws(IOException::class)
  actual open fun copy(source: Path, target: Path): Unit = commonCopy(source, target)

  @Throws(IOException::class)
  actual abstract fun delete(path: Path, mustExist: Boolean)

  @Throws(IOException::class)
  fun delete(path: Path) = delete(path, mustExist = false)

  @Throws(IOException::class)
  actual open fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean): Unit =
    commonDeleteRecursively(fileOrDirectory, mustExist)

  @Throws(IOException::class)
  fun deleteRecursively(fileOrDirectory: Path): Unit =
    deleteRecursively(fileOrDirectory, mustExist = false)

  @Throws(IOException::class)
  actual abstract fun createSymlink(source: Path, target: Path)

  @Throws(IOException::class)
  actual override fun close() {
  }

  @Throws(IOException::class)
  actual open fun lock(
    path: Path,
    mode: LockMode,
  ): FileLock {
    throw IOException("This file system does not support locking.")
  }

  actual companion object {
    /**
     * The current process's host file system. Use this instance directly, or dependency inject a
     * [FileSystem] to make code testable.
     */
    @JvmField
    val SYSTEM: FileSystem = run {
      try {
        Class.forName("java.nio.file.Files")
        return@run NioSystemFileSystem()
      } catch (e: ClassNotFoundException) {
        return@run JvmSystemFileSystem()
      }
    }

    @JvmField
    actual val SYSTEM_TEMPORARY_DIRECTORY: Path = System.getProperty("java.io.tmpdir").toPath()

    /**
     * A read-only file system holding the classpath resources of the current process. If a resource
     * is available with [ClassLoader.getResource], it is also available via this file system.
     *
     * In applications that compose multiple class loaders, this holds only the resources of
     * whichever class loader includes Okio classes. Use [ClassLoader.asResourceFileSystem] for the
     * resources of a specific class loader.
     *
     * This file system does not need to be closed. Calling its close function does nothing.
     */
    @JvmField
    val RESOURCES: FileSystem = ResourceFileSystem(
      classLoader = ResourceFileSystem::class.java.classLoader,
      indexEagerly = false,
    )

    /**
     * Closing the returned file system will close the underlying [java.nio.file.FileSystem].
     *
     * Note that the [default file system][java.nio.file.FileSystems.getDefault] is not closeable
     * and calling its close function will throw an [UnsupportedOperationException].
     */
    @JvmName("get")
    @JvmStatic
    fun JavaNioFileSystem.asOkioFileSystem(): FileSystem = NioFileSystemWrappingFileSystem(this)
  }
}
