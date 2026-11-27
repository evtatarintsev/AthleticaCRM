#!/usr/bin/env bash
# Сборка debug-APK, установка на подключённый по USB телефон и запуск.
# Использование: mise run deploy-android [-- https://адрес-сервера/api/]
set -euo pipefail
cd "$(dirname "$0")/../.."

fail() {
    printf '\n\033[31m✗ %s\033[0m\n\n%b\n' "$1" "$2" >&2
    exit 1
}

sdk="${ANDROID_HOME:-}"
if [[ -z "$sdk" && -f local.properties ]]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
sdk="${sdk:-$HOME/Library/Android/sdk}"
adb="$sdk/platform-tools/adb"
[[ -x "$adb" ]] || fail "Не найден adb в $sdk/platform-tools" \
"Установи Android SDK (Android Studio → Settings → Android SDK → SDK Tools → Platform-Tools)
или укажи путь: sdk.dir=... в local.properties либо переменную ANDROID_HOME."
export ANDROID_HOME="$sdk"

devices="$("$adb" devices | tail -n +2 | grep -v '^$' || true)"
[[ -n "$devices" ]] || fail "Телефон не виден" \
"1. Подключи телефон кабелем (кабель должен передавать данные, а не только заряжать).
2. На телефоне: Настройки → О телефоне → 7 раз тап по «Номер сборки» (на Xiaomi — «Версия MIUI/HyperOS»).
3. Настройки → Для разработчиков → включи «Отладка по USB».
4. При запросе режима USB выбери «Передача файлов».
Затем запусти задачу снова."

if grep -q unauthorized <<<"$devices"; then
    fail "Телефон не разрешил отладку с этого компьютера" \
"1. Разблокируй экран телефона — окно «Разрешить отладку по USB?» показывается только на нём.
2. Если окна нет: Для разработчиков → «Отозвать авторизацию отладки по USB»,
   затем выполни «$adb kill-server», переподключи кабель и запусти задачу снова.
3. Поставь галочку «Всегда разрешать с этого компьютера» и нажми «Разрешить».
Xiaomi/Redmi/POCO: в «Для разработчиков» включи также «Отладка по USB (настройки безопасности)»
и «Установка через USB» (нужен вход в Mi-аккаунт)."
fi
if grep -q offline <<<"$devices"; then
    fail "Телефон в состоянии offline" \
"Переподключи кабель или выполни «$adb kill-server» и запусти задачу снова."
fi
if (( $(grep -c 'device$' <<<"$devices") > 1 )); then
    fail "Подключено несколько устройств" \
"$devices
Оставь одно или выбери нужное: ANDROID_SERIAL=<серийник> mise run deploy-android"
fi

props=(-Pathletica.mobile=true)
if [[ $# -gt 0 ]]; then
    props+=("-Pathletica.mobile.apiUrl=$1")
    echo "Сервер API: $1"
fi

./gradlew "${props[@]}" :mobile:androidApp:installDebug || fail "Сборка или установка не удалась" \
"Смотри ошибку выше. Частые случаи:
• INSTALL_FAILED_USER_RESTRICTED — Xiaomi: включи «Установка через USB» в «Для разработчиков»
  и подтверди установку на экране телефона.
• INSTALL_FAILED_UPDATE_INCOMPATIBLE — стоит сборка с другой подписью:
  $adb uninstall org.athletica.mobile
• SDK location not found — укажи sdk.dir в local.properties."

"$adb" shell am start -n org.athletica.mobile/org.athletica.mobile.app.MainActivity >/dev/null
printf '\n\033[32m✓ Приложение установлено и запущено.\033[0m\nЛоги: %s logcat --pid=$(%s shell pidof org.athletica.mobile)\n' "$adb" "$adb"
