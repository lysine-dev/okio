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

import kotlinx.cinterop.UShortVarOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.DWORD
import platform.windows.ERROR_FILE_NOT_FOUND
import platform.windows.ERROR_PATH_NOT_FOUND
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FormatMessageW
import platform.windows.GetLastError
import platform.windows.LANG_NEUTRAL
import platform.windows.SUBLANG_DEFAULT

internal fun lastErrorToIOException(): IOException {
  val lastError = GetLastError()
  return when (lastError.toInt()) {
    ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND -> FileNotFoundException(lastErrorString(lastError))
    else -> IOException(lastErrorString(lastError))
  }
}

internal fun lastErrorString(lastError: DWORD): String {
  memScoped {
    val messageMaxSize = 2048
    val message = allocArray<UShortVarOf<UShort>>(messageMaxSize)
    FormatMessageW(
      dwFlags = (FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS).toUInt(),
      lpSource = null,
      dwMessageId = lastError,
      dwLanguageId = (SUBLANG_DEFAULT * 1024 + LANG_NEUTRAL).toUInt(), // MAKELANGID macro.
      lpBuffer = message,
      nSize = messageMaxSize.toUInt(),
      Arguments = null,
    )
    return message.toKString()
  }
}
