/*
 * Copyright (C) 2026 Square, Inc.
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

import cnames.structs.DIR
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.reinterpret
import okio.Path
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir

private class AndroidPosixDirectory(private val dir: CPointer<DIR>) : PosixDirectory {
  override fun nextEntry() = readdir(dir.reinterpret())
  override fun close() {
    closedir(dir.reinterpret()) // Ignore errno from closedir.
  }
}

internal actual fun openPosixDirectory(path: Path): PosixDirectory? {
  return opendir(path.toString())?.let(::AndroidPosixDirectory)
}
