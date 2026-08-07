/*
 * Copyright (C) 2026 Square, Inc.
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

import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Statistical timing test for [ByteString.equals] with [constantTime]=true.
 *
 * Given two very large byte strings:
 * - When not equal, constant-time equals takes the same time as when equal (no short-circuit).
 * - When not equal, normal equals takes statistically significantly less time than when equal
 *   (short-circuits at the first differing byte).
 */
class ConstantTimeEqualsTimingTest {

  @Test
  fun constantTimeEqualsDoesNotShortCircuit() {
    val n = 1_000_000
    val aBytes = ByteArray(n) { 0 }
    val bBytes = ByteArray(n) { 0 } // identical to a
    val cBytes = ByteArray(n) { 0 }.also { it[0] = 1 } // differs at byte 0

    val a = aBytes.toByteString()
    val b = bBytes.toByteString()
    val c = cBytes.toByteString()

    // Warm up the JIT.
    repeat(200) {
      a.equals(b, constantTime = true)
      a.equals(c, constantTime = true)
      a == b
      a == c
    }

    val iterations = 500

    val ctMatch = median(iterations) { a.equals(b, constantTime = true) }
    val ctMismatch = median(iterations) { a.equals(c, constantTime = true) }
    val normalMismatch = median(iterations) { a == c }

    // CT(match) and CT(mismatch) should be within 3x of each other: neither short-circuits.
    val ctRatio =
      if (ctMatch > ctMismatch) ctMatch.toDouble() / ctMismatch else ctMismatch.toDouble() / ctMatch
    assertTrue(
      "CT(match)=$ctMatch ns and CT(mismatch)=$ctMismatch ns differ by ${ctRatio}x (expected <3x)",
      ctRatio < 3.0,
    )

    // normal(mismatch) must be significantly faster than CT(mismatch): normal short-circuits at byte 0.
    assertTrue(
      "normal(mismatch)=$normalMismatch ns should be <2% of CT(mismatch)=$ctMismatch ns (short-circuit at byte 0)",
      normalMismatch * 50L < ctMismatch,
    )
  }

  private inline fun median(n: Int, block: () -> Unit): Long {
    val times = LongArray(n)
    repeat(n) { i ->
      val t0 = System.nanoTime()
      block()
      times[i] = System.nanoTime() - t0
    }
    times.sort()
    return times[n / 2]
  }
}
