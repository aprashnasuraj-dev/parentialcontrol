#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK" ]]; then
  echo "Set ANDROID_SDK_ROOT or ANDROID_HOME to your Android SDK." >&2
  exit 1
fi

BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
PLATFORM="${ANDROID_PLATFORM:-android-36}"
BT="$SDK/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$SDK/platforms/$PLATFORM/android.jar"
OUT="$ROOT/build/offline"

for tool in aapt2 d8 zipalign apksigner; do
  [[ -x "$BT/$tool" ]] || { echo "Missing $BT/$tool" >&2; exit 1; }
done
[[ -f "$ANDROID_JAR" ]] || { echo "Missing $ANDROID_JAR" >&2; exit 1; }
command -v kotlinc >/dev/null || { echo "kotlinc is required" >&2; exit 1; }

rm -rf "$OUT/classes" "$OUT/dex"
mkdir -p "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/compiled-res.zip"
"$BT/aapt2" link \
  -o "$OUT/Charikot-base-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 36 \
  --version-code 2 \
  --version-name 0.1.1 \
  --auto-add-overlay \
  "$OUT/compiled-res.zip"

mapfile -t SRC < <(find "$ROOT/app/src/main/java" -name '*.kt' -type f | sort)
kotlinc -jvm-target 1.8 -classpath "$ANDROID_JAR" -no-reflect "${SRC[@]}" -d "$OUT/classes"
jar cf "$OUT/app-classes.jar" -C "$OUT/classes" .

KOTLIN_HOME="${KOTLIN_HOME:-$(dirname "$(dirname "$(command -v kotlinc)")")}" 
KLIB="$KOTLIN_HOME/lib"
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" \
  "$OUT/app-classes.jar" \
  "$KLIB/kotlin-stdlib.jar" \
  "$KLIB/kotlin-stdlib-jdk7.jar" \
  "$KLIB/kotlin-stdlib-jdk8.jar"

cp "$OUT/Charikot-base-unsigned.apk" "$OUT/Charikot-with-dex-unsigned.apk"
(cd "$OUT/dex" && zip -q -0 "$OUT/Charikot-with-dex-unsigned.apk" classes.dex)
"$BT/zipalign" -f -P 16 4 \
  "$OUT/Charikot-with-dex-unsigned.apk" \
  "$OUT/Charikot-0.1.1-unsigned-aligned.apk"

if [[ -n "${CHARIKOT_KEYSTORE:-}" ]]; then
  : "${CHARIKOT_KEY_ALIAS:?Set CHARIKOT_KEY_ALIAS}"
  : "${CHARIKOT_KEYSTORE_PASSWORD:?Set CHARIKOT_KEYSTORE_PASSWORD}"
  KEY_PASS="${CHARIKOT_KEY_PASSWORD:-$CHARIKOT_KEYSTORE_PASSWORD}"
  "$BT/apksigner" sign \
    --ks "$CHARIKOT_KEYSTORE" \
    --ks-key-alias "$CHARIKOT_KEY_ALIAS" \
    --ks-pass "pass:$CHARIKOT_KEYSTORE_PASSWORD" \
    --key-pass "pass:$KEY_PASS" \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUT/Charikot-0.1.1-signed.apk" \
    "$OUT/Charikot-0.1.1-unsigned-aligned.apk"
  "$BT/apksigner" verify --verbose --print-certs "$OUT/Charikot-0.1.1-signed.apk"
  "$BT/zipalign" -c -P 16 -v 4 "$OUT/Charikot-0.1.1-signed.apk"
  sha256sum "$OUT/Charikot-0.1.1-signed.apk"
else
  echo "Unsigned APK ready: $OUT/Charikot-0.1.1-unsigned-aligned.apk"
  echo "To sign, set CHARIKOT_KEYSTORE, CHARIKOT_KEY_ALIAS and CHARIKOT_KEYSTORE_PASSWORD."
fi
