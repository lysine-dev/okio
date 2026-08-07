// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

/** `u32`. */
internal typealias size = Int

/**
 * `Handle`.
 *
 * A file descriptor handle.
 */
internal typealias fd = Int

/**
 * `u64`.
 *
 * A reference to the offset of a directory entry.
 *
 * The value 0 signifies the start of the directory.
 */
internal typealias dircookie = Long

/**
 * `Variant`.
 *
 * Error codes returned by functions. Not all of these error codes are returned by the functions
 * provided by this API; some are used in higher-level library layers, and others are provided
 * merely for alignment with POSIX.
 */
internal typealias errno = Short

/**
 * `u64`.
 *
 * File serial number that is unique within its file system.
 */
internal typealias inode = Long

/**
 * `u32`.
 *
 * The type for the [`dirent::d_namlen`](#dirent.d_namlen) field of [`dirent`](#dirent) struct.
 */
internal typealias dirnamelen = Int

/**
 * `Pointer<u8>`.
 */
internal typealias PointerU8 = Int

/**
 * path_create_directory(fd: fd, path: string) -> Result<(), errno>
 *
 * Create a directory.
 * Note: This is similar to `mkdirat` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_create_directory")
internal external fun path_create_directory(
  fd: fd,
  path: PointerU8,
  pathSize: size,
): Int // should be Short??

/**
 * path_filestat_get(fd: fd, flags: lookupflags, path: string) -> Result<filestat, errno>
 *
 * Return the attributes of a file or directory.
 * Note: This is similar to `stat` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_filestat_get")
internal external fun path_filestat_get(
  fd: fd,
  flags: lookupflags,
  path: PointerU8,
  pathSize: size,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * path_open(fd: fd, dirflags: lookupflags, path: string, oflags: oflags, fs_rights_base: rights, fs_rights_inheriting: rights, fdflags: fdflags) -> Result<fd, errno>
 *
 * Open a file or directory.
 * The returned file descriptor is not guaranteed to be the lowest-numbered
 * file descriptor not currently open; it is randomized to prevent
 * applications from depending on making assumptions about indexes, since this
 * is error-prone in multi-threaded contexts. The returned file descriptor is
 * guaranteed to be less than 2**31.
 * Note: This is similar to `openat` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_open")
internal external fun path_open(
  fd: fd,
  dirflags: lookupflags,
  path: PointerU8,
  pathSize: size,
  oflags: oflags,
  fs_rights_base: rights,
  fs_rights_inheriting: rights,
  fdflags: fdflags,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * path_readlink(fd: fd, path: string, buf: Pointer<u8>, buf_len: size) -> Result<size, errno>
 *
 * Read the contents of a symbolic link.
 * Note: This is similar to `readlinkat` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_readlink")
internal external fun path_readlink(
  fd: fd,
  path: PointerU8,
  pathSize: size,
  buf: PointerU8,
  buf_len: size,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * path_remove_directory(fd: fd, path: string) -> Result<(), errno>
 *
 * Remove a directory.
 * Return [`errno::notempty`](#errno.notempty) if the directory is not empty.
 * Note: This is similar to `unlinkat(fd, path, AT_REMOVEDIR)` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_remove_directory")
internal external fun path_remove_directory(
  fd: fd,
  path: PointerU8,
  pathSize: size,
): Int // should be Short??

/**
 * path_rename(fd: fd, old_path: string, new_fd: fd, new_path: string) -> Result<(), errno>
 *
 * Rename a file or directory.
 * Note: This is similar to `renameat` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_rename")
internal external fun path_rename(
  fd: fd,
  old_path: PointerU8,
  old_pathSize: size,
  new_fd: fd,
  new_path: PointerU8,
  new_pathSize: size,
): Int // should be Short??

/**
 * path_symlink(old_path: string, fd: fd, new_path: string) -> Result<(), errno>
 *
 * Create a symbolic link.
 * Note: This is similar to `symlinkat` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_symlink")
internal external fun path_symlink(
  old_path: PointerU8,
  old_pathSize: size,
  fd: fd,
  new_path: PointerU8,
  new_pathSize: size,
): Int // should be Short??

/**
 * path_unlink_file(fd: fd, path: string) -> Result<(), errno>
 *
 * Unlink a file.
 * Return [`errno::isdir`](#errno.isdir) if the path refers to a directory.
 * Note: This is similar to `unlinkat(fd, path, 0)` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "path_unlink_file")
internal external fun path_unlink_file(
  fd: fd,
  path: PointerU8,
  pathSize: size,
): Int // should be Short??

/**
 * fd_close(fd: fd) -> Result<(), errno>
 *
 * Close a file descriptor.
 * Note: This is similar to `close` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "fd_close")
internal external fun fd_close(
  fd: fd,
): Int // should be Short??

/**
 * fd_filestat_get(fd: fd) -> Result<filestat, errno>
 *
 * Return the attributes of an open file.
 */
@WasmImport("wasi_snapshot_preview1", "fd_filestat_get")
internal external fun fd_filestat_get(
  fd: fd,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_pread(fd: fd, iovs: iovec_array, offset: filesize) -> Result<size, errno>
 *
 * Read from a file descriptor.
 * Note: This is similar to `readv` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "fd_pread")
internal external fun fd_pread(
  fd: fd,
  iovs: PointerU8,
  iovsSize: size,
  offset: Long,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_prestat_dir_name(fd: fd, path: Pointer<u8>, path_len: size) -> Result<(), errno>
 *
 * Return a description of the given preopened file descriptor.
 */
@WasmImport("wasi_snapshot_preview1", "fd_prestat_dir_name")
internal external fun fd_prestat_dir_name(
  fd: fd,
  path: PointerU8,
  pathSize: size,
): Int // should be Short??

/**
 * fd_prestat_get(fd: fd) -> Result<prestat, errno>
 *
 * Return a description of the given preopened file descriptor.
 */
@WasmImport("wasi_snapshot_preview1", "fd_prestat_get")
internal external fun fd_prestat_get(
  fd: fd,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_pwrite(fd: fd, iovs: ciovec_array, offset: filesize) -> Result<size, errno>`
 *
 * Write to a file descriptor, without using and updating the file descriptor's offset.
 * Note: This is similar to `pwritev` in Linux (and other Unix-es).
 *
 * Like Linux (and other Unix-es), any calls of `pwrite` (and other
 * functions to read or write) for a regular file by other threads in the
 * WASI process should not be interleaved while `pwrite` is executed.
 */
@WasmImport("wasi_snapshot_preview1", "fd_pwrite")
internal external fun fd_pwrite(
  fd: fd,
  iovs: PointerU8,
  iovsSize: size,
  offset: Long,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_read(fd: fd, iovs: iovec_array) -> Result<size, errno>
 *
 * Read from a file descriptor.
 * Note: This is similar to `readv` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "fd_read")
internal external fun fd_read(
  fd: fd,
  iovs: PointerU8,
  iovsSize: size,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_readdir(fd: fd, buf: Pointer<u8>, buf_len: size, cookie: dircookie) -> Result<size, errno>
 *
 * Read directory entries from a directory.
 * When successful, the contents of the output buffer consist of a sequence of
 * directory entries. Each directory entry consists of a [`dirent`](#dirent) object,
 * followed by [`dirent::d_namlen`](#dirent.d_namlen) bytes holding the name of the directory
 * entry.
 * This function fills the output buffer as much as possible, potentially
 * truncating the last directory entry. This allows the caller to grow its
 * read buffer size in case it's too small to fit a single large directory
 * entry, or skip the oversized directory entry.
 */
@WasmImport("wasi_snapshot_preview1", "fd_readdir")
internal external fun fd_readdir(
  fd: fd,
  buf: PointerU8,
  buf_len: size,
  cookie: dircookie,
  returnPointer: PointerU8,
): Int // should be Short??

/**
 * fd_filestat_set_size(fd: fd, size: filesize) -> Result<(), errno>
 *
 * Adjust the size of an open file. If this increases the file's size, the extra bytes are filled with zeros.
 * Note: This is similar to `ftruncate` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "fd_filestat_set_size")
internal external fun fd_filestat_set_size(
  fd: fd,
  size: Long,
): Int // should be Short??

/**
 * fd_sync(fd: fd) -> Result<(), errno>
 *
 * Synchronize the data and metadata of a file to disk.
 * Note: This is similar to `fsync` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "fd_sync")
internal external fun fd_sync(
  fd: fd,
): Int // should be Short??

/**
 * fd_write(fd: fd, iovs: ciovec_array) -> Result<size, errno>
 *
 * Write to a file descriptor.
 * Note: This is similar to `writev` in POSIX.
 *
 * Like POSIX, any calls of `write` (and other functions to read or write)
 * for a regular file by other threads in the WASI process should not be
 * interleaved while `write` is executed.
 */
@WasmImport("wasi_snapshot_preview1", "fd_write")
internal external fun fd_write(
  fd: fd,
  iovs: PointerU8,
  iovsSize: size,
  returnPointer: PointerU8,
): Int // should be Short??
