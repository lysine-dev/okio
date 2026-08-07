// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

/**
 * `lookupflags: Record`.
 *
 * Flags determining the method of how paths are resolved.
 *
 * Bit0:
 * symlink_follow:
 */
internal typealias lookupflags = Int

/** As long as the resolved path corresponds to a symbolic link, it is expanded. */
internal val lookupflags_symlink_follow = 1 shl 0
