#!/bin/sh
# Скрипт сборки watchButtons (app + wear APK)
# Работает и под sh (dash), и под bash.
# Использование:
#   ./build.sh                — сборка debug
#   ./build.sh release        — сборка release
#   ./build.sh install        — сборка debug + установка на устройство (adb)
#   ./build.sh clean          — очистка сборки

set -eu

PROJECT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$PROJECT_DIR"

MODE="${1:-debug}"

# ── Поиск JDK 17+ ──────────────────────────────────────────────
java_ok() {
    "$1" -version 2>&1 | head -1 | grep -qE 'version "(17|2[1-9])'
}

find_jdk() {
    dir="${JAVA_HOME:-}"
    if [ -n "$dir" ] && [ -x "$dir/bin/java" ] && java_ok "$dir/bin/java"; then
        echo "$dir"; return 0
    fi
    if command -v java >/dev/null 2>&1 && java_ok java; then
        echo "system"; return 0
    fi
    for dir in \
        "$HOME/Android/Sdk/jdk" \
        "/tmp/opencode/jdk" \
        "/opt/android-studio/jbr" \
        "$HOME/android-studio/jbr" \
        "$HOME/.local/share/JetBrains"/*/jbr \
        /usr/lib/jvm/java-17-openjdk-* \
        /usr/lib/jvm/temurin-17-* \
        /usr/lib/jvm/*; do
        if [ -x "$dir/bin/java" ] && java_ok "$dir/bin/java"; then
            echo "$dir"; return 0
        fi
    done
    return 1
}

JAVA_HOME_FOUND=$(find_jdk) || {
    echo "ОШИБКА: не найден JDK 17+."
    echo "Установите: sudo apt install openjdk-17-jdk  или скачайте https://adoptium.net"
    exit 1
}
if [ "$JAVA_HOME_FOUND" = "system" ]; then
    echo ">> JDK: системный"
    JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 | grep 'java.home' | awk '{print $3}')
    export JAVA_HOME
else
    echo ">> JDK: $JAVA_HOME_FOUND"
    JAVA_HOME="$JAVA_HOME_FOUND"
    export JAVA_HOME
fi
export PATH="$JAVA_HOME/bin:$PATH"

# ── Поиск Android SDK ──────────────────────────────────────────
find_sdk() {
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME:-}" ]; then
        echo "${ANDROID_HOME}"; return 0
    fi
    if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "${ANDROID_SDK_ROOT:-}" ]; then
        echo "${ANDROID_SDK_ROOT}"; return 0
    fi
    for dir in "$HOME/Android/Sdk" /opt/android-sdk "$HOME/android-sdk"; do
        if [ -d "$dir" ]; then
            echo "$dir"; return 0
        fi
    done
    return 1
}

SDK=$(find_sdk) || {
    echo "ОШИБКА: не найден Android SDK."
    echo "Установите Android Studio или sdkmanager, затем задайте ANDROID_HOME."
    exit 1
}
echo ">> Android SDK: $SDK"
if [ ! -f local.properties ]; then
    echo "sdk.dir=$SDK" > local.properties
fi

# ── Запуск gradle (gradlew или системный) ──────────────────────
run_gradle() {
    if [ -x ./gradlew ]; then
        ./gradlew "$@"
    elif command -v gradle >/dev/null 2>&1; then
        gradle "$@"
    else
        echo "ОШИБКА: нет ./gradlew и не найден gradle в PATH."
        exit 1
    fi
}

# ── Сборка ─────────────────────────────────────────────────────
print_result() {
    echo ""
    echo "═══════════ Сборка завершена ═══════════"
    for apk in "$1" "$2"; do
        if [ -f "$apk" ]; then
            SIZE=$(du -h "$apk" | cut -f1)
            echo "  $apk ($SIZE)"
        else
            echo "  НЕ НАЙДЕН: $apk"
            exit 1
        fi
    done
}

case "$MODE" in
    debug)
        echo ">> Сборка debug..."
        run_gradle assembleDebug
        print_result "app/build/outputs/apk/debug/watchButtons-debug.apk" \
                     "wear/build/outputs/apk/debug/watchButtons-wear-debug.apk"
        ;;
    release)
        echo ">> Сборка release..."
        run_gradle assembleRelease
        APP_APK="app/build/outputs/apk/release/watchButtons.apk"
        WEAR_APK="wear/build/outputs/apk/release/watchButtons-wear.apk"
        print_result "$APP_APK" "$WEAR_APK"
        ;;
    clean)
        echo ">> Очистка..."
        run_gradle clean
        echo "Готово."
        ;;
    install)
        echo ">> Сборка debug + установка..."
        run_gradle assembleDebug
        ADB=$(find "$SDK" -name adb -path "*platform-tools*" 2>/dev/null | head -1)
        if [ -z "$ADB" ]; then
            echo "ОШИБКА: adb не найден в SDK"
            exit 1
        fi
        echo ">> Установка на телефон..."
        "$ADB" install -r app/build/outputs/apk/debug/watchButtons-debug.apk
        echo ">> Установка на часы..."
        "$ADB" install -r wear/build/outputs/apk/debug/watchButtons-wear-debug.apk
        echo "Готово. Оба APK установлены."
        ;;
    *)
        echo "Неизвестный режим: $MODE (доступно: debug | release | install | clean)"
        exit 1
        ;;
esac