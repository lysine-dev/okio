/*
 * Copyright (C) 2019 Square, Inc.
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

@file:JvmName("-Unsigned")
@file:Suppress("NOTHING_TO_INLINE") // Syntactic sugar.

package okio

import kotlin.jvm.JvmName

/** Writes an unsigned byte to this sink. */
@Throws(IOException::class)
inline fun <S : BufferedSink> S.writeUByte(b: UByte): S {
  writeByte(b.toByte().toInt())
  return this
}

/**
 * Writes a big-endian, unsigned short to this sink using two bytes.
 * ```
 * val buffer = Buffer()
 * buffer.writeUShort(65534u.toUShort())
 * buffer.writeUShort(15u.toUShort())
 *
 * assertEquals(4, buffer.size)
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xfe.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x0f.toByte(), buffer.readByte())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun <S : BufferedSink> S.writeUShort(b: UShort): S {
  writeShort(b.toShort().toInt())
  return this
}

/**
 * Writes a little-endian, unsigned short to this sink using two bytes.
 * ```
 * val buffer = Buffer()
 * buffer.writeUShortLe(65534u.toUShort())
 * buffer.writeUShortLe(15u.toUShort())
 *
 * assertEquals(4, buffer.size)
 * assertEquals(0xfe.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0x0f.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun <S : BufferedSink> S.writeUShortLe(b: UShort): S {
  writeShortLe(b.toShort().toInt())
  return this
}

/**
 * Writes a big-endian, unsigned int to this sink using four bytes.
 * ```
 * val buffer = Buffer()
 * buffer.writeUInt(4294967294u)
 * buffer.writeUInt(15u)
 *
 * assertEquals(8, buffer.size)
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xfe.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x0f.toByte(), buffer.readByte())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun <S : BufferedSink> S.writeUInt(b: UInt): S {
  writeInt(b.toInt())
  return this
}

/**
 * Writes a little-endian, unsigned int to this sink using four bytes.
 * ```
 * val buffer = Buffer()
 * buffer.writeUIntLe(4294967294u)
 * buffer.writeUIntLe(15u)
 *
 * assertEquals(8, buffer.size)
 * assertEquals(0xfe.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0x0f.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun <S : BufferedSink> S.writeUIntLe(b: UInt): S {
  writeIntLe(b.toInt())
  return this
}

/**
 * Writes a big-endian, unsigned long to this sink using eight bytes.
 * ```
 * val buffer = Buffer()
 * buffer.writeULong(18446744073709551614uL)
 * buffer.writeULong(15u)
 *
 * assertEquals(16, buffer.size)
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xfe.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x0f.toByte(), buffer.readByte())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun <S : BufferedSink> S.writeULong(b: ULong): S {
  writeLong(b.toLong())
  return this
}

/**
 * Writes a little-endian, unsigned long to this sink using eight bytes.
 * ```
 * val buffer = Buffer()
 * buffer.writeULongLe(18446744073709551614uL)
 * buffer.writeULongLe(15uL)
 *
 * assertEquals(16, buffer.size)
 * assertEquals(0xfe.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0xff.toByte(), buffer.readByte())
 * assertEquals(0x0f.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0x00.toByte(), buffer.readByte())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun <S : BufferedSink> S.writeULongLe(b: ULong): S {
  writeLongLe(b.toLong())
  return this
}

/** Removes an unsigned byte from this source and returns it. */
@Throws(IOException::class)
inline fun BufferedSource.readUByte(): UByte {
  return readByte().toUByte()
}

/**
 * Removes two bytes from this source and returns a big-endian, unsigned short.
 * ```
 * val buffer = Buffer()
 *   .writeByte(0xff)
 *   .writeByte(0xfe)
 *   .writeByte(0x00)
 *   .writeByte(0x0f)
 * assertEquals(4, buffer.size)
 *
 * assertEquals(65534u.toUShort(), buffer.readUShort())
 * assertEquals(2, buffer.size)
 *
 * assertEquals(15u.toUShort(), buffer.readUShort())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun BufferedSource.readUShort(): UShort {
  return readShort().toUShort()
}

/**
 * Removes two bytes from this source and returns a little-endian, unsigned short.
 * ```
 * val buffer = Buffer()
 *   .writeByte(0xfe)
 *   .writeByte(0xff)
 *   .writeByte(0x0f)
 *   .writeByte(0x00)
 * assertEquals(4, buffer.size)
 *
 * assertEquals(65534u.toUShort(), buffer.readUShortLe())
 * assertEquals(2, buffer.size)
 *
 * assertEquals(15u.toUShort(), buffer.readUShortLe())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun BufferedSource.readUShortLe(): UShort {
  return readShortLe().toUShort()
}

/**
 * Removes four bytes from this source and returns a big-endian, unsigned int.
 * ```
 * val buffer = Buffer()
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xfe)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x0f)
 * assertEquals(8, buffer.size)
 *
 * assertEquals(4294967294u, buffer.readUInt())
 * assertEquals(4, buffer.size)
 *
 * assertEquals(15u, buffer.readUInt())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun BufferedSource.readUInt(): UInt {
  return readInt().toUInt()
}

/**
 * Removes four bytes from this source and returns a little-endian, unsigned int.
 * ```
 * val buffer = Buffer()
 *   .writeByte(0xfe)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0x0f)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 * assertEquals(8, buffer.size)
 *
 * assertEquals(4294967294u, buffer.readUIntLe())
 * assertEquals(4, buffer.size)
 *
 * assertEquals(15u, buffer.readUIntLe())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun BufferedSource.readUIntLe(): UInt {
  return readIntLe().toUInt()
}

/**
 * Removes eight bytes from this source and returns a big-endian, unsigned long.
 * ```
 * val buffer = Buffer()
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xfe)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x0f)
 * assertEquals(16, buffer.size)
 *
 * assertEquals(18446744073709551614uL, buffer.readULong())
 * assertEquals(8, buffer.size)
 *
 * assertEquals(15u, buffer.readULong())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun BufferedSource.readULong(): ULong {
  return readLong().toULong()
}

/**
 * Removes eight bytes from this source and returns a little-endian, unsigned long.
 * ```
 * val buffer = Buffer()
 *   .writeByte(0xfe)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0xff)
 *   .writeByte(0x0f)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 *   .writeByte(0x00)
 * assertEquals(16, buffer.size)
 *
 * assertEquals(18446744073709551614uL, buffer.readULongLe())
 * assertEquals(8, buffer.size)
 *
 * assertEquals(15u, buffer.readULongLe())
 * assertEquals(0, buffer.size)
 * ```
 */
@Throws(IOException::class)
inline fun BufferedSource.readULongLe(): ULong {
  return readLongLe().toULong()
}
