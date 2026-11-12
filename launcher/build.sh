#!/usr/bin/env bash
# Build Calm Home without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Usage: build.sh <theme>          themes: nature | anime  (folders under themes/)
# Output: build/calm-home-<theme>.apk
#
# res/ holds what every theme shares; themes/<theme>/res is linked on top as an overlay
# (wallpapers, captions/accents, styles, badge layout, scrims, fonts).
#
# Signing key: $CALM_KEYSTORE (default ~/.android/calmhome.keystore). Its password
# is read from $CALM_KEYSTORE_PASS_FILE (default <keystore>.pass, mode 600). Both
# are created on first run. Keep them: Android only accepts updates signed with
# the same key.
set -euo pipefail
cd "$(dirname "$0")"

THEME="${1:?usage: build.sh <theme>  (one of: $(ls themes | tr '\n' ' '))}"
[[ "$THEME" =~ ^[a-z][a-z0-9-]{0,30}$ && -d "themes/$THEME/res" ]] || { echo "unknown theme: $THEME" >&2; exit 64; }

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:$PATH"
KS="${CALM_KEYSTORE:-$HOME/.android/calmhome.keystore}"
KS_PASS_FILE="${CALM_KEYSTORE_PASS_FILE:-$KS.pass}"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}-$THEME"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$JAR"; do
  [[ -e "$f" ]] || { echo "missing $f (install build-tools;34.0.0 and platforms;android-34)" >&2; exit 69; }
done

if [[ ! -f "$KS" ]]; then
  echo "Creating signing key $KS"
  mkdir -p "$(dirname "$KS")"
  (umask 077; openssl rand -base64 32 | tr -d '\n' > "$KS_PASS_FILE")
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -alias calmhome \
    -keyalg EC -groupname secp256r1 -validity 10000 -dname "CN=Calm Home" \
    -storepass:file "$KS_PASS_FILE" -keypass:file "$KS_PASS_FILE" >/dev/null
  chmod 600 "$KS"
fi

OUT="build/$THEME"
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir res -o "$OUT/res-common.zip"
"$BT/aapt2" compile --dir "themes/$THEME/res" -o "$OUT/res-theme.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o "$OUT/unaligned.apk" \
  --java "$OUT/gen" --min-sdk-version 28 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$OUT/res-common.zip" --auto-add-overlay -R "$OUT/res-theme.zip"

javac -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR" \
  -classpath "$BT/core-lambda-stubs.jar" -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')

"$BT/d8" --release --min-api 28 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -j ../unaligned.apk classes.dex)

"$BT/zipalign" -p -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias calmhome --ks-pass "file:$KS_PASS_FILE" \
  --out "build/calm-home-$THEME.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "build/calm-home-$THEME.apk"
ls -l "build/calm-home-$THEME.apk"
