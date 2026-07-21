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

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okio.ByteString.Companion.decodeHex

class ByteStringHexTest {
  @Test fun encodeHex() {
    assertThat(ByteString.of(0x0, 0x1, 0x2).hex()).isEqualTo("000102")
  }

  @Test fun decodeHex() {
    assertThat("CAFEBABE".decodeHex())
      .isEqualTo(ByteString.of(-54, -2, -70, -66))
    assertThat("CAFEBABE".decodeHex(ignoreWhitespace = true))
      .isEqualTo(ByteString.of(-54, -2, -70, -66))
  }

  @Test fun decodeHexIgnoreWhitespace() {
    assertFailsWith<IllegalArgumentException> {
      " C\tA\rFE\n B   AB\t\tE\r\n".decodeHex(ignoreWhitespace = false)
    }
    assertThat(" C\tA\rFE\n B   AB\t\tE\r\n".decodeHex(ignoreWhitespace = true))
      .isEqualTo(ByteString.of(-54, -2, -70, -66))

    assertFailsWith<IllegalArgumentException> {
      "C            AFEBAB            E".decodeHex(ignoreWhitespace = false)
    }
    assertThat("C            AFEBAB            E".decodeHex(ignoreWhitespace = true))
      .isEqualTo(ByteString.of(-54, -2, -70, -66))
  }

  @Test fun decodeHexOddNumberOfChars() {
    assertFailsWith<IllegalArgumentException> {
      "aaa".decodeHex()
    }
    assertFailsWith<IllegalArgumentException> {
      "aa a".decodeHex(ignoreWhitespace = true)
    }
    assertFailsWith<IllegalArgumentException> {
      "aaa".decodeHex(ignoreWhitespace = true)
    }
  }

  @Test fun decodeHexInvalidChar() {
    assertFailsWith<IllegalArgumentException> {
      "a\u0000".decodeHex()
    }
    assertFailsWith<IllegalArgumentException> {
      "a\u0000".decodeHex(ignoreWhitespace = true)
    }
  }
}
