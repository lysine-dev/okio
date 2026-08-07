/*
 * Copyright (C) 2025 Square, Inc.
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
@file:JvmName("-ByteStringNonJs") // A leading '-' hides this class from Java.

package okio.internal

import kotlin.jvm.JvmName
import okio.ByteString

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun String.commonDecodeHex(): ByteString {
  require(length % 2 == 0) { "Unexpected hex string: $this" }

  val result = ByteArray(length / 2) { i ->
    val d1 = decodeHexDigit(this[i * 2]) shl 4
    val d2 = decodeHexDigit(this[i * 2 + 1])
    (d1 + d2).toByte()
  }
  return ByteString(result)
}

private fun decodeHexDigit(c: Char): Int {
  return when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> throw IllegalArgumentException("Unexpected hex digit: $c")
  }
}
