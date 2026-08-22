/*
 * Copyright (C) 2023 Square, Inc.
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

internal actual val PLATFORM_DIRECTORY_SEPARATOR: String
  get() = "/"

// FIXME Should wasmJs be shared with js?
//  Maybe create a new webMain source set?
//  And what about wasmWasi? I don't think that wasi provides any APIS to get these..
internal actual fun getCurrentThreadId() = 0L
internal actual fun getAvailableProcessors() = 1
