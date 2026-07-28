/*
 * Copyright (C) 2024 Square, Inc.
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
package okio.internal

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.zlib.crc32
import platform.zlib.uBytefVar

@OptIn(UnsafeNumber::class)
internal actual class CRC32 {
  private var crc = crc32(0u, null, 0u)

  actual fun update(content: ByteArray, offset: Int, byteCount: Int) {
    content.usePinned {
      crc = crc32(crc, it.addressOf(offset) as CValuesRef<uBytefVar>, byteCount.toUInt())
    }
  }

  actual fun update(content: ByteArray) {
    update(content, 0, content.size)
  }

  actual fun getValue(): Long {
    return crc.toLong()
  }

  actual fun reset() {
    crc = crc32(0u, null, 0u)
  }
}
