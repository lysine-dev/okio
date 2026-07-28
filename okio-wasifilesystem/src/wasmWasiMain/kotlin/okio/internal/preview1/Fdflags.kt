// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

/**
 * `fdflags: Record`.
 *
 * File descriptor flags.
 */
internal typealias fdflags = Short

/** Data written to the file is always appended to the file's end. */
internal val fdflags_append: Short = (1 shl 0).toShort()

/** Write according to synchronized I/O data integrity completion. Only the data stored in the file is synchronized. */
internal val fdflags_dsync: Short = (1 shl 1).toShort()

/** Non-blocking mode. */
internal val fdflags_nonblock: Short = (1 shl 2).toShort()

/** Synchronized read I/O operations. */
internal val fdflags_rsync: Short = (1 shl 3).toShort()

/** Write according to synchronized I/O file integrity completion. In addition to synchronizing the data stored in the file, the implementation may also synchronously update the file's metadata. */
internal val fdflags_sync: Short = (1 shl 4).toShort()
