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

import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.internal.linux.AT_FDCWD
import okio.internal.linux.AT_SYMLINK_NOFOLLOW
import okio.internal.linux.STATX_BASIC_STATS
import okio.internal.linux.STATX_BTIME
import okio.internal.linux.S_IFDIR
import okio.internal.linux.S_IFMT
import okio.internal.linux.S_IFREG
import okio.internal.linux.__NR_statx
import okio.internal.linux.statx
import okio.internal.linux.statx_timestamp
import platform.posix.ENOENT
import platform.posix.ENOSYS
import platform.posix.errno
import platform.posix.lstat
import platform.posix.stat
import platform.posix.syscall

internal expect var isStatXSupported: Boolean

/**
 * Prefer `statx()` if it's available. Fall back to `stat()` which doesn't have a field for
 * `createdAt`.
 */
@OptIn(UnsafeNumber::class)
internal actual fun PosixFileSystem.variantMetadataOrNull(path: Path): FileMetadata? {
  memScoped {
    if (isStatXSupported) {
      val statx = alloc<statx>()
      val result = syscall(
        __NR_statx.convert(),
        AT_FDCWD,
        path.toString(),
        AT_SYMLINK_NOFOLLOW,
        STATX_BASIC_STATS or STATX_BTIME,
        statx.ptr,
      ).convert<Int>()
      if (result == 0) {
        return FileMetadata(
          isRegularFile = statx.stx_mode.toInt() and S_IFMT == S_IFREG,
          isDirectory = statx.stx_mode.toInt() and S_IFMT == S_IFDIR,
          symlinkTarget = symlinkTarget(statx.stx_mode.toInt(), path),
          size = statx.stx_size.toLong(),
          createdAtMillis = when {
            statx.stx_mask and STATX_BTIME != 0U -> statx.stx_btime.epochMillis
            else -> statx.stx_mtime.epochMillis
          },
          lastModifiedAtMillis = statx.stx_mtime.epochMillis,
          lastAccessedAtMillis = statx.stx_atime.epochMillis,
        )
      }

      // Recover if statx() isn't available. It first appeared in Linux in 4.11 (2017-04-30) and
      // Android in API 30 (2020-09-08).
      if (errno == ENOSYS) {
        isStatXSupported = false
      } else {
        if (errno == ENOENT) return null
        throw errnoToIOException(errno)
      }
    }

    val stat = alloc<stat>()
    if (lstat(path.toString(), stat.ptr) == 0) {
      return FileMetadata(
        isRegularFile = stat.st_mode.toInt() and S_IFMT == S_IFREG,
        isDirectory = stat.st_mode.toInt() and S_IFMT == S_IFDIR,
        symlinkTarget = symlinkTarget(stat.st_mode.toInt(), path),
        size = stat.st_size,
        createdAtMillis = stat.st_mtim.epochMillis,
        lastModifiedAtMillis = stat.st_mtim.epochMillis,
        lastAccessedAtMillis = stat.st_atim.epochMillis,
      )
    }

    if (errno == ENOENT) return null
    throw errnoToIOException(errno)
  }
}

@OptIn(UnsafeNumber::class)
internal val statx_timestamp.epochMillis: Long
  get() = tv_sec * 1000L + tv_nsec.toLong() / 1_000_000L
