#!/usr/bin/env bash
# Cross-compiles the upstream iperf3 executable for every Android ABI the app
# ships and installs the results as app/src/main/jniLibs/<abi>/libiperf3.so.
#
# The "lib*.so" naming is deliberate: only files matching that pattern are
# extracted from the APK into nativeLibraryDir with the exec bit set, which is
# the only place an app may exec a binary from on modern Android.
set -euo pipefail

IPERF_VERSION="${IPERF_VERSION:-3.21}"
API="${API:-26}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
JNI_LIBS="$ROOT/../app/src/main/jniLibs"

: "${ANDROID_NDK_HOME:=$HOME/Library/Android/sdk/ndk/28.2.13676358}"
HOST_TAG="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
[ -d "$TOOLCHAIN" ] || { echo "NDK toolchain not found at $TOOLCHAIN" >&2; exit 1; }

mkdir -p "$BUILD"
TARBALL="$BUILD/iperf-$IPERF_VERSION.tar.gz"
SRC="$BUILD/iperf-$IPERF_VERSION"
[ -f "$TARBALL" ] || curl -sSL -o "$TARBALL" "https://downloads.es.net/pub/iperf/iperf-$IPERF_VERSION.tar.gz"
[ -d "$SRC" ] || tar xzf "$TARBALL" -C "$BUILD"

# abi:configure-host:clang-target-prefix
for entry in \
  "arm64-v8a:aarch64-linux-android:aarch64-linux-android" \
  "armeabi-v7a:armv7a-linux-androideabi:armv7a-linux-androideabi" \
  "x86_64:x86_64-linux-android:x86_64-linux-android" \
  "x86:i686-linux-android:i686-linux-android"
do
  ABI="${entry%%:*}"; rest="${entry#*:}"; HOST="${rest%%:*}"; PREFIX="${rest##*:}"
  OUT="$BUILD/$ABI"
  echo "=== building iperf3 $IPERF_VERSION for $ABI (API $API)"
  rm -rf "$OUT"; mkdir -p "$OUT"
  (
    cd "$OUT"
    export CC="$TOOLCHAIN/bin/${PREFIX}${API}-clang"
    export AR="$TOOLCHAIN/bin/llvm-ar" RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$TOOLCHAIN/bin/llvm-strip"
    export CFLAGS="-O2 -fPIE -DANDROID"
    export LDFLAGS="-fPIE -pie"
    "$SRC/configure" --host="$HOST" --build="$(uname -m)-apple-darwin" \
      --without-openssl --disable-shared \
      --disable-profiling >configure.log 2>&1 || { tail -30 configure.log; exit 1; }
    make -j"$(sysctl -n hw.ncpu)" >make.log 2>&1 || { tail -40 make.log; exit 1; }
    "$STRIP" src/iperf3
  )
  mkdir -p "$JNI_LIBS/$ABI"
  cp "$OUT/src/iperf3" "$JNI_LIBS/$ABI/libiperf3.so"
  echo "    -> $JNI_LIBS/$ABI/libiperf3.so ($(du -h "$JNI_LIBS/$ABI/libiperf3.so" | cut -f1))"
done
echo "done."
