#!/bin/sh
set -eu
project_dir=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to an SDK containing NDK 30.0.16248370}"
ndk_dir="$ANDROID_SDK_ROOT/ndk/30.0.16248370/toolchains/llvm/prebuilt/darwin-x86_64/bin"
export GOTOOLCHAIN=go1.27.3 CGO_ENABLED=1 GOOS=android GOARCH=arm64
export CC="$ndk_dir/aarch64-linux-android26-clang"
cd "$project_dir/remote-core"
target="$project_dir/output/components-core/arm64-v8a"
mkdir -p "$target"
go build -trimpath -tags ts_omit_logtail -buildmode=c-shared -ldflags='-s -w -extldflags=-Wl,-z,max-page-size=16384' -o "$target/libquietremote.so" .
