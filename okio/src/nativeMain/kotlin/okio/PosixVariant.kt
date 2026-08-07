/*
 * Copyright (C) 2020 Square, Inc.
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

internal expect val PLATFORM_TEMPORARY_DIRECTORY: Path

internal expect fun PosixFileSystem.variantList(dir: Path, throwOnFailure: Boolean): List<Path>?

internal expect fun PosixFileSystem.variantDelete(path: Path, mustExist: Boolean)

internal expect fun PosixFileSystem.variantMkdir(dir: Path): Int

internal expect fun PosixFileSystem.variantCanonicalize(path: Path): Path

internal expect fun PosixFileSystem.variantMetadataOrNull(path: Path): FileMetadata?

internal expect fun PosixFileSystem.variantMove(source: Path, target: Path)

internal expect fun PosixFileSystem.variantSource(file: Path): Source

internal expect fun PosixFileSystem.variantSink(file: Path, mustCreate: Boolean): Sink

internal expect fun PosixFileSystem.variantAppendingSink(file: Path, mustExist: Boolean): Sink

internal expect fun PosixFileSystem.variantOpenReadOnly(file: Path): FileHandle

internal expect fun PosixFileSystem.variantOpenReadWrite(
  file: Path,
  mustCreate: Boolean,
  mustExist: Boolean,
): FileHandle

internal expect fun PosixFileSystem.variantCreateSymlink(source: Path, target: Path)
