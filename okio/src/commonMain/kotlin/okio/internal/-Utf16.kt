// ktlint-disable filename
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
package okio.internal

/*
 * UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
 * UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
 * Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
 */

internal val Int.isSurrogate: Boolean
  inline get() = this in 0xd800..0xdfff

internal val Int.isHighSurrogate: Boolean
  inline get() = this in 0xd800..0xdbff

internal val Int.isLowSurrogate: Boolean
  inline get() = this in 0xdc00..0xdfff

internal fun combineSurrogates(high: Int, low: Int): Int =
  0x010000 + (high and 0x03ff shl 10 or (low and 0x03ff))
