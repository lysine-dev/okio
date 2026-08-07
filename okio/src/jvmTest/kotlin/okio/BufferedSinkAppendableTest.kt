/*
 * Copyright (c) 2026 Okio Authors
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

import app.cash.burst.Burst
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

@Burst
class BufferedSinkAppendableTest(
  factory: BufferedSinkFactory = BufferedSinkFactory.BasicBuffer,
) {
  private val data = Buffer()
  private val sink = factory.create(data)
  private val appendable = sink.utf8Appendable()

  @Test
  fun sizeBoundsCheck() {
    assertFailsWith<IllegalArgumentException> {
      appendable.append("abc", -1, 2)
    }
    assertFailsWith<IllegalArgumentException> {
      appendable.append("abc", 2, 1)
    }
    assertFailsWith<IllegalArgumentException> {
      appendable.append("abc", 1, 4)
    }
  }

  @Test
  fun appendNulls() {
    appendable.append("abc")
    appendable.append(null)
    appendable.append("def")
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("abcnulldef")
  }

  @Test
  fun appendNullsWithRanges() {
    appendable.append("abcde", 1, 3)
    appendable.append(null, 1, 3)
    appendable.append("fghij", 1, 3)
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("bculgh")
  }

  @Test
  fun charCallsWithMatchedSurrogates() {
    appendable.append("donut ")
    appendable.append('\ud83c')
    appendable.append('\udf69')
    appendable.append(" sprinkles")
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("donut \ud83c\udf69 sprinkles")
  }

  @Test
  fun charCallsWithBrokenLowHighSurrogates() {
    appendable.append("donut ")
    appendable.append('\udf69')
    appendable.append('\ud83c')
    appendable.append(" sprinkles")
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("donut ?? sprinkles")
  }

  @Test
  fun stringCallsWithMatchedSurrogates() {
    appendable.append("donut \ud83c")
    appendable.append("\udf69 sprinkles")
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("donut \ud83c\udf69 sprinkles")
  }

  @Test
  fun stringCallsWithEmpty() {
    appendable.append("donut \ud83c")
    appendable.append("")
    appendable.append("\udf69 sprinkles")
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("donut \ud83c\udf69 sprinkles")
  }

  @Test
  fun stringCallsWithLowAndHigh() {
    appendable.append("two \ud83c")
    appendable.append("\udf69\ud83c")
    appendable.append("\udf69 donuts")
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("two \ud83c\udf69\ud83c\udf69 donuts")
  }

  @Test
  fun stringCallsWithBrokenLowHighSurrogates() {
    appendable.append("donut \udf69")
    appendable.append("\ud83c sprinkles")
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("donut ?? sprinkles")
  }

  @Test
  fun savedHighSurrogateIsDropped() {
    appendable.append("donut \ud83c")
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("donut ")
  }

  @Test
  fun stringCallsWithBrokenHighSurrogateAndNull() {
    appendable.append("donut \ud83c")
    appendable.append(null)
    sink.emit()
    assertThat(data.readUtf8()).isEqualTo("donut ?null")
  }
}
