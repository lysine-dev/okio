// ktlint-disable filename
/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio.internal

import okio.ArrayIndexOutOfBoundsException
import okio.processUtf16Chars
import okio.processUtf8Bytes

// TODO For benchmarking, these methods need to be available but preferably invisible
// to everything else. Putting them in this file, `-Utf8.kt`, makes them invisible to
// Java but still visible to Kotlin.

internal fun ByteArray.commonToUtf8String(beginIndex: Int = 0, endIndex: Int = size): String {
  if (beginIndex < 0 || endIndex > size || beginIndex > endIndex) {
    throw ArrayIndexOutOfBoundsException("size=$size beginIndex=$beginIndex endIndex=$endIndex")
  }
  val chars = CharArray(endIndex - beginIndex)

  var length = 0
  processUtf16Chars(beginIndex, endIndex) { c ->
    chars[length++] = c
  }

  return chars.concatToString(0, length)
}

internal fun String.commonAsUtf8ToByteArray(): ByteArray {
  val bytes = ByteArray(4 * length)

  // Assume ASCII until a UTF-8 code point is observed. This is ugly but yields
  // about a 2x performance increase for pure ASCII.
  for (index in 0 until length) {
    val b0 = this[index]
    if (b0 >= '\u0080') {
      var size = index
      processUtf8Bytes(index, length) { c ->
        bytes[size++] = c
      }
      return bytes.copyOf(size)
    }
    bytes[index] = b0.code.toByte()
  }

  return bytes.copyOf(length)
}
