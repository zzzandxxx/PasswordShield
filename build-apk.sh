#!/usr/bin/env bash
# 密码盾 APK 一键构建脚本（Git Bash / MSYS 可用，无需 Gradle / Android Studio）
# 依赖：JDK 8+（javac/keytool/jar/java）+ 本地 Android SDK（aapt2/zipalign/d8/apksigner）
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"          # aapt2 / zipalign 用
BTJ="$SDK/build-tools/30.0.3"         # d8 / apksigner jar 兼容 JDK8
AJ="$SDK/platforms/android-34/android.jar"
OUT="PasswordShield-v1.0.apk"

rm -rf build/classes build/gen build/dex build/*.apk
mkdir -p build/classes build/gen build/dex

echo "[1/7] 资源编译 (aapt2)"
sed 's/<manifest /<manifest package="com.passwordshield" /' app/src/main/AndroidManifest.xml > build/AndroidManifest.xml
"$BT/aapt2" compile --dir app/src/main/res -o build/res.zip
"$BT/aapt2" link -o build/app.base.apk -I "$AJ" \
    --manifest build/AndroidManifest.xml --java build/gen \
    --min-sdk-version 26 --target-sdk-version 34 \
    --version-code 1 --version-name 1.0.0 \
    build/res.zip

echo "[2/7] Java 编译 (javac)"
javac -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
    -bootclasspath "$AJ;$BT/core-lambda-stubs.jar" \
    -classpath build/gen -d build/classes \
    $(find app/src/main/java build/gen -name '*.java')

echo "[3/7] 转 dex (d8)"
java -cp "$BTJ/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 \
    --lib "$AJ" --output build/dex $(find build/classes -name '*.class')

echo "[4/7] 合并 dex"
(cd build/dex && jar uf ../app.base.apk classes.dex)

echo "[5/7] 对齐 (zipalign)"
"$BT/zipalign" -f 4 build/app.base.apk build/app.aligned.apk

echo "[6/7] 签名密钥"
KS="build/release.keystore"
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -alias shield \
        -storepass passwordshield123 -keypass passwordshield123 \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=PasswordShield,O=Personal,C=CN"
fi

echo "[7/7] 签名 (apksigner)"
java -jar "$BTJ/lib/apksigner.jar" sign --ks "$KS" \
    --ks-pass pass:passwordshield123 --ks-key-alias shield \
    --out "$OUT" build/app.aligned.apk

java -jar "$BTJ/lib/apksigner.jar" verify --print-certs "$OUT" | head -2
echo "完成: $OUT ($(du -h "$OUT" | cut -f1))"
