#!/usr/bin/env bash
# Rebuild the excluded generated SQLite archive from the retained upstream source.
set -euo pipefail
: "${DEVECO_SDK_HOME:?Set DEVECO_SDK_HOME to the DevEco SDK directory}"
sqlite_source_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
sqlite_native_dir="$DEVECO_SDK_HOME/default/openharmony/native"
sqlite_build_dir="$sqlite_source_dir/build/integration-arm64"
sqlite_out_dir="$sqlite_source_dir/lib/arm64-v8a"
mkdir -p "$sqlite_build_dir" "$sqlite_out_dir"
"$sqlite_native_dir/llvm/bin/clang" --target=aarch64-linux-ohos \
    --sysroot="$sqlite_native_dir/sysroot" -O2 \
    -DSQLITE_THREADSAFE=1 -DSQLITE_ENABLE_FTS5 -DSQLITE_ENABLE_RTREE \
    -DSQLITE_ENABLE_JSON1 -DSQLITE_ENABLE_COLUMN_METADATA \
    -DSQLITE_ENABLE_MATH_FUNCTIONS -DSQLITE_OMIT_LOAD_EXTENSION \
    -DSQLITE_DEFAULT_MEMSTATUS=0 -DSQLITE_TEMP_STORE=2 -DHAVE_USLEEP=1 \
    -c "$sqlite_source_dir/sqlite3.c" -o "$sqlite_build_dir/sqlite3.o"
"$sqlite_native_dir/llvm/bin/llvm-ar" rcs \
    "$sqlite_out_dir/libsqlite3.a" "$sqlite_build_dir/sqlite3.o"
printf 'SQLite archive: %s\n' "$sqlite_out_dir/libsqlite3.a"
