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
@file:JvmName("-FileSystem") // A leading '-' hides this class from Java.

package okio.internal

import kotlin.jvm.JvmName
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use

/**
 * Returns metadata of the file, directory, or object identified by [path].
 *
 * @throws IOException if [path] does not exist or its metadata cannot be read.
 */
@Throws(IOException::class)
internal fun FileSystem.commonMetadata(path: Path): FileMetadata {
  return metadataOrNull(path) ?: throw FileNotFoundException("no such file: $path")
}

@Throws(IOException::class)
internal fun FileSystem.commonExists(path: Path): Boolean {
  return metadataOrNull(path) != null
}

@Throws(IOException::class)
internal fun FileSystem.commonCreateDirectories(dir: Path, mustCreate: Boolean) {
  // Compute the sequence of directories to create.
  val directories = ArrayDeque<Path>()
  var path: Path? = dir
  while (path != null && !exists(path)) {
    directories.addFirst(path)
    path = path.parent
  }

  if (mustCreate && directories.isEmpty()) throw IOException("$dir already exists.")

  // Create them.
  for (toCreate in directories) {
    // We know we are creating new directories by now so we don't have to pass down `mustCreate`.
    createDirectory(toCreate)
  }
}

@Throws(IOException::class)
internal fun FileSystem.commonCopy(source: Path, target: Path) {
  source(source).use { bytesIn ->
    sink(target).buffer().use { bytesOut ->
      bytesOut.writeAll(bytesIn)
    }
  }
}

@Throws(IOException::class)
internal fun FileSystem.commonDeleteRecursively(fileOrDirectory: Path, mustExist: Boolean) {
  val sequence = sequence {
    collectRecursively(
      fileSystem = this@commonDeleteRecursively,
      stack = ArrayDeque(),
      path = fileOrDirectory,
      followSymlinks = false,
      postorder = true,
    )
  }
  val iterator = sequence.iterator()
  while (iterator.hasNext()) {
    val toDelete = iterator.next()
    delete(toDelete, mustExist = mustExist && !iterator.hasNext())
  }
}

@Throws(IOException::class)
internal fun FileSystem.commonListRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> {
  return sequence {
    val stack = ArrayDeque<Path>()
    stack.addLast(dir)
    for (child in list(dir)) {
      collectRecursively(
        fileSystem = this@commonListRecursively,
        stack = stack,
        path = child,
        followSymlinks = followSymlinks,
        postorder = false,
      )
    }
  }
}

internal suspend fun SequenceScope<Path>.collectRecursively(
  fileSystem: FileSystem,
  stack: ArrayDeque<Path>,
  path: Path,
  followSymlinks: Boolean,
  postorder: Boolean,
) {
  // For listRecursively, visit enclosing directory first.
  if (!postorder) {
    yield(path)
  }

  val children =
    (if (followSymlinks || fileSystem.metadataOrNull(path)?.symlinkTarget == null) {
      fileSystem.listOrNull(path)
    } else {
      null
    }) ?: listOf()

  if (children.isNotEmpty()) {
    // Figure out if path is a symlink and detect symlink cycles.
    var symlinkPath = path
    var symlinkCount = 0
    while (true) {
      if (followSymlinks && symlinkPath in stack) throw IOException("symlink cycle at $path")
      symlinkPath = fileSystem.symlinkTarget(symlinkPath) ?: break
      symlinkCount++
    }

    // Recursively visit children.
    if (followSymlinks || symlinkCount == 0) {
      stack.addLast(symlinkPath)
      try {
        for (child in children) {
          collectRecursively(fileSystem, stack, child, followSymlinks, postorder)
        }
      } finally {
        stack.removeLast()
      }
    }
  }

  // For deleteRecursively, visit enclosing directory last.
  if (postorder) {
    yield(path)
  }
}

/** Returns a resolved path to the symlink target, resolving it if necessary. */
@Throws(IOException::class)
internal fun FileSystem.symlinkTarget(path: Path): Path? {
  val target = metadata(path).symlinkTarget ?: return null
  return path.parent!!.div(target)
}
