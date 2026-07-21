Android Test
============

This module runs Okio's test suite on a connected Android emulator or device. It requires the same
set-up as [OkHttp's android-test module][okhttp_android_test].

In brief, configure the Android SDK and PATH:

```
export ANDROID_SDK_ROOT=/Users/$USER/Library/Android/sdk
export PATH=$PATH:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools
```

Use `logcat` to stream test logs:

```
adb logcat '*:E' TestRunner:D TaskRunner:D GnssHAL_GnssInterface:F DeviceStateChecker:F memtrack:F
```

Then run the tests:

```
./gradlew :android-test:connectedAndroidTest
```

Or just a single test:

```
./gradlew :android-test:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=okio.SystemFileSystemTest
```


### Watch Out For Crashing Failures

Some of Okio's tests can cause the test process to crash. The test will be reported as a failure
with a message like this:

> Test failed to run to completion. Reason: 'Instrumentation run failed due to 'Process crashed.''.
> Check device logcat for details

When this happens, it's possible that tests are missing from the test run! One workaround is to
exclude the crashing test and re-run the rest. You can confirm that the test run completed normally
if a `run finished` line is printed in the logcat logs:

```
01-01 00:00:00.000 12345 23456 I TestRunner: run finished: 2976 tests, 0 failed, 3 ignored
```


[okhttp_android_test]: https://github.com/lysine-dev/okhttp/tree/master/android-test
