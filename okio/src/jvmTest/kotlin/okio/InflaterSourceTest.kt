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

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import de.infix.testBalloon.framework.core.testSuite
import java.io.EOFException
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.TestUtil.SEGMENT_SIZE
import okio.TestUtil.randomBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail

val InflaterSourceTest by testSuite {
  for (bufferFactory in BufferedSourceFactory.entries) {
    testSuite(bufferFactory.name) {
      testFixture { InflaterFixture(bufferFactory) } asContextForEach {
        test("inflate") {
          decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
          val inflated = inflate(deflatedSource)
          assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8())
        }

        test("inflateTruncated") {
          decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CDw==")
          try {
            inflate(deflatedSource)
            fail()
          } catch (expected: EOFException) {
          }
        }

        test("inflateWellCompressed") {
          decodeBase64(
            "eJztwTEBAAAAwqCs61/CEL5AAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
              "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB8BtFeWvE=",
          )
          val original = "a".repeat(1024 * 1024)
          deflate(original.encodeUtf8())
          val inflated = inflate(deflatedSource)
          assertEquals(original, inflated.readUtf8())
        }

        test("inflatePoorlyCompressed") {
          if (bufferFactory.isOneByteAtATime) return@test // 8 GiB for 1 byte per segment!
          val original = randomBytes(1024 * 1024)
          deflate(original)
          val inflated = inflate(deflatedSource)
          assertEquals(original, inflated.readByteString())
        }

        test("inflateIntoNonemptySink") {
          for (i in 0 until SEGMENT_SIZE) {
            resetDeflatedSourceAndSink()
            val inflated = Buffer().writeUtf8("a".repeat(i))
            deflate("God help us, we're in the hands of engineers.".encodeUtf8())
            val source = InflaterSource(deflatedSource, Inflater())
            while (source.read(inflated, Int.MAX_VALUE.toLong()) != -1L) {
            }
            inflated.skip(i.toLong())
            assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8())
          }
        }

        test("inflateSingleByte") {
          val inflated = Buffer()
          decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
          val source = InflaterSource(deflatedSource, Inflater())
          source.read(inflated, 1)
          source.close()
          assertEquals("G", inflated.readUtf8())
          assertEquals(0, inflated.size)
        }

        test("inflateByteCount") {
          if (bufferFactory.isOneByteAtATime) return@test // This test assumes one step.
          val inflated = Buffer()
          decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
          val source = InflaterSource(deflatedSource, Inflater())
          source.read(inflated, 11)
          source.close()
          assertEquals("God help us", inflated.readUtf8())
          assertEquals(0, inflated.size)
        }

        test("sourceExhaustedPrematurelyOnRead") {
          // Deflate 0 bytes of data that lacks the in-stream terminator.
          decodeBase64("eJwAAAD//w==")
          val inflated = Buffer()
          val inflater = Inflater()
          val source = InflaterSource(deflatedSource, inflater)
          assertThat(deflatedSource.exhausted()).isFalse()
          try {
            source.read(inflated, Long.MAX_VALUE)
            fail()
          } catch (expected: EOFException) {
            assertThat(expected).hasMessage("source exhausted prematurely")
          }

          // Despite the exception, the read() call made forward progress on the underlying stream!
          assertThat(deflatedSource.exhausted()).isTrue()
        }

        /**
         * Confirm that [InflaterSource.readOrInflate] consumes a byte on each call even if it
         * doesn't produce a byte on every call.
         */
        test("readOrInflateMakesByteByByteProgress") {
          // Deflate 0 bytes of data that lacks the in-stream terminator.
          decodeBase64("eJwAAAD//w==")
          val deflatedByteCount = 7
          val inflated = Buffer()
          val inflater = Inflater()
          val source = InflaterSource(deflatedSource, inflater)
          assertThat(deflatedSource.exhausted()).isFalse()
          if (bufferFactory.isOneByteAtATime) {
            for (i in 0 until deflatedByteCount) {
              assertThat(inflater.bytesRead).isEqualTo(i.toLong())
              assertThat(source.readOrInflate(inflated, Long.MAX_VALUE)).isEqualTo(0L)
            }
          } else {
            assertThat(source.readOrInflate(inflated, Long.MAX_VALUE)).isEqualTo(0L)
          }
          assertThat(inflater.bytesRead).isEqualTo(deflatedByteCount.toLong())
          assertThat(deflatedSource.exhausted()).isTrue()
        }
      }
    }
  }
}

private class InflaterFixture(val factory: BufferedSourceFactory) {
  lateinit var deflatedSink: BufferedSink
  lateinit var deflatedSource: BufferedSource

  init {
    resetDeflatedSourceAndSink()
  }

  fun resetDeflatedSourceAndSink() {
    val pipe = factory.pipe()
    deflatedSink = pipe.sink
    deflatedSource = pipe.source
  }

  fun decodeBase64(s: String) {
    deflatedSink.write(s.decodeBase64()!!)
    deflatedSink.flush()
  }

  /** Use DeflaterOutputStream to deflate source.  */
  fun deflate(source: ByteString) {
    val sink = DeflaterOutputStream(deflatedSink.outputStream()).sink()
    sink.write(Buffer().write(source), source.size.toLong())
    sink.close()
  }

  /** Returns a new buffer containing the inflated contents of `deflated`.  */
  fun inflate(deflated: BufferedSource?): Buffer {
    val result = Buffer()
    val source = InflaterSource(deflated!!, Inflater())
    while (source.read(result, Int.MAX_VALUE.toLong()) != -1L) {
    }
    return result
  }
}
