// Copyright 2019-2023 the Contributors to the WASI Specification
// This file is adapted from the WASI preview1 spec here:
// https://github.com/WebAssembly/WASI/blob/main/legacy/preview1/docs.md
package okio.internal.preview1

@Suppress("ktlint:enum-entry-name-case")
internal enum class Errno {
  /** `success`: No error occurred. System call completed successfully. */
  success,

  /** `2big`: Argument list too long. */
  toobig,

  /** `acces`: Permission denied. */
  acces,

  /** `addrinuse`: Address in use. */
  addrinuse,

  /** `addrnotavail`: Address not available. */
  addrnotavail,

  /** `afnosupport`: Address family not supported. */
  afnosupport,

  /** `again`: Resource unavailable, or operation would block. */
  again,

  /** `already`: Connection already in progress. */
  already,

  /** `badf`: Bad file descriptor. */
  badf,

  /** `badmsg`: Bad message. */
  badmsg,

  /** `busy`: Device or resource busy. */
  busy,

  /** `canceled`: Operation canceled. */
  canceled,

  /** `child`: No child processes. */
  child,

  /** `connaborted`: Connection aborted. */
  connaborted,

  /** `connrefused`: Connection refused. */
  connrefused,

  /** `connreset`: Connection reset. */
  connreset,

  /** `deadlk`: Resource deadlock would occur. */
  deadlk,

  /** `destaddrreq`: Destination address required. */
  destaddrreq,

  /** `dom`: Mathematics argument out of domain of function. */
  dom,

  /** `dquot`: Reserved. */
  dquot,

  /** `exist`: File exists. */
  exist,

  /** `fault`: Bad address. */
  fault,

  /** `fbig`: File too large. */
  fbig,

  /** `hostunreach`: Host is unreachable. */
  hostunreach,

  /** `idrm`: Identifier removed. */
  idrm,

  /** `ilseq`: Illegal byte sequence. */
  ilseq,

  /** `inprogress`: Operation in progress. */
  inprogress,

  /** `intr`: Interrupted function. */
  intr,

  /** `inval`: Invalid argument. */
  inval,

  /** `io`: I/O error. */
  io,

  /** `isconn`: Socket is connected. */
  isconn,

  /** `isdir`: Is a directory. */
  isdir,

  /** `loop`: Too many levels of symbolic links. */
  loop,

  /** `mfile`: File descriptor value too large. */
  mfile,

  /** `mlink`: Too many links. */
  mlink,

  /** `msgsize`: Message too large. */
  msgsize,

  /** `multihop`: Reserved. */
  multihop,

  /** `nametoolong`: Filename too long. */
  nametoolong,

  /** `netdown`: Network is down. */
  netdown,

  /** `netreset`: Connection aborted by network. */
  netreset,

  /** `netunreach`: Network unreachable. */
  netunreach,

  /** `nfile`: Too many files open in system. */
  nfile,

  /** `nobufs`: No buffer space available. */
  nobufs,

  /** `nodev`: No such device. */
  nodev,

  /** `noent`: No such file or directory. */
  noent,

  /** `noexec`: Executable file format error. */
  noexec,

  /** `nolck`: No locks available. */
  nolck,

  /** `nolink`: Reserved. */
  nolink,

  /** `nomem`: Not enough space. */
  nomem,

  /** `nomsg`: No message of the desired type. */
  nomsg,

  /** `noprotoopt`: Protocol not available. */
  noprotoopt,

  /** `nospc`: No space left on device. */
  nospc,

  /** `nosys`: Function not supported. */
  nosys,

  /** `notconn`: The socket is not connected. */
  notconn,

  /** `notdir`: Not a directory or a symbolic link to a directory. */
  notdir,

  /** `notempty`: Directory not empty. */
  notempty,

  /** `notrecoverable`: State not recoverable. */
  notrecoverable,

  /** `notsock`: Not a socket. */
  notsock,

  /** `notsup`: Not supported, or operation not supported on socket. */
  notsup,

  /** `notty`: Inappropriate I/O control operation. */
  notty,

  /** `nxio`: No such device or address. */
  nxio,

  /** `overflow`: Value too large to be stored in data type. */
  overflow,

  /** `ownerdead`: Previous owner died. */
  ownerdead,

  /** `perm`: Operation not permitted. */
  perm,

  /** `pipe`: Broken pipe. */
  pipe,

  /** `proto`: Protocol error. */
  proto,

  /** `protonosupport`: Protocol not supported. */
  protonosupport,

  /** `prototype`: Protocol wrong type for socket. */
  prototype_,

  /** `range`: Result too large. */
  range,

  /** `rofs`: Read-only file system. */
  rofs,

  /** `spipe`: Invalid seek. */
  spipe,

  /** `srch`: No such process. */
  srch,

  /** `stale`: Reserved. */
  stale,

  /** `timedout`: Connection timed out. */
  timedout,

  /** `txtbsy`: Text file busy. */
  txtbsy,

  /** `xdev`: Cross-device link. */
  xdev,

  /** `notcapable`: Extension: Capabilities insufficient. */
  notcapable,
}
