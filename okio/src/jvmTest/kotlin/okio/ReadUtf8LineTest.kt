/*
 * Copyright (C) 2014 Square, Inc.
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

import de.infix.testBalloon.framework.core.testSuite
import java.io.EOFException
import okio.TestUtil.SEGMENT_SIZE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

val ReadUtf8LineTest by testSuite {
  for (factory in ReadUtf8LineFactory.entries) {
    testSuite(factory.name) {
      testFixture {
        object {
          val data: Buffer = Buffer()
          val source: BufferedSource = factory.create(data)
        }
      } asContextForEach {
        test("readLines") {
          data.writeUtf8("abc\ndef\n")
          assertEquals("abc", source.readUtf8LineStrict())
          assertEquals("def", source.readUtf8LineStrict())
          try {
            source.readUtf8LineStrict()
            fail()
          } catch (expected: EOFException) {
            assertEquals("\\n not found: limit=0 content=…", expected.message)
          }
        }

        test("readUtf8LineStrictWithLimits") {
          val lens = intArrayOf(1, SEGMENT_SIZE - 2, SEGMENT_SIZE - 1, SEGMENT_SIZE, SEGMENT_SIZE * 10)
          for (len in lens) {
            data.writeUtf8("a".repeat(len)).writeUtf8("\n")
            assertEquals(len.toLong(), source.readUtf8LineStrict(len.toLong()).length.toLong())
            source.readUtf8()
            data.writeUtf8("a".repeat(len)).writeUtf8("\n").writeUtf8("a".repeat(len))
            assertEquals(len.toLong(), source.readUtf8LineStrict(len.toLong()).length.toLong())
            source.readUtf8()
            data.writeUtf8("a".repeat(len)).writeUtf8("\r\n")
            assertEquals(len.toLong(), source.readUtf8LineStrict(len.toLong()).length.toLong())
            source.readUtf8()
            data.writeUtf8("a".repeat(len)).writeUtf8("\r\n").writeUtf8("a".repeat(len))
            assertEquals(len.toLong(), source.readUtf8LineStrict(len.toLong()).length.toLong())
            source.readUtf8()
          }
        }

        test("readUtf8LineStrictNoBytesConsumedOnFailure") {
          data.writeUtf8("abc\n")
          try {
            source.readUtf8LineStrict(2)
            fail()
          } catch (expected: EOFException) {
            assertTrue(expected.message!!.startsWith("\\n not found: limit=2 content=61626"))
          }
          assertEquals("abc", source.readUtf8LineStrict(3))
        }

        test("readUtf8LineStrictEmptyString") {
          data.writeUtf8("\r\nabc")
          assertEquals("", source.readUtf8LineStrict(0))
          assertEquals("abc", source.readUtf8())
        }

        test("readUtf8LineStrictNonPositive") {
          data.writeUtf8("\r\n")
          try {
            source.readUtf8LineStrict(-1)
            fail("Expected failure: limit must be greater than 0")
          } catch (expected: IllegalArgumentException) {
          }
        }

        test("eofExceptionProvidesLimitedContent") {
          data.writeUtf8("aaaaaaaabbbbbbbbccccccccdddddddde")
          try {
            source.readUtf8LineStrict()
            fail()
          } catch (expected: EOFException) {
            assertEquals(
              "\\n not found: limit=33 content=616161616161616162626262626262626363636363636363" +
                "6464646464646464…",
              expected.message,
            )
          }
        }

        test("newlineAtEnd") {
          data.writeUtf8("abc\n")
          assertEquals("abc", source.readUtf8LineStrict(3))
          assertTrue(source.exhausted())
          data.writeUtf8("abc\r\n")
          assertEquals("abc", source.readUtf8LineStrict(3))
          assertTrue(source.exhausted())
          data.writeUtf8("abc\r")
          try {
            source.readUtf8LineStrict(3)
            fail()
          } catch (expected: EOFException) {
            assertEquals("\\n not found: limit=3 content=6162630d…", expected.message)
          }
          source.readUtf8()
          data.writeUtf8("abc")
          try {
            source.readUtf8LineStrict(3)
            fail()
          } catch (expected: EOFException) {
            assertEquals("\\n not found: limit=3 content=616263…", expected.message)
          }
        }

        test("emptyLines") {
          data.writeUtf8("\n\n\n")
          assertEquals("", source.readUtf8LineStrict())
          assertEquals("", source.readUtf8LineStrict())
          assertEquals("", source.readUtf8LineStrict())
          assertTrue(source.exhausted())
        }

        test("crDroppedPrecedingLf") {
          data.writeUtf8("abc\r\ndef\r\nghi\rjkl\r\n")
          assertEquals("abc", source.readUtf8LineStrict())
          assertEquals("def", source.readUtf8LineStrict())
          assertEquals("ghi\rjkl", source.readUtf8LineStrict())
        }

        test("bufferedReaderCompatible") {
          data.writeUtf8("abc\ndef")
          assertEquals("abc", source.readUtf8Line())
          assertEquals("def", source.readUtf8Line())
          assertNull(source.readUtf8Line())
        }

        test("bufferedReaderCompatibleWithTrailingNewline") {
          data.writeUtf8("abc\ndef\n")
          assertEquals("abc", source.readUtf8Line())
          assertEquals("def", source.readUtf8Line())
          assertNull(source.readUtf8Line())
        }
      }
    }
  }
}

enum class ReadUtf8LineFactory {
  BasicBuffer {
    override fun create(data: Buffer) = data
  },
  Buffered {
    override fun create(data: Buffer): BufferedSource = RealBufferedSource(data)
  },
  SlowBuffered {
    override fun create(data: Buffer): BufferedSource {
      return RealBufferedSource(
        object : ForwardingSource(data) {
          override fun read(sink: Buffer, byteCount: Long): Long {
            return super.read(sink, 1L.coerceAtMost(byteCount))
          }
        },
      )
    }
  },
  ;

  abstract fun create(data: Buffer): BufferedSource
}
