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

package okio

import kotlin.test.Test
import kotlin.test.assertEquals

class UnsignedTest {
  @Test
  fun writeUnsignedByte() {
    val buffer = Buffer()
    buffer.writeUByte(254u.toUByte())
    buffer.writeUByte(15u.toUByte())

    assertEquals(2, buffer.size)
    assertEquals(0xfe.toByte(), buffer.readByte())
    assertEquals(0x0f.toByte(), buffer.readByte())
    assertEquals(0, buffer.size)
  }

  @Test fun readUnsignedByte() {
    val buffer = Buffer()
      .writeByte(0xfe)
      .writeByte(0x0f)
    assertEquals(2, buffer.size)

    assertEquals(254u.toUByte(), buffer.readUByte())
    assertEquals(1, buffer.size)

    assertEquals(15u.toUByte(), buffer.readUByte())
    assertEquals(0, buffer.size)
  }

  @Test fun writeUnsignedShort() {
    val buffer = Buffer()
    buffer.writeUShort(65534u.toUShort())
    buffer.writeUShort(15u.toUShort())

    assertEquals(4, buffer.size)
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xfe.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x0f.toByte(), buffer.readByte())
    assertEquals(0, buffer.size)
  }

  @Test fun readUnsignedShort() {
    val buffer = Buffer()
      .writeByte(0xff)
      .writeByte(0xfe)
      .writeByte(0x00)
      .writeByte(0x0f)
    assertEquals(4, buffer.size)

    assertEquals(65534u.toUShort(), buffer.readUShort())
    assertEquals(2, buffer.size)

    assertEquals(15u.toUShort(), buffer.readUShort())
    assertEquals(0, buffer.size)
  }

  @Test fun writeUnsignedShortLittleEndian() {
    val buffer = Buffer()
    buffer.writeUShortLe(65534u.toUShort())
    buffer.writeUShortLe(15u.toUShort())

    assertEquals(4, buffer.size)
    assertEquals(0xfe.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0x0f.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0, buffer.size)
  }

  @Test fun readUnsignedShortLittleEndian() {
    val buffer = Buffer()
      .writeByte(0xfe)
      .writeByte(0xff)
      .writeByte(0x0f)
      .writeByte(0x00)
    assertEquals(4, buffer.size)

    assertEquals(65534u.toUShort(), buffer.readUShortLe())
    assertEquals(2, buffer.size)

    assertEquals(15u.toUShort(), buffer.readUShortLe())
    assertEquals(0, buffer.size)
  }

  @Test fun writeUnsignedInt() {
    val buffer = Buffer()
    buffer.writeUInt(4294967294u)
    buffer.writeUInt(15u)

    assertEquals(8, buffer.size)
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xfe.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x0f.toByte(), buffer.readByte())
    assertEquals(0, buffer.size)
  }

  @Test fun readUnsignedInt() {
    val buffer = Buffer()
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xfe)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x0f)
    assertEquals(8, buffer.size)

    assertEquals(4294967294u, buffer.readUInt())
    assertEquals(4, buffer.size)

    assertEquals(15u, buffer.readUInt())
    assertEquals(0, buffer.size)
  }

  @Test fun writeUnsignedIntLittleEndian() {
    val buffer = Buffer()
    buffer.writeUIntLe(4294967294u)
    buffer.writeUIntLe(15u)

    assertEquals(8, buffer.size)
    assertEquals(0xfe.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0x0f.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0, buffer.size)
  }

  @Test fun readUnsignedIntLittleEndian() {
    val buffer = Buffer()
      .writeByte(0xfe)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0x0f)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
    assertEquals(8, buffer.size)

    assertEquals(4294967294u, buffer.readUIntLe())
    assertEquals(4, buffer.size)

    assertEquals(15u, buffer.readUIntLe())
    assertEquals(0, buffer.size)
  }

  @Test fun writeUnsignedLong() {
    val buffer = Buffer()
    buffer.writeULong(18446744073709551614uL)
    buffer.writeULong(15u)

    assertEquals(16, buffer.size)
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xfe.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x0f.toByte(), buffer.readByte())
    assertEquals(0, buffer.size)
  }

  @Test fun readUnsignedLong() {
    val buffer = Buffer()
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xfe)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x0f)
    assertEquals(16, buffer.size)

    assertEquals(18446744073709551614uL, buffer.readULong())
    assertEquals(8, buffer.size)

    assertEquals(15u, buffer.readULong())
    assertEquals(0, buffer.size)
  }

  @Test fun writeUnsignedLongLittleEndian() {
    val buffer = Buffer()
    buffer.writeULongLe(18446744073709551614uL)
    buffer.writeULongLe(15uL)

    assertEquals(16, buffer.size)
    assertEquals(0xfe.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0xff.toByte(), buffer.readByte())
    assertEquals(0x0f.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0x00.toByte(), buffer.readByte())
    assertEquals(0, buffer.size)
  }

  @Test fun readUnsignedLongLittleEndian() {
    val buffer = Buffer()
      .writeByte(0xfe)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0xff)
      .writeByte(0x0f)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
      .writeByte(0x00)
    assertEquals(16, buffer.size)

    assertEquals(18446744073709551614uL, buffer.readULongLe())
    assertEquals(8, buffer.size)

    assertEquals(15u, buffer.readULongLe())
    assertEquals(0, buffer.size)
  }
}
