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

package okio

import okio.internal.HashFunction
import okio.internal.Hmac
import okio.internal.Md5
import okio.internal.Sha1
import okio.internal.Sha256
import okio.internal.Sha512
import okio.internal.commonBase64
import okio.internal.commonBase64Url
import okio.internal.commonCompareTo
import okio.internal.commonCopyInto
import okio.internal.commonDecodeBase64
import okio.internal.commonDecodeHex
import okio.internal.commonEncodeUtf8
import okio.internal.commonEndsWith
import okio.internal.commonEquals
import okio.internal.commonEqualsConstantTime
import okio.internal.commonGetByte
import okio.internal.commonGetSize
import okio.internal.commonHashCode
import okio.internal.commonHex
import okio.internal.commonIndexOf
import okio.internal.commonInternalArray
import okio.internal.commonLastIndexOf
import okio.internal.commonOf
import okio.internal.commonRangeEquals
import okio.internal.commonStartsWith
import okio.internal.commonSubstring
import okio.internal.commonToAsciiLowercase
import okio.internal.commonToAsciiUppercase
import okio.internal.commonToByteArray
import okio.internal.commonToByteString
import okio.internal.commonToString
import okio.internal.commonUtf8
import okio.internal.commonWrite
import okio.internal.decodeHexIgnoreWhitespace

actual open class ByteString
internal actual constructor(
  internal actual val data: ByteArray,
) : Comparable<ByteString> {
  @Suppress("SetterBackingFieldAssignment")
  internal actual var hashCode: Int = 0 // 0 if unknown.
    set(value) {
      // Do nothing to avoid IllegalImmutabilityException.
    }

  @Suppress("SetterBackingFieldAssignment")
  internal actual var utf8: String? = null
    set(value) {
      // Do nothing to avoid IllegalImmutabilityException.
    }

  actual open fun utf8(): String = commonUtf8()

  actual open fun base64(includePadding: Boolean): String = commonBase64(includePadding = includePadding)

  actual open fun base64Url(includePadding: Boolean): String = commonBase64Url(includePadding = includePadding)

  actual open fun hex(): String = commonHex()

  actual fun md5() = digest(Md5())

  actual fun sha1() = digest(Sha1())

  actual fun sha256() = digest(Sha256())

  actual fun sha512() = digest(Sha512())

  /** Returns the 160-bit SHA-1 HMAC of this byte string.  */
  actual fun hmacSha1(key: ByteString) = digest(Hmac.sha1(key))

  /** Returns the 256-bit SHA-256 HMAC of this byte string.  */
  actual fun hmacSha256(key: ByteString) = digest(Hmac.sha256(key))

  /** Returns the 512-bit SHA-512 HMAC of this byte string.  */
  actual fun hmacSha512(key: ByteString) = digest(Hmac.sha512(key))

  internal open fun digest(hashFunction: HashFunction): ByteString {
    hashFunction.update(data, 0, size)
    val digestBytes = hashFunction.digest()
    return ByteString(digestBytes)
  }

  actual open fun toAsciiLowercase(): ByteString = commonToAsciiLowercase()

  actual open fun toAsciiUppercase(): ByteString = commonToAsciiUppercase()

  actual open fun substring(beginIndex: Int, endIndex: Int): ByteString =
    commonSubstring(beginIndex, endIndex)

  internal actual open fun internalGet(pos: Int): Byte {
    if (pos >= size || pos < 0) throw ArrayIndexOutOfBoundsException("size=$size pos=$pos")
    return commonGetByte(pos)
  }

  actual operator fun get(index: Int): Byte = internalGet(index)

  actual val size
    get() = getSize()

  internal actual open fun getSize() = commonGetSize()

  actual open fun toByteArray() = commonToByteArray()

  internal actual open fun internalArray() = commonInternalArray()

  internal actual open fun write(buffer: Buffer, offset: Int, byteCount: Int) =
    commonWrite(buffer, offset, byteCount)

  actual open fun rangeEquals(
    offset: Int,
    other: ByteString,
    otherOffset: Int,
    byteCount: Int,
  ): Boolean = commonRangeEquals(offset, other, otherOffset, byteCount)

  actual open fun rangeEquals(
    offset: Int,
    other: ByteArray,
    otherOffset: Int,
    byteCount: Int,
  ): Boolean = commonRangeEquals(offset, other, otherOffset, byteCount)

  actual open fun copyInto(
    offset: Int,
    target: ByteArray,
    targetOffset: Int,
    byteCount: Int,
  ) = commonCopyInto(offset, target, targetOffset, byteCount)

  actual fun startsWith(prefix: ByteString) = commonStartsWith(prefix)

  actual fun startsWith(prefix: ByteArray) = commonStartsWith(prefix)

  actual fun endsWith(suffix: ByteString) = commonEndsWith(suffix)

  actual fun endsWith(suffix: ByteArray) = commonEndsWith(suffix)

  actual fun indexOf(other: ByteString, fromIndex: Int) = indexOf(other.internalArray(), fromIndex)

  actual open fun indexOf(other: ByteArray, fromIndex: Int) = commonIndexOf(other, fromIndex)

  actual fun lastIndexOf(other: ByteString, fromIndex: Int) = commonLastIndexOf(other, fromIndex)

  actual open fun lastIndexOf(other: ByteArray, fromIndex: Int) = commonLastIndexOf(other, fromIndex)

  actual override fun equals(other: Any?) = commonEquals(other)

  actual fun equals(other: ByteString, constantTime: Boolean) =
    if (constantTime) commonEqualsConstantTime(other) else this == other

  actual override fun hashCode() = commonHashCode()

  actual override fun compareTo(other: ByteString) = commonCompareTo(other)

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  actual override fun toString() = commonToString()

  actual companion object {
    actual val EMPTY: ByteString = ByteString(byteArrayOf())

    actual fun of(vararg data: Byte) = commonOf(data)

    actual fun ByteArray.toByteString(offset: Int, byteCount: Int): ByteString =
      commonToByteString(offset, byteCount)

    actual fun String.encodeUtf8(): ByteString = commonEncodeUtf8()

    actual fun String.decodeBase64(): ByteString? = commonDecodeBase64()

    actual fun String.decodeHex() = commonDecodeHex()

    actual fun String.decodeHex(ignoreWhitespace: Boolean) = when {
      ignoreWhitespace -> decodeHexIgnoreWhitespace()
      else -> commonDecodeHex()
    }
  }
}
