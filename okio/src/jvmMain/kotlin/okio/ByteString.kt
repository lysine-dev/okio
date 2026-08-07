/*
 * Copyright 2014 Square Inc.
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

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
) : Serializable, Comparable<ByteString> {
  @Transient internal actual var hashCode: Int = 0 // Lazily computed; 0 if unknown.
  @Transient internal actual var utf8: String? = null // Lazily computed.

  actual open fun utf8(): String = commonUtf8()

  /** Constructs a new `String` by decoding the bytes using `charset`.  */
  open fun string(charset: Charset) = String(data, charset)

  @JvmOverloads
  actual open fun base64(includePadding: Boolean) = commonBase64(includePadding = includePadding)

  actual fun md5() = digest("MD5")

  actual fun sha1() = digest("SHA-1")

  actual fun sha256() = digest("SHA-256")

  actual fun sha512() = digest("SHA-512")

  internal open fun digest(algorithm: String): ByteString {
    val digestBytes = MessageDigest.getInstance(algorithm).run {
      update(data, 0, size)
      digest()
    }
    return ByteString(digestBytes)
  }

  /** Returns the 160-bit SHA-1 HMAC of this byte string.  */
  actual open fun hmacSha1(key: ByteString) = hmac("HmacSHA1", key)

  /** Returns the 256-bit SHA-256 HMAC of this byte string.  */
  actual open fun hmacSha256(key: ByteString) = hmac("HmacSHA256", key)

  /** Returns the 512-bit SHA-512 HMAC of this byte string.  */
  actual open fun hmacSha512(key: ByteString) = hmac("HmacSHA512", key)

  internal open fun hmac(algorithm: String, key: ByteString): ByteString {
    try {
      val mac = Mac.getInstance(algorithm)
      mac.init(SecretKeySpec(key.toByteArray(), algorithm))
      return ByteString(mac.doFinal(data))
    } catch (e: InvalidKeyException) {
      throw IllegalArgumentException(e)
    }
  }

  @JvmOverloads
  actual open fun base64Url(includePadding: Boolean) = commonBase64Url(includePadding = includePadding)

  actual open fun hex(): String = commonHex()

  actual open fun toAsciiLowercase(): ByteString = commonToAsciiLowercase()

  actual open fun toAsciiUppercase(): ByteString = commonToAsciiUppercase()

  @JvmOverloads
  actual open fun substring(beginIndex: Int, endIndex: Int): ByteString =
    commonSubstring(beginIndex, endIndex)

  internal actual open fun internalGet(pos: Int) = commonGetByte(pos)

  @JvmName("getByte")
  actual operator fun get(index: Int): Byte = internalGet(index)

  actual val size
    @JvmName("size")
    get() = getSize()

  internal actual open fun getSize() = commonGetSize()

  actual open fun toByteArray() = commonToByteArray()

  internal actual open fun internalArray() = commonInternalArray()

  /** Returns a `ByteBuffer` view of the bytes in this `ByteString`. */
  open fun asByteBuffer(): ByteBuffer = ByteBuffer.wrap(data).asReadOnlyBuffer()

  /** Writes the contents of this byte string to `out`.  */
  @Throws(IOException::class)
  open fun write(out: OutputStream) {
    out.write(data)
  }

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

  @JvmOverloads
  actual fun indexOf(other: ByteString, fromIndex: Int) = indexOf(other.internalArray(), fromIndex)

  @JvmOverloads
  actual open fun indexOf(other: ByteArray, fromIndex: Int) = commonIndexOf(other, fromIndex)

  @JvmOverloads
  actual fun lastIndexOf(other: ByteString, fromIndex: Int) = commonLastIndexOf(other, fromIndex)

  @JvmOverloads
  actual open fun lastIndexOf(other: ByteArray, fromIndex: Int) = commonLastIndexOf(other, fromIndex)

  actual override fun equals(other: Any?) = commonEquals(other)

  actual fun equals(other: ByteString, constantTime: Boolean) =
    if (constantTime) commonEqualsConstantTime(other) else this == other

  actual override fun hashCode() = commonHashCode()

  actual override fun compareTo(other: ByteString) = commonCompareTo(other)

  actual override fun toString() = commonToString()

  @Throws(IOException::class)
  private fun readObject(`in`: ObjectInputStream) {
    val dataLength = `in`.readInt()
    val byteString = `in`.readByteString(dataLength)
    val field = ByteString::class.java.getDeclaredField("data")
    field.isAccessible = true
    field.set(this, byteString.data)
  }

  @Throws(IOException::class)
  private fun writeObject(out: ObjectOutputStream) {
    out.writeInt(data.size)
    out.write(data)
  }

  @JvmName("-deprecated_getByte")
  @Deprecated(
    message = "moved to operator function",
    replaceWith = ReplaceWith(expression = "this[index]"),
    level = DeprecationLevel.ERROR,
  )
  fun getByte(index: Int) = this[index]

  @JvmName("-deprecated_size")
  @Deprecated(
    message = "moved to val",
    replaceWith = ReplaceWith(expression = "size"),
    level = DeprecationLevel.ERROR,
  )
  fun size() = size

  actual companion object {
    private const val serialVersionUID = 1L

    @JvmField
    actual val EMPTY: ByteString = ByteString(byteArrayOf())

    @JvmStatic
    actual fun of(vararg data: Byte) = commonOf(data)

    @JvmStatic
    @JvmName("of")
    actual fun ByteArray.toByteString(offset: Int, byteCount: Int): ByteString =
      commonToByteString(offset, byteCount)

    /** Returns a [ByteString] containing a copy of this [ByteBuffer]. */
    @JvmStatic
    @JvmName("of")
    fun ByteBuffer.toByteString(): ByteString {
      val copy = ByteArray(remaining())
      get(copy)
      return ByteString(copy)
    }

    @JvmStatic
    actual fun String.encodeUtf8(): ByteString = commonEncodeUtf8()

    /** Returns a new [ByteString] containing the `charset`-encoded bytes of this [String].  */
    @JvmStatic
    @JvmName("encodeString")
    fun String.encode(charset: Charset = Charsets.UTF_8) = ByteString(toByteArray(charset))

    @JvmStatic
    actual fun String.decodeBase64() = commonDecodeBase64()

    @JvmStatic
    actual fun String.decodeHex() = commonDecodeHex()

    @JvmStatic
    actual fun String.decodeHex(ignoreWhitespace: Boolean) = when {
      ignoreWhitespace -> decodeHexIgnoreWhitespace()
      else -> commonDecodeHex()
    }

    /**
     * Reads `count` bytes from this [InputStream] and returns the result.
     *
     * @throws java.io.EOFException if `in` has fewer than `count` bytes to read.
     */
    @Throws(IOException::class)
    @JvmStatic
    @JvmName("read")
    fun InputStream.readByteString(byteCount: Int): ByteString {
      require(byteCount >= 0) { "byteCount < 0: $byteCount" }

      val result = ByteArray(byteCount)
      var offset = 0
      var read: Int
      while (offset < byteCount) {
        read = read(result, offset, byteCount - offset)
        if (read == -1) throw EOFException()
        offset += read
      }
      return ByteString(result)
    }

    @JvmName("-deprecated_decodeBase64")
    @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
        expression = "string.decodeBase64()",
        imports = ["okio.ByteString.Companion.decodeBase64"],
      ),
      level = DeprecationLevel.ERROR,
    )
    fun decodeBase64(string: String) = string.decodeBase64()

    @JvmName("-deprecated_decodeHex")
    @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
        expression = "string.decodeHex()",
        imports = ["okio.ByteString.Companion.decodeHex"],
      ),
      level = DeprecationLevel.ERROR,
    )
    fun decodeHex(string: String) = string.decodeHex()

    @JvmName("-deprecated_encodeString")
    @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
        expression = "string.encode(charset)",
        imports = ["okio.ByteString.Companion.encode"],
      ),
      level = DeprecationLevel.ERROR,
    )
    fun encodeString(string: String, charset: Charset) = string.encode(charset)

    @JvmName("-deprecated_encodeUtf8")
    @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
        expression = "string.encodeUtf8()",
        imports = ["okio.ByteString.Companion.encodeUtf8"],
      ),
      level = DeprecationLevel.ERROR,
    )
    fun encodeUtf8(string: String) = string.encodeUtf8()

    @JvmName("-deprecated_of")
    @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
        expression = "buffer.toByteString()",
        imports = ["okio.ByteString.Companion.toByteString"],
      ),
      level = DeprecationLevel.ERROR,
    )
    fun of(buffer: ByteBuffer) = buffer.toByteString()

    @JvmName("-deprecated_of")
    @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
        expression = "array.toByteString(offset, byteCount)",
        imports = ["okio.ByteString.Companion.toByteString"],
      ),
      level = DeprecationLevel.ERROR,
    )
    fun of(array: ByteArray, offset: Int, byteCount: Int) = array.toByteString(offset, byteCount)

    @JvmName("-deprecated_read")
    @Deprecated(
      message = "moved to extension function",
      replaceWith = ReplaceWith(
        expression = "inputstream.readByteString(byteCount)",
        imports = ["okio.ByteString.Companion.readByteString"],
      ),
      level = DeprecationLevel.ERROR,
    )
    fun read(inputstream: InputStream, byteCount: Int) = inputstream.readByteString(byteCount)
  }
}
