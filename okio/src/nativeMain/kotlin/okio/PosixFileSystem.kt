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

import okio.Path.Companion.toPath
import platform.posix.EEXIST
import platform.posix.errno

internal object PosixFileSystem : FileSystem() {
  internal val SELF_DIRECTORY_ENTRY = ".".toPath()
  internal val PARENT_DIRECTORY_ENTRY = "..".toPath()

  override fun canonicalize(path: Path) = variantCanonicalize(path)

  override fun metadataOrNull(path: Path) = variantMetadataOrNull(path)

  override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)!!

  override fun listOrNull(dir: Path): List<Path>? = list(dir, throwOnFailure = false)

  private fun list(dir: Path, throwOnFailure: Boolean): List<Path>? = variantList(dir, throwOnFailure)

  override fun openReadOnly(file: Path) = variantOpenReadOnly(file)

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    return variantOpenReadWrite(file, mustCreate = mustCreate, mustExist = mustExist)
  }

  override fun source(file: Path) = variantSource(file)

  override fun sink(file: Path, mustCreate: Boolean) = variantSink(file, mustCreate)

  override fun appendingSink(file: Path, mustExist: Boolean) = variantAppendingSink(file, mustExist)

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    val result = variantMkdir(dir)
    if (result != 0) {
      if (errno == EEXIST && !mustCreate) return // Already exists.
      throw errnoToIOException(errno)
    }
  }

  override fun atomicMove(
    source: Path,
    target: Path,
  ) {
    variantMove(source, target)
  }

  override fun delete(path: Path, mustExist: Boolean) {
    variantDelete(path, mustExist)
  }

  override fun createSymlink(source: Path, target: Path) = variantCreateSymlink(source, target)

  override fun toString() = "PosixSystemFileSystem"
}
