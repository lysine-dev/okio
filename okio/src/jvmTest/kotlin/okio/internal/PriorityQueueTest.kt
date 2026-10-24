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
import java.lang.Integer.numberOfTrailingZeros
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds
import okio.AsyncTimeout
import okio.PriorityQueue
import org.junit.jupiter.api.Test

class PriorityQueueTest {
  private val TIME_UNIT = TimeUnit.NANOSECONDS
  val now = 0L

  @Test
  fun insertedElementIsSmallerThanItsAncestors() {
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21............
      |""".toHeap()

    before.add(AsyncTimeout(2))

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............2................
      |......10...............5........
      |..28......29......21......20....
      |
      """.trimMargin(),
    )
  }

  @Test
  fun insertedElementWithSmallerTimeoutButLargerTimeoutAtIsLargerThanItsAncestors() {
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21............
      |""".toHeap()

    AsyncTimeout(2, false)
      .apply { setTimeoutAt(now + 28.nanoseconds.inWholeNanoseconds) }
      .let { before.add(it) }

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......10..............20........
      |..28......29......21......30....
      |
      """.trimMargin(),
    )
  }

  @Test
  fun insertedElementWithLargerTimeoutButSmallerTimeoutAtIsSmallerThanItsAncestors() {
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21............
      |""".toHeap()

    // now=-30, timeoutNanos=30, timeoutAt=0
    AsyncTimeout(30, false)
      .apply { setTimeoutAt(now - 30.nanoseconds.inWholeNanoseconds) }
      .let { before.add(it) }

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............0................
      |......10...............5........
      |..28......29......21......20....
      |
      """.trimMargin(),
    )
  }

  @Test
  fun insertedElementIsSmallerThanParent() {
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21............
      |""".toHeap()

    before.add(AsyncTimeout(15))

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......10..............15........
      |..28......29......21......20....
      |
      """.trimMargin(),
    )
  }

  @Test
  fun lastElementIsGreaterThanOneOfRemovedElementChildren() {
    val before = """
      |...............5................
      |......10..............20........
      |..11......12......21......22....
      |""".toHeap()

    before.remove(before[10])

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......11..............20........
      |..22......12......21............
      |
      """.trimMargin(),
    )
  }

  @Test
  fun insertedElementIsLargerThanBothItsParent() {
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21............
      |""".toHeap()

    before.add(AsyncTimeout(24))

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......10..............20........
      |..28......29......21......24....
      |
      """.trimMargin(),
    )
  }

  @Test
  fun lastElementIsGreaterThanRemovedElement() {
    val before = """
      |...............5................
      |......10..............20........
      |..28......29......21......22....
      |""".toHeap()

    before.remove(before[10])

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......22..............20........
      |..28......29......21............
      |
      """.trimMargin(),
    )
  }

  @Test
  fun lastElementIsLessThanRemovedElement() {
    val before = """
      |...............5................
      |......20..............10........
      |..21......22......11......12....
      |""".toHeap()

    before.remove(before[20])

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............5................
      |......12..............10........
      |..21......22......11............
      |
      """.trimMargin(),
    )
  }

  @Test
  fun lastElementIsLessThanRemovedElementParent() {
    val before = """
      |...............................5................................
      |..............10..............................20................
      |......11..............12..............21..............22........
      |..13......14....................................................
      |""".toHeap()

    before.remove(before[21])

    assertThat(before.toDebugString()).isEqualTo(
      """
      |...............................5................................
      |..............10..............................14................
      |......11..............12..............20..............22........
      |..13............................................................
      |
      """.trimMargin(),
    )
  }

  private fun AsyncTimeout(value: Long, setTimeoutAt: Boolean = true) = AsyncTimeout().apply {
    timeout(value, TIME_UNIT)
    if (setTimeoutAt) setTimeoutAt(now)
  }

  private fun String.toHeap(): PriorityQueue {
    val nodeValues = trimMargin()
      .replace(".", " ")
      .trim()
      .split(Regex("\\s+", RegexOption.MULTILINE))
      .map { it.toLong() }

    val result = PriorityQueue()

    for (i in nodeValues) {
      AsyncTimeout(i).let { result.add(it) }
    }

    val formattedBack = result.toDebugString()
    assertThat(formattedBack, "input is well-formatted").isEqualTo(this.trimMargin())

    return result
  }

  private operator fun PriorityQueue.get(value: Long): AsyncTimeout {
    return array.single { it?.timeoutNanos() == TIME_UNIT.toNanos(value) }!!
  }

  /**
   * Returns a string like this:
   *
   * ```
   * ...............5................
   * ......12..............10........
   * ..21......22......11............
   * ```
   */
  private fun PriorityQueue.toDebugString() = buildString {
    val nodeWidth = 4
    val height = numberOfTrailingZeros(Integer.highestOneBit(size)) + 1
    val width = (1 shl height) - 1
    var index = 1 // Heap's first element is at 1!
    for (row in 0 until height) {
      val printAt = height - row - 1
      for (column in 1..width) {
        if (
          numberOfTrailingZeros(column) != printAt ||
          index >= array.size ||
          array[index] == null
        ) {
          append(".".repeat(nodeWidth))
        } else {
          val nodeValue = array[index++]!!.timeoutAt.toString()
          append(".".repeat(nodeWidth - nodeValue.length))
          append(nodeValue)
        }
      }
      append(".".repeat(nodeWidth))
      append("\n")
    }
  }
}
