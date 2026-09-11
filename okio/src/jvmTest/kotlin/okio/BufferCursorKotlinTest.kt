/*
 * Copyright (C) 2018 Square, Inc.
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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import okio.Buffer.UnsafeCursor
import okio.TestUtil.deepCopy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

val BufferCursorKotlinTest by testSuite {
  for (bufferFactory in BufferFactory.entries) {
    testSuite(bufferFactory.name) {
      test("acquireReadOnlyDoesNotCopySharedDataArray") {
        val buffer = deepCopy(bufferFactory.newBuffer())
        if (buffer.size <= 0L) return@test

        val shared = buffer.clone()
        assertTrue(buffer.head!!.shared)

        buffer.readUnsafe().use { cursor ->
          cursor.seek(0)
          assertSame(cursor.data, shared.head!!.data)
        }
      }

      test("acquireReadWriteDoesNotCopyUnsharedDataArray") {
        val buffer = deepCopy(bufferFactory.newBuffer())
        if (buffer.size <= 0L) return@test
        assertFalse(buffer.head!!.shared)

        val originalData = buffer.head!!.data

        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.seek(0)
          assertSame(cursor.data, originalData)
        }
      }

      test("acquireReadWriteCopiesSharedDataArray") {
        val buffer = deepCopy(bufferFactory.newBuffer())
        if (buffer.size <= 0L) return@test

        val shared = buffer.clone()
        assertTrue(buffer.head!!.shared)

        buffer.readAndWriteUnsafe().use { cursor ->
          cursor.seek(0)
          assertNotSame(cursor.data, shared.head!!.data)
        }
      }

      test("writeSharedSegments") {
        val buffer = bufferFactory.newBuffer()

        // Make a deep copy. This buffer's segments are not shared.
        val deepCopy = deepCopy(buffer)
        assertTrue(deepCopy.head == null || !deepCopy.head!!.shared)

        // Make a shallow copy. Both buffers' segments are shared as a side effect.
        val shallowCopy = buffer.clone()
        assertTrue(shallowCopy.head == null || shallowCopy.head!!.shared)
        assertTrue(buffer.head == null || buffer.head!!.shared)

        val expected = Buffer()
        expected.writeUtf8("x".repeat(buffer.size.toInt()))

        buffer.readAndWriteUnsafe().use { cursor ->
          while (cursor.next() != -1) {
            cursor.data!!.fill('x'.code.toByte(), cursor.start, cursor.end)
          }
        }

        // The buffer was fully changed.
        assertEquals(expected, buffer)

        // The buffer we're shared with is unchanged.
        assertEquals(deepCopy, shallowCopy)
      }

      /** As an optimization it's okay to use the same cursor on multiple buffers.  */
      test("cursorReuse") {
        val cursor = UnsafeCursor()

        val buffer1 = bufferFactory.newBuffer()
        buffer1.readUnsafe(cursor)
        assertSame(buffer1, cursor.buffer)
        assertFalse(cursor.readWrite)
        cursor.close()
        assertSame(null, cursor.buffer)

        val buffer2 = bufferFactory.newBuffer()
        buffer2.readAndWriteUnsafe(cursor)
        assertSame(buffer2, cursor.buffer)
        assertTrue(cursor.readWrite)
        cursor.close()
        assertSame(null, cursor.buffer)
      }
    }
  }
}
