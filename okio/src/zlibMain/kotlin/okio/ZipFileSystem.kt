/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import okio.Path.Companion.toPath
import okio.internal.COMPRESSION_METHOD_STORED
import okio.internal.ZipEntry
import okio.internal.readLocalHeader
import okio.internal.skipLocalHeader

/**
 * Read only access to a [zip file](https://pkware.cachefly.net/webdocs/APPNOTE/APPNOTE_6.2.0.txt)
 * and common [extra fields](https://opensource.apple.com/source/zip/zip-6/unzip/unzip/proginfo/extra.fld).
 */
internal class ZipFileSystem internal constructor(
  private val zipPath: Path,
  private val fileSystem: FileSystem,
  private val entries: Map<Path, ZipEntry>,
  private val comment: String?,
) : FileSystem() {
  override fun canonicalize(path: Path): Path {
    val canonical = canonicalizeInternal(path)
    if (canonical !in entries) {
      throw FileNotFoundException("$path")
    }
    return canonical
  }

  /** Don't throw [FileNotFoundException] if the path doesn't identify a file. */
  private fun canonicalizeInternal(path: Path): Path {
    return ROOT.resolve(path, normalize = true)
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val canonicalPath = canonicalizeInternal(path)
    val centralDirectoryEntry = entries[canonicalPath] ?: return null

    val fullEntry = when {
      centralDirectoryEntry.offset != -1L -> {
        fileSystem.openReadOnly(zipPath).use { fileHandle ->
          return@use fileHandle.source(centralDirectoryEntry.offset).buffer().use { source ->
            source.readLocalHeader(centralDirectoryEntry)
          }
        }
      }

      else -> centralDirectoryEntry
    }

    return FileMetadata(
      isRegularFile = !fullEntry.isDirectory,
      isDirectory = fullEntry.isDirectory,
      symlinkTarget = null,
      size = if (fullEntry.isDirectory) null else fullEntry.size,
      createdAtMillis = fullEntry.createdAtMillis,
      lastModifiedAtMillis = fullEntry.lastModifiedAtMillis,
      lastAccessedAtMillis = fullEntry.lastAccessedAtMillis,
    )
  }

  override fun openReadOnly(file: Path): FileHandle {
    throw UnsupportedOperationException("not implemented yet!")
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    throw IOException("zip entries are not writable")
  }

  override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)!!

  override fun listOrNull(dir: Path): List<Path>? = list(dir, throwOnFailure = false)

  private fun list(dir: Path, throwOnFailure: Boolean): List<Path>? {
    val canonicalDir = canonicalizeInternal(dir)
    val entry = entries[canonicalDir]
      ?: if (throwOnFailure) throw IOException("not a directory: $dir") else return null
    return entry.children.toList()
  }

  @Throws(IOException::class)
  override fun source(file: Path): Source {
    val canonicalPath = canonicalizeInternal(file)
    val entry = entries[canonicalPath] ?: throw FileNotFoundException("no such file: $file")
    val source = fileSystem.openReadOnly(zipPath).use { fileHandle ->
      fileHandle.source(entry.offset).buffer()
    }
    source.skipLocalHeader()

    return when (entry.compressionMethod) {
      COMPRESSION_METHOD_STORED -> {
        source.limit(entry.size)
      }
      else -> {
        val inflaterSource = InflaterSource(
          source.limit(entry.compressedSize),
          Inflater(true),
        )
        inflaterSource.limit(entry.size, throwIfSourceIsLonger = true)
      }
    }
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    throw IOException("zip file systems are read-only")
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    throw IOException("zip file systems are read-only")
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean): Unit =
    throw IOException("zip file systems are read-only")

  override fun atomicMove(source: Path, target: Path): Unit =
    throw IOException("zip file systems are read-only")

  override fun delete(path: Path, mustExist: Boolean): Unit =
    throw IOException("zip file systems are read-only")

  override fun createSymlink(source: Path, target: Path): Unit =
    throw IOException("zip file systems are read-only")

  private companion object {
    val ROOT = "/".toPath()
  }
}
