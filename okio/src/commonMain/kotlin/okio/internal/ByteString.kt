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
@file:JvmName("-ByteString") // A leading '-' hides this class from Java.

package okio.internal

import kotlin.jvm.JvmName
import okio.BASE64_URL_SAFE
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.REPLACEMENT_CODE_POINT
import okio.and
import okio.arrayRangeEquals
import okio.asUtf8ToByteArray
import okio.checkOffsetAndCount
import okio.decodeBase64ToArray
import okio.encodeBase64
import okio.isIsoControl
import okio.processUtf8CodePoints
import okio.shr
import okio.toUtf8String

// TODO Kotlin's expect classes can't have default implementations, so platform implementations
// have to call these functions. Remove all this nonsense when expect class allow actual code.

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonUtf8(): String {
  var result = utf8
  if (result == null) {
    // We don't care if we double-allocate in racy code.
    result = internalArray().toUtf8String()
    utf8 = result
  }
  return result
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonBase64(includePadding: Boolean): String = data.encodeBase64(includePadding = includePadding)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonBase64Url(includePadding: Boolean) = data.encodeBase64(includePadding = includePadding, map = BASE64_URL_SAFE)

internal val HEX_DIGIT_CHARS =
  charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonHex(): String {
  val result = CharArray(data.size * 2)
  var c = 0
  for (b in data) {
    result[c++] = HEX_DIGIT_CHARS[b shr 4 and 0xf]
    result[c++] = HEX_DIGIT_CHARS[b       and 0xf] // ktlint-disable no-multi-spaces
  }
  return result.concatToString()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonToAsciiLowercase(): ByteString {
  // Search for an uppercase character. If we don't find one, return this.
  var i = 0
  while (i < data.size) {
    var c = data[i]
    if (c < 'A'.code.toByte() || c > 'Z'.code.toByte()) {
      i++
      continue
    }

    // This string is needs to be lowercased. Create and return a new byte string.
    val lowercase = data.copyOf()
    lowercase[i++] = (c - ('A' - 'a')).toByte()
    while (i < lowercase.size) {
      c = lowercase[i]
      if (c < 'A'.code.toByte() || c > 'Z'.code.toByte()) {
        i++
        continue
      }
      lowercase[i] = (c - ('A' - 'a')).toByte()
      i++
    }
    return ByteString(lowercase)
  }
  return this
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonToAsciiUppercase(): ByteString {
  // Search for an lowercase character. If we don't find one, return this.
  var i = 0
  while (i < data.size) {
    var c = data[i]
    if (c < 'a'.code.toByte() || c > 'z'.code.toByte()) {
      i++
      continue
    }

    // This string is needs to be uppercased. Create and return a new byte string.
    val lowercase = data.copyOf()
    lowercase[i++] = (c - ('a' - 'A')).toByte()
    while (i < lowercase.size) {
      c = lowercase[i]
      if (c < 'a'.code.toByte() || c > 'z'.code.toByte()) {
        i++
        continue
      }
      lowercase[i] = (c - ('a' - 'A')).toByte()
      i++
    }
    return ByteString(lowercase)
  }
  return this
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonSubstring(beginIndex: Int, endIndex: Int): ByteString {
  require(beginIndex >= 0) { "beginIndex < 0" }
  require(endIndex <= data.size) { "endIndex > length(${data.size})" }

  val subLen = endIndex - beginIndex
  require(subLen >= 0) { "endIndex < beginIndex" }

  if (beginIndex == 0 && endIndex == data.size) {
    return this
  }
  return ByteString(data.copyOfRange(beginIndex, endIndex))
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonGetByte(pos: Int) = data[pos]

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonGetSize() = data.size

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonToByteArray() = data.copyOf()

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonInternalArray() = data

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonRangeEquals(
  offset: Int,
  other: ByteString,
  otherOffset: Int,
  byteCount: Int,
): Boolean = other.rangeEquals(otherOffset, this.data, offset, byteCount)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonRangeEquals(
  offset: Int,
  other: ByteArray,
  otherOffset: Int,
  byteCount: Int,
): Boolean {
  return (
    offset >= 0 && offset <= data.size - byteCount &&
      otherOffset >= 0 && otherOffset <= other.size - byteCount &&
      arrayRangeEquals(data, offset, other, otherOffset, byteCount)
    )
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonCopyInto(
  offset: Int,
  target: ByteArray,
  targetOffset: Int,
  byteCount: Int,
) {
  data.copyInto(target, targetOffset, offset, offset + byteCount)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonStartsWith(prefix: ByteString) =
  rangeEquals(0, prefix, 0, prefix.size)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonStartsWith(prefix: ByteArray) =
  rangeEquals(0, prefix, 0, prefix.size)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonEndsWith(suffix: ByteString) =
  rangeEquals(size - suffix.size, suffix, 0, suffix.size)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonEndsWith(suffix: ByteArray) =
  rangeEquals(size - suffix.size, suffix, 0, suffix.size)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonIndexOf(other: ByteArray, fromIndex: Int): Int {
  val limit = data.size - other.size
  for (i in maxOf(fromIndex, 0)..limit) {
    if (arrayRangeEquals(data, i, other, 0, other.size)) {
      return i
    }
  }
  return -1
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonLastIndexOf(
  other: ByteString,
  fromIndex: Int,
) = lastIndexOf(other.internalArray(), fromIndex)

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonLastIndexOf(other: ByteArray, fromIndex: Int): Int {
  val limit = data.size - other.size
  for (i in minOf(fromIndex, limit) downTo 0) {
    if (arrayRangeEquals(data, i, other, 0, other.size)) {
      return i
    }
  }
  return -1
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonEquals(other: Any?): Boolean {
  return when {
    other === this -> true
    other is ByteString -> other.size == data.size && other.rangeEquals(0, data, 0, data.size)
    else -> false
  }
}

internal fun ByteString.commonEqualsConstantTime(other: ByteString): Boolean {
  if (other === this) return true
  if (other.size != size) return false
  var result = 0
  for (i in 0 until size) {
    result = result or (this[i].toInt() xor other[i].toInt())
  }
  return result == 0
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonHashCode(): Int {
  val result = hashCode
  if (result != 0) return result
  return data.contentHashCode().also {
    hashCode = it
  }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonCompareTo(other: ByteString): Int {
  val sizeA = size
  val sizeB = other.size
  var i = 0
  val size = minOf(sizeA, sizeB)
  while (i < size) {
    val byteA = this[i] and 0xff
    val byteB = other[i] and 0xff
    if (byteA == byteB) {
      i++
      continue
    }
    return if (byteA < byteB) -1 else 1
  }
  if (sizeA == sizeB) return 0
  return if (sizeA < sizeB) -1 else 1
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun commonOf(data: ByteArray) = ByteString(data.copyOf())

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.commonToByteString(offset: Int, byteCount: Int): ByteString {
  checkOffsetAndCount(size.toLong(), offset.toLong(), byteCount.toLong())
  return ByteString(copyOfRange(offset, offset + byteCount))
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun String.commonEncodeUtf8(): ByteString {
  val byteString = ByteString(asUtf8ToByteArray())
  byteString.utf8 = this
  return byteString
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun String.commonDecodeBase64(): ByteString? {
  val decoded = decodeBase64ToArray()
  return if (decoded != null) ByteString(decoded) else null
}

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun String.commonDecodeHex(): ByteString

/** Writes the contents of this byte string to `buffer`.  */
internal fun ByteString.commonWrite(buffer: Buffer, offset: Int, byteCount: Int) {
  buffer.write(data, offset, byteCount)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteString.commonToString(): String {
  if (data.isEmpty()) return "[size=0]"

  val i = codePointIndexToCharIndex(data, 64)
  if (i == -1) {
    return if (data.size <= 64) {
      "[hex=${hex()}]"
    } else {
      "[size=${data.size} hex=${commonSubstring(0, 64).hex()}…]"
    }
  }

  val text = utf8()
  val safeText = text.substring(0, i)
    .replace("\\", "\\\\")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
  return if (i < text.length) {
    "[size=${data.size} text=$safeText…]"
  } else {
    "[text=$safeText]"
  }
}

private fun codePointIndexToCharIndex(s: ByteArray, codePointCount: Int): Int {
  var charCount = 0
  var j = 0
  s.processUtf8CodePoints(0, s.size) { c ->
    if (j++ == codePointCount) {
      return charCount
    }

    if ((c != '\n'.code && c != '\r'.code && isIsoControl(c)) ||
      c == REPLACEMENT_CODE_POINT
    ) {
      return -1
    }

    charCount += if (c < 0x10000) 1 else 2
  }
  return charCount
}

internal fun String.decodeHexIgnoreWhitespace(): ByteString {
  var i = 0
  var r = 0
  val result = ByteArray(length / 2)

  byte@ while (i < length) {
    val d1 = decodeHexDigitIgnoreWhitespace(this[i++])
    if (d1 == -1) continue

    while (i < length) {
      val d2 = decodeHexDigitIgnoreWhitespace(this[i++])
      if (d2 == -1) continue

      result[r++] = ((d1 shl 4) + d2).toByte()
      continue@byte
    }

    throw IllegalArgumentException("Expected an even number of hex digits but was: ${r * 2 + 1}")
  }

  return when {
    r < result.size -> result.toByteString(0, r)
    else -> ByteString(result)
  }
}

private fun decodeHexDigitIgnoreWhitespace(c: Char): Int {
  return when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    ' ', '\r', '\n', '\t' -> -1
    else -> throw IllegalArgumentException("Unexpected hex digit: $c")
  }
}

