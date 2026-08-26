# iperf3 for Android

Full iperf3 client/server with a Compose UI, background execution and live
throughput in the notification shade.

## Layout

- `native/build-iperf.sh` — cross-compiles upstream iperf 3.21 with the NDK for
  all four ABIs and installs each binary as `app/src/main/jniLibs/<abi>/libiperf3.so`.
  The `lib*.so` name matters: it is what makes the installer extract the file
  into `nativeLibraryDir`, the only app directory Android allows `exec()` from.
  Requires `android:extractNativeLibs="true"` / `useLegacyPackaging = true`.
- `IperfEngine` — process-wide singleton that runs the binary with
  `--json-stream --forceflush` and turns each event line into `EngineState`.
- `IperfService` — foreground service (`dataSync`) owning the notification,
  the wake lock and the run's background lifetime. The test itself lives in the engine.
- `History` / `Settings` — finished runs (JSON file) and last-used config plus
  named profiles (DataStore).
- `ui/` — Compose screens: setup form, live run view with chart, history.

## Building

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./native/build-iperf.sh      # only needed when bumping the iperf3 version
./gradlew installDebug
```

## Status

Verified end to end on a clean Android 16 (API 36) emulator:

- native binaries build and run on Android (`iperf 3.21`), all four ABIs;
- the `--json-stream` events (TCP, UDP, `--bidir`, error) match the parser;
- live run screen: speed, chart, progress, transferred bytes, retransmits;
- notification: `↑ 567 Mbit/s` / `26 s of 30 s · 5.92 GB transferred` with a
  determinate progress bar and a Stop action;
- background: the test keeps running with the app on the home screen
  (foreground service stays up, notification keeps updating);
- completion posts a result notification, stops the service, leaves no stray
  process, and writes the run to history;
- stop, connection-failure and server modes all behave (server showed
  "Waiting for clients" → "Serving test #1"; an external client measured
  6.58 Gbit/s against it).

Exercised only at the protocol level, not through the UI: UDP jitter/loss and
`--bidir` rendering (the JSON was verified against the device; the screens reuse
the same summary rendering as TCP).

## Release APK

`./gradlew assembleRelease` produces a signed, R8-shrunk APK at
`app/build/outputs/apk/release/app-release.apk` (~1.7 MB, universal: all four ABIs).

Signing reads `keystore.properties` in the project root (gitignored, along with
`release.keystore`). Keep both — Android only allows updates signed with the same
key, so losing them means uninstalling before you can install a new build.
Without those files the release build still compiles, just unsigned.
