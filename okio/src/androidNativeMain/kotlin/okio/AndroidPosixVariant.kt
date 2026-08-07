package okio

import kotlin.concurrent.Volatile
import platform.posix.android_get_device_api_level

/**
 * Checked eagerly because on older Android versions (API < 30)
 * seccomp blocks the syscall with SIGSYS, crashing the app instead of returning ENOSYS.
 **/
@Volatile
internal actual var isStatXSupported = android_get_device_api_level() >= 30
