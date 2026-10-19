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

import assertk.assertThat
import assertk.assertions.isEqualTo
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertFailsWith

val BufferedSinkAppendableTest by testSuite {
  for (factory in BufferedSinkFactory.entries) {
    testSuite(factory.name) {
      testFixture {
        object {
          val data = Buffer()
          val sink = factory.create(data)
          val appendable = sink.utf8Appendable()
        }
      } asContextForEach {
        test("sizeBoundsCheck") {
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

        test("appendNulls") {
          appendable.append("abc")
          appendable.append(null)
          appendable.append("def")
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("abcnulldef")
        }

        test("appendNullsWithRanges") {
          appendable.append("abcde", 1, 3)
          appendable.append(null, 1, 3)
          appendable.append("fghij", 1, 3)
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("bculgh")
        }

        test("charCallsWithMatchedSurrogates") {
          appendable.append("donut ")
          appendable.append('\ud83c')
          appendable.append('\udf69')
          appendable.append(" sprinkles")
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("donut \ud83c\udf69 sprinkles")
        }

        test("charCallsWithBrokenLowHighSurrogates") {
          appendable.append("donut ")
          appendable.append('\udf69')
          appendable.append('\ud83c')
          appendable.append(" sprinkles")
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("donut ?? sprinkles")
        }

        test("stringCallsWithMatchedSurrogates") {
          appendable.append("donut \ud83c")
          appendable.append("\udf69 sprinkles")
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("donut \ud83c\udf69 sprinkles")
        }

        test("stringCallsWithEmpty") {
          appendable.append("donut \ud83c")
          appendable.append("")
          appendable.append("\udf69 sprinkles")
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("donut \ud83c\udf69 sprinkles")
        }

        test("stringCallsWithLowAndHigh") {
          appendable.append("two \ud83c")
          appendable.append("\udf69\ud83c")
          appendable.append("\udf69 donuts")
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("two \ud83c\udf69\ud83c\udf69 donuts")
        }

        test("stringCallsWithBrokenLowHighSurrogates") {
          appendable.append("donut \udf69")
          appendable.append("\ud83c sprinkles")
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("donut ?? sprinkles")
        }

        test("savedHighSurrogateIsDropped") {
          appendable.append("donut \ud83c")
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("donut ")
        }

        test("stringCallsWithBrokenHighSurrogateAndNull") {
          appendable.append("donut \ud83c")
          appendable.append(null)
          sink.emit()
          assertThat(data.readUtf8()).isEqualTo("donut ?null")
        }
      }
    }
  }
}
