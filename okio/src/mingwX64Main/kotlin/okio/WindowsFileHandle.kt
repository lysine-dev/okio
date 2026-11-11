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

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.windows.CloseHandle
import platform.windows.ERROR_HANDLE_EOF
import platform.windows.FILE_BEGIN
import platform.windows.FlushFileBuffers
import platform.windows.GetFileSizeEx
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_SET_FILE_POINTER
import platform.windows.LARGE_INTEGER
import platform.windows.NO_ERROR
import platform.windows.ReadFile
import platform.windows.SetEndOfFile
import platform.windows.SetFilePointer
import platform.windows.WriteFile
import platform.windows._OVERLAPPED

internal class WindowsFileHandle(
  readWrite: Boolean,
  private val file: HANDLE?,
) : FileHandle(readWrite) {
  override fun protectedSize(): Long {
    memScoped {
      val result = alloc<LARGE_INTEGER>()
      if (GetFileSizeEx(file, result.ptr) == 0) {
        throw lastErrorToIOException()
      }
      return result.toLong()
    }
  }

  override fun protectedRead(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ): Int {
    val bytesRead = if (array.isNotEmpty()) {
      array.usePinned { pinned ->
        variantPread(pinned.addressOf(arrayOffset), byteCount, fileOffset)
      }
    } else {
      0
    }
    if (bytesRead == 0) return -1
    return bytesRead
  }

  fun variantPread(
    target: CValuesRef<*>,
    byteCount: Int,
    offset: Long,
  ): Int {
    memScoped {
      val overlapped = alloc<_OVERLAPPED>()
      overlapped.Offset = offset.toUInt()
      overlapped.OffsetHigh = (offset ushr 32).toUInt()
      val readFileResult = ReadFile(
        hFile = file,
        lpBuffer = target.getPointer(this),
        nNumberOfBytesToRead = byteCount.toUInt(),
        lpNumberOfBytesRead = null,
        lpOverlapped = overlapped.ptr,
      )
      if (readFileResult == 0 && GetLastError().toInt() != ERROR_HANDLE_EOF) {
        throw lastErrorToIOException()
      }
      return overlapped.InternalHigh.toInt()
    }
  }

  override fun protectedWrite(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ) {
    val bytesWritten = if (array.isNotEmpty()) {
      array.usePinned { pinned ->
        variantPwrite(pinned.addressOf(arrayOffset), byteCount, fileOffset)
      }
    } else {
      0
    }
    if (bytesWritten != byteCount) throw IOException("bytesWritten=$bytesWritten")
  }

  fun variantPwrite(
    source: CValuesRef<*>,
    byteCount: Int,
    offset: Long,
  ): Int {
    memScoped {
      val overlapped = alloc<_OVERLAPPED>()
      overlapped.Offset = offset.toUInt()
      overlapped.OffsetHigh = (offset ushr 32).toUInt()
      val writeFileResult = WriteFile(
        hFile = file,
        lpBuffer = source.getPointer(this),
        nNumberOfBytesToWrite = byteCount.toUInt(),
        lpNumberOfBytesWritten = null,
        lpOverlapped = overlapped.ptr,
      )
      if (writeFileResult == 0) {
        throw lastErrorToIOException()
      }
      return overlapped.InternalHigh.toInt()
    }
  }

  override fun protectedFlush() {
    if (FlushFileBuffers(file) == 0) {
      throw lastErrorToIOException()
    }
  }

  override fun protectedResize(size: Long) {
    memScoped {
      val distanceToMoveHigh = alloc<IntVar>()
      distanceToMoveHigh.value = (size ushr 32).toInt()
      val movePointerResult = SetFilePointer(
        hFile = file,
        lDistanceToMove = size.toInt(),
        lpDistanceToMoveHigh = distanceToMoveHigh.ptr,
        dwMoveMethod = FILE_BEGIN.toUInt(),
      )
      if (movePointerResult == INVALID_SET_FILE_POINTER && GetLastError().toInt() != NO_ERROR) {
        throw lastErrorToIOException()
      }
      if (SetEndOfFile(file) == 0) {
        throw lastErrorToIOException()
      }
    }
  }

  override fun protectedClose() {
    if (CloseHandle(file) == 0) {
      throw lastErrorToIOException()
    }
  }

  private fun LARGE_INTEGER.toLong(): Long {
    return (HighPart.toLong() shl 32) + (LowPart.toLong() and 0xffffffffL)
  }
}
