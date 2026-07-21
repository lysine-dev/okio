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

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * An immutable sequence of bytes.
 *
 * Byte strings compare lexicographically as a sequence of **unsigned** bytes. That is, the byte
 * string `ff` sorts after `00`. This is counter to the sort order of the corresponding bytes,
 * where `-1` sorts before `0`.
 *
 * **Full disclosure:** this class provides untrusted input and output streams with raw access to
 * the underlying byte array. A hostile stream implementation could keep a reference to the mutable
 * byte string, violating the immutable guarantee of this class. For this reason a byte string's
 * immutability guarantee cannot be relied upon for security in applets and other environments that
 * run both trusted and untrusted code in the same process.
 */
expect open class ByteString
// Trusted internal constructor doesn't clone data.
internal constructor(data: ByteArray) : Comparable<ByteString> {
  internal val data: ByteArray

  internal var hashCode: Int
  internal var utf8: String?

  /** Constructs a new `String` by decoding the bytes as `UTF-8`. */
  fun utf8(): String

  /**
   * Returns this byte string encoded as [Base64](http://www.ietf.org/rfc/rfc2045.txt). In violation
   * of the RFC, the returned string does not wrap lines at 76 columns.
   */
  fun base64(includePadding: Boolean = true): String

  /** Returns this byte string encoded as [URL-safe Base64](http://www.ietf.org/rfc/rfc4648.txt). */
  fun base64Url(includePadding: Boolean = true): String

  /** Returns this byte string encoded in hexadecimal. */
  fun hex(): String

  /**
   * Returns the 128-bit MD5 hash of this byte string.
   *
   * MD5 has been vulnerable to collisions since 2004. It should not be used in new code.
   */
  fun md5(): ByteString

  /**
   * Returns the 160-bit SHA-1 hash of this byte string.
   *
   * SHA-1 has been vulnerable to collisions since 2017. It should not be used in new code.
   */
  fun sha1(): ByteString

  /** Returns the 256-bit SHA-256 hash of this byte string. */
  fun sha256(): ByteString

  /** Returns the 512-bit SHA-512 hash of this byte string. */
  fun sha512(): ByteString

  /** Returns the 160-bit SHA-1 HMAC of this byte string. */
  fun hmacSha1(key: ByteString): ByteString

  /** Returns the 256-bit SHA-256 HMAC of this byte string. */
  fun hmacSha256(key: ByteString): ByteString

  /** Returns the 512-bit SHA-512 HMAC of this byte string. */
  fun hmacSha512(key: ByteString): ByteString

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'A' through 'Z' replaced
   * with the corresponding byte in 'a' through 'z'. Returns this byte string if it contains no
   * bytes in 'A' through 'Z'.
   */
  fun toAsciiLowercase(): ByteString

  /**
   * Returns a byte string that is a substring of this byte string, beginning at the specified
   * `beginIndex` and ends at the specified `endIndex`. Returns this byte string if `beginIndex` is
   * 0 and `endIndex` is the length of this byte string.
   */
  fun substring(beginIndex: Int = 0, endIndex: Int = size): ByteString

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'a' through 'z' replaced
   * with the corresponding byte in 'A' through 'Z'. Returns this byte string if it contains no
   * bytes in 'a' through 'z'.
   */
  fun toAsciiUppercase(): ByteString

  /** Returns the byte at `pos`. */
  internal fun internalGet(pos: Int): Byte

  /** Returns the byte at `index`. */
  @JvmName("getByte")
  operator fun get(index: Int): Byte

  /** Returns the number of bytes in this ByteString. */
  val size: Int
    @JvmName("size")
    get

  // Hack to work around Kotlin's limitation for using JvmName on open/override vals/funs
  internal fun getSize(): Int

  /** Returns a byte array containing a copy of the bytes in this `ByteString`. */
  fun toByteArray(): ByteArray

  /** Writes the contents of this byte string to `buffer`. */
  internal fun write(buffer: Buffer, offset: Int, byteCount: Int)

  /** Returns the bytes of this string without a defensive copy. Do not mutate!  */
  internal fun internalArray(): ByteArray

  /**
   * Returns true if the bytes of this in `[offset..offset+byteCount)` equal the bytes of `other` in
   * `[otherOffset..otherOffset+byteCount)`. Returns false if either range is out of bounds.
   */
  fun rangeEquals(offset: Int, other: ByteString, otherOffset: Int, byteCount: Int): Boolean

  /**
   * Returns true if the bytes of this in `[offset..offset+byteCount)` equal the bytes of `other` in
   * `[otherOffset..otherOffset+byteCount)`. Returns false if either range is out of bounds.
   */
  fun rangeEquals(offset: Int, other: ByteArray, otherOffset: Int, byteCount: Int): Boolean

  /**
   * Copies bytes of this in `[offset..offset+byteCount]` to other in
   * `[targetOffset..targetOffset+byteCount]`.
   *
   * @throws IndexOutOfBoundsException if either range is out of bounds.
   */
  fun copyInto(offset: Int = 0, target: ByteArray, targetOffset: Int = 0, byteCount: Int)

  fun startsWith(prefix: ByteString): Boolean

  fun startsWith(prefix: ByteArray): Boolean

  fun endsWith(suffix: ByteString): Boolean

  fun endsWith(suffix: ByteArray): Boolean

  @JvmOverloads
  fun indexOf(other: ByteString, fromIndex: Int = 0): Int

  @JvmOverloads
  fun indexOf(other: ByteArray, fromIndex: Int = 0): Int

  fun lastIndexOf(other: ByteString, fromIndex: Int = size): Int

  fun lastIndexOf(other: ByteArray, fromIndex: Int = size): Int

  override fun equals(other: Any?): Boolean

  /**
   * Returns true if the bytes of this equal the bytes of `other`. If [constantTime] is true this
   * always inspects every byte and does not short-circuit on the first mismatch, so its running
   * time does not depend on where the byte strings differ. Use that for timing-safe comparison of
   * secrets like hashes or message authentication codes. If [constantTime] is false this behaves
   * like [equals] and may return as soon as a mismatch is found.
   */
  fun equals(other: ByteString, constantTime: Boolean): Boolean

  override fun hashCode(): Int

  override fun compareTo(other: ByteString): Int

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  override fun toString(): String

  companion object {
    /** A singleton empty `ByteString`. */
    val EMPTY: ByteString

    /** Returns a new byte string containing a clone of the bytes of `data`. */
    @JvmStatic
    fun of(vararg data: Byte): ByteString

    /**
     * Returns a new [ByteString] containing a copy of `byteCount` bytes of this [ByteArray]
     * starting at `offset`.
     */
    @JvmStatic
    fun ByteArray.toByteString(offset: Int = 0, byteCount: Int = size): ByteString

    /** Returns a new byte string containing the `UTF-8` bytes of this [String]. */
    @JvmStatic
    fun String.encodeUtf8(): ByteString

    /**
     * Decodes the Base64-encoded bytes and returns their value as a byte string. Returns null if
     * this is not a Base64-encoded sequence of bytes.
     */
    @JvmStatic
    fun String.decodeBase64(): ByteString?

    /** Decodes the hex-encoded bytes and returns their value a byte string. */
    @JvmStatic
    fun String.decodeHex(): ByteString

    /**
     * Decodes the hex-encoded bytes and returns their value a byte string.
     *
     * @param ignoreWhitespace true to skip ASCII whitespace characters (space, tab, carriage
     *   return and line feed). Otherwise, this throws if whitespace is present otherwise.
     */
    @JvmStatic
    fun String.decodeHex(ignoreWhitespace: Boolean): ByteString
  }
}
