#!/bin/bash
# Builds GharHome.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
rm -rf out && mkdir -p out/res out/classes out/dex
"$BT/aapt2" compile --dir res -o out/res
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 19 --target-sdk-version 23 -A assets out/res/*.flat
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" \
  -d out/classes $(find src -name '*.java')
"$SDK/build-tools/36.0.0/d8" --release --min-api 19 --lib "$JAR" --output out/dex $(find out/classes -name '*.class')
cp out/base.apk out/unsigned.apk
(cd out/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk
# The signing key stays in this folder: updates must be signed with the same one.
if [ ! -f gharhome.keystore ]; then
  keytool -genkeypair -keystore gharhome.keystore -storepass gharhome -keypass gharhome \
    -alias ghar -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Ghar Home" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks gharhome.keystore --ks-pass pass:gharhome --key-pass pass:gharhome \
  --min-sdk-version 19 --out GharHome.apk out/aligned.apk
"$BT/apksigner" verify --min-sdk-version 19 GharHome.apk && ls -lh GharHome.apk
