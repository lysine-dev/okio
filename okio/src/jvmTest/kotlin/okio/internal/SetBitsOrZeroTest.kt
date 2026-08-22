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
package okio.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Test

class SetBitsOrZeroTest {
  @Test
  fun happyPath() {
    val value = AtomicInteger(0)
    assertThat(value.setBitsOrZero(1)).isEqualTo(1)
    assertThat(value.setBitsOrZero(1)).isEqualTo(0)
    assertThat(value.setBitsOrZero(2)).isEqualTo(1 or 2)
    assertThat(value.setBitsOrZero(2)).isEqualTo(0)
    assertThat(value.setBitsOrZero(8)).isEqualTo(1 or 2 or 8)
    assertThat(value.setBitsOrZero(8)).isEqualTo(0)
  }

  @Test
  fun multipleBits() {
    val value = AtomicInteger(0)
    assertThat(value.setBitsOrZero(1 or 4)).isEqualTo(1 or 4)
    assertThat(value.setBitsOrZero(1)).isEqualTo(0)
    assertThat(value.setBitsOrZero(4)).isEqualTo(0)
    assertThat(value.setBitsOrZero(1 or 4)).isEqualTo(0)
    assertThat(value.setBitsOrZero(1 or 8)).isEqualTo(0)
    assertThat(value.setBitsOrZero(4 or 8)).isEqualTo(0)

    assertThat(value.setBitsOrZero(8 or 32)).isEqualTo(1 or 4 or 8 or 32)
    assertThat(value.setBitsOrZero(1)).isEqualTo(0)
    assertThat(value.setBitsOrZero(4)).isEqualTo(0)
    assertThat(value.setBitsOrZero(8)).isEqualTo(0)
    assertThat(value.setBitsOrZero(32)).isEqualTo(0)
    assertThat(value.setBitsOrZero(8 or 32)).isEqualTo(0)
    assertThat(value.setBitsOrZero(1 or 32)).isEqualTo(0)
  }
}
