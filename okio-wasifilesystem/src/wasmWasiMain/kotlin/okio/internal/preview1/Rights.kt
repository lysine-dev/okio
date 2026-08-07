// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

/** `rights: Record`. */
internal typealias rights = Long

/**
 * The right to invoke [`fd_datasync`](#fd_datasync)0
 * If [`path_open`](#path_open) is set, includes the right to invoke
 * [`path_open`](#path_open) with [`fdflags::dsync`](#fdflags.dsync).
 */
internal val right_fd_datasync = 1L shl 0

/**
 * The right to invoke [`fd_read`](#fd_read) and [`sock_recv`](#sock_recv).
 * If [`rights::fd_seek`](#rights.fd_seek) is set, includes the right to invoke [`fd_pread`](#fd_pread).
 */
internal val right_fd_read = 1L shl 1

/** The right to invoke [`fd_seek`](#fd_seek). This flag implies [`rights::fd_tell`](#rights.fd_tell). */
internal val right_fd_seek = 1L shl 2

/** The right to invoke [`fd_fdstat_set_flags`](#fd_fdstat_set_flags). */
internal val right_fd_fdstat_set_flags = 1L shl 3

/**
 * The right to invoke [`fd_sync`](#fd_sync).
 * If [`path_open`](#path_open) is set, includes the right to invoke
 * [`path_open`](#path_open) with [`fdflags::rsync`](#fdflags.rsync) and [`fdflags::dsync`](#fdflags.dsync).
 */
internal val right_fd_sync = 1L shl 4

/**
 * The right to invoke [`fd_seek`](#fd_seek) in such a way that the file offset
 * remains unaltered (i.e., [`whence::cur`](#whence.cur) with offset zero), or to
 * invoke [`fd_tell`](#fd_tell).
 */
internal val right_fd_tell = 1L shl 5

/**
 * The right to invoke [`fd_write`](#fd_write) and [`sock_send`](#sock_send).
 * If [`rights::fd_seek`](#rights.fd_seek) is set, includes the right to invoke [`fd_pwrite`](#fd_pwrite).
 */
internal val right_fd_write = 1L shl 6

/** The right to invoke [`fd_advise`](#fd_advise). */
internal val right_fd_advise = 1L shl 7

/** The right to invoke [`fd_allocate`](#fd_allocate). */
internal val right_fd_allocate = 1L shl 8

/** The right to invoke [`path_create_directory`](#path_create_directory). */
internal val right_path_create_directory = 1L shl 9

/** If [`path_open`](#path_open) is set, the right to invoke [`path_open`](#path_open) with [`oflags::creat`](#oflags.creat). */
internal val right_path_create_file = 1L shl 10

/**
 * The right to invoke [`path_link`](#path_link) with the file descriptor as the
 * source directory.
 */
internal val right_path_link_source = 1L shl 11

/** The right to invoke [`path_link`](#path_link) with the file descriptor as the target directory.
 */
internal val right_path_link_target = 1L shl 12

/** The right to invoke [`path_open`](#path_open). */
internal val right_path_open = 1L shl 13

/** The right to invoke [`fd_readdir`](#fd_readdir). */
internal val right_fd_readdir = 1L shl 14

/** The right to invoke [`path_readlink`](#path_readlink). */
internal val right_path_readlink = 1L shl 15

/** The right to invoke [`path_rename`](#path_rename) with the file descriptor as the source directory. */
internal val right_path_rename_source = 1L shl 16

/** The right to invoke [`path_rename`](#path_rename) with the file descriptor as the target directory. */
internal val right_path_rename_target = 1L shl 17

/** The right to invoke [`path_filestat_get`](#path_filestat_get). */
internal val right_path_filestat_get = 1L shl 18

/**
 * The right to change a file's size.
 * If [`path_open`](#path_open) is set, includes the right to invoke [`path_open`](#path_open) with [`oflags::trunc`](#oflags.trunc).
 * Note: there is no function named `path_filestat_set_size`. This follows POSIX design,
 * which only has `ftruncate` and does not provide `ftruncateat`.
 * While such function would be desirable from the API design perspective, there are virtually
 * no use cases for it since no code written for POSIX systems would use it.
 * Moreover, implementing it would require multiple syscalls, leading to inferior performance.
 */
internal val right_path_filestat_set_size = 1L shl 19

/** The right to invoke [`path_filestat_set_times`](#path_filestat_set_times). */
internal val right_path_filestat_set_times = 1L shl 20

/** The right to invoke [`fd_filestat_get`](#fd_filestat_get). */
internal val right_fd_filestat_get = 1L shl 21

/** The right to invoke [`fd_filestat_set_size`](#fd_filestat_set_size). */
internal val right_fd_filestat_set_size = 1L shl 22

/** The right to invoke [`fd_filestat_set_times`](#fd_filestat_set_times). */
internal val right_fd_filestat_set_times = 1L shl 23

/** The right to invoke [`path_symlink`](#path_symlink). */
internal val right_path_symlink = 1L shl 24

/** The right to invoke [`path_remove_directory`](#path_remove_directory). */
internal val right_path_remove_directory = 1L shl 25

/** The right to invoke [`path_unlink_file`](#path_unlink_file). */
internal val right_path_unlink_file = 1L shl 26

/**
 * If [`rights::fd_read`](#rights.fd_read) is set, includes the right to invoke [`poll_oneoff`](#poll_oneoff) to subscribe to [`eventtype::fd_read`](#eventtype.fd_read).
 * If [`rights::fd_write`](#rights.fd_write) is set, includes the right to invoke [`poll_oneoff`](#poll_oneoff) to subscribe to [`eventtype::fd_write`](#eventtype.fd_write).
 */
internal val right_poll_fd_readwrite = 1L shl 27

/** The right to invoke [`sock_shutdown`](#sock_shutdown). */
internal val right_sock_shutdown = 1L shl 28

/** The right to invoke [`sock_accept`](#sock_accept). */
internal val right_sock_accept = 1L shl 29
