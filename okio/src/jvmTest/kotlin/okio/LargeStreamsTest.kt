/*
 * Copyright (C) 2016 Square, Inc.
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

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import okio.ByteString.Companion.decodeHex
import okio.HashingSink.Companion.sha256
import okio.TestUtil.SEGMENT_SIZE
import okio.TestUtil.randomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Slow running tests that run a large amount of data through a stream.  */
class LargeStreamsTest {
  @Test
  fun test() {
    val pipe = Pipe((1024 * 1024).toLong())
    val future = readAllAndCloseAsync(randomSource(FOUR_GIB_PLUS_ONE), pipe.sink)
    val hashingSink = sha256(blackholeSink())
    readAllAndClose(pipe.source, hashingSink)
    assertEquals(FOUR_GIB_PLUS_ONE, future.get() as Long)
    assertEquals(SHA256_RANDOM_FOUR_GIB_PLUS_1, hashingSink.hash)
  }

  /** Note that this test hangs on Android.  */
  @Test
  fun gzipSource() {
    val pipe = Pipe(1024L * 1024)
    val gzipOut = object : GZIPOutputStream(pipe.sink.buffer().outputStream()) {
      init {
        // Disable compression to speed up a slow test. Improved from 141s to 33s on one machine.
        def.setLevel(Deflater.NO_COMPRESSION)
      }
    }
    val future = readAllAndCloseAsync(
      randomSource(FOUR_GIB_PLUS_ONE),
      gzipOut.sink(),
    )
    val hashingSink = sha256(blackholeSink())
    val gzipSource = GzipSource(pipe.source)
    readAllAndClose(gzipSource, hashingSink)
    assertEquals(FOUR_GIB_PLUS_ONE, future.get() as Long)
    assertEquals(SHA256_RANDOM_FOUR_GIB_PLUS_1, hashingSink.hash)
  }

  /** Note that this test hangs on Android.  */
  @Test
  fun gzipSink() {
    val pipe = Pipe(1024L * 1024)
    val gzipSink = GzipSink(pipe.sink)

    // Disable compression to speed up a slow test. Improved from 141s to 35s on one machine.
    gzipSink.deflater.setLevel(Deflater.NO_COMPRESSION)
    val future = readAllAndCloseAsync(randomSource(FOUR_GIB_PLUS_ONE), gzipSink)
    val hashingSink = sha256(blackholeSink())
    val gzipIn = GZIPInputStream(pipe.source.buffer().inputStream())
    readAllAndClose(gzipIn.source(), hashingSink)
    assertEquals(FOUR_GIB_PLUS_ONE, future.get() as Long)
    assertEquals(SHA256_RANDOM_FOUR_GIB_PLUS_1, hashingSink.hash)
  }

  /** Reads all bytes from `source` and writes them to `sink`.  */
  private fun readAllAndClose(source: Source, sink: Sink): Long {
    var result = 0L
    val buffer = Buffer()
    while (true) {
      val count = source.read(buffer, SEGMENT_SIZE.toLong())
      if (count == -1L) break
      sink.write(buffer, count)
      result += count
    }
    source.close()
    sink.close()
    return result
  }

  /** Calls [readAllAndClose] on a background thread.  */
  private fun readAllAndCloseAsync(source: Source, sink: Sink): Future<Long> {
    val executor = Executors.newScheduledThreadPool(0)
    return try {
      executor.submit<Long> { readAllAndClose(source, sink) }
    } finally {
      executor.shutdown()
    }
  }

  companion object {
    /** 4 GiB plus 1 byte. This is greater than what can be expressed in an unsigned int.  */
    const val FOUR_GIB_PLUS_ONE = 0x100000001L

    /** SHA-256 of `TestUtil.randomSource(FOUR_GIB_PLUS_ONE)`.  */
    val SHA256_RANDOM_FOUR_GIB_PLUS_1 =
      "9654947a655c5efc445502fd1bf11117d894b7812b7974fde8ca4a02c5066315".decodeHex()
  }
}
