# Watch Buttons

[🇬🇧 English](#english) · [🇷🇺 Русский](#русский)

---

<a name="english"></a>

**Watch Buttons** — a paired Android + Wear OS app: control sound modes on your phone and smartwatch with a single tap, and fire HTTP GET/POST requests to any URL from your own custom buttons.

## Features

### Sound modes (phone + watch)
| Button | Action |
|---|---|
| **Do Not Disturb** | Full silence: sound and vibration off on both phone and watch |
| **Vibration only** | Ring volume muted, vibration stays on |
| **Normal mode** | Regular notifications with sound |

Pressing a mode in either app (phone or watch) applies it to **both** devices instantly.

Sound control uses system DND access when available and automatically falls back to stream volume control (no special permissions needed), so it works on watches where the DND access screen doesn't exist.

### Custom buttons (HTTP requests)
- Buttons that send **GET** or **POST** requests to any URL / IP address
  (Home Assistant, Node-RED, your own server, a router, etc.)
- **Two actions per button**: single tap — first action, fast double tap — second action
  (e.g. one tap turns the light on, double tap turns it off)
- Long press on a button to edit (name, URL, method, body, color) or delete it
- Toast shows the execution result; optionally includes the server response text (configurable in settings)

### Interface
- Button layout: **1, 2 or 3 per row**, or **one centered**
- Per-button **color** from a palette
- The list on the watch scrolls with the **bezel / crown** or swipe
- Custom buttons and settings **sync automatically** to the watch via the Wearable Data API
- In-app settings: hide sound-mode buttons, show/hide server response text, UI language
- Languages: **English** (default), **Русский**, **中文**

## Build

### Requirements
- JDK 17+
- Android SDK (platform 34, build-tools 34)

### Quick way
```bash
./build.sh            # debug APKs (phone + watch)
./build.sh release    # signed release APKs
./build.sh install    # build + install to connected adb devices
./build.sh clean      # clean up
```
The script finds JDK and Android SDK automatically (`JAVA_HOME` / `ANDROID_HOME` / standard paths).

### Release signing
Release builds are signed automatically if `keystore.properties` exists in the project root:
```properties
storeFile=keystore/watchbuttons.keystore
storePassword=<password>
keyAlias=watchbuttons
keyPassword=<password>
```
`keystore/` and `keystore.properties` are in `.gitignore` — don't lose them:
the keystore is required to ship app updates (otherwise the old version must be uninstalled first).
Create your own keystore:
```bash
keytool -genkeypair -v -keystore keystore/watchbuttons.keystore -alias watchbuttons \
  -keyalg RSA -keysize 2048 -validity 10000
```

### Manually
```bash
./gradlew assembleDebug
# APKs:
#   app/build/outputs/apk/debug/watchButtons-debug.apk
#   wear/build/outputs/apk/debug/watchButtons-wear-debug.apk
# release:
#   app/build/outputs/apk/release/watchButtons.apk
#   wear/build/outputs/apk/release/watchButtons-wear.apk
```

### Android Studio
Open the project folder as a regular Android project — the `app` and `wear` modules are picked up automatically.
Note: both modules use the **same applicationId** (`com.watchbuttons.app`) — this is required
for the Wearable API to deliver messages and data between the devices.

## Installation

```bash
adb install app/build/outputs/apk/debug/watchButtons-debug.apk       # phone
adb install wear/build/outputs/apk/debug/watchButtons-wear-debug.apk # watch
```
On first launch, grant Do Not Disturb access on the phone (banner in the app). This is optional on the watch — sound modes work there via volume control.

## Usage

1. Open the phone app, tap **«+»** and create a button, for example:
   - Name: `Kitchen light`
   - Single tap: `POST http://192.168.1.10/api/light` + body `{"state": "on"}`
   - Double tap (optional): `POST http://192.168.1.10/api/light` + `{"state": "off"}`
2. Pick a color and a layout (1/2/3 per row, centered)
3. The button appears on the watch automatically
4. Tapping it sends the request; the notification shows the result (HTTP code or error)

## Project structure

```
watchButtons/
├── app/                        # phone app
│   └── src/main/java/com/watchbuttons/app/
│       ├── MainActivity.kt     # screen (Compose): buttons, dialogs, settings
│       ├── ButtonData.kt       # button model and actions (ButtonAction)
│       ├── ButtonStore.kt      # SharedPreferences storage + sync to the watch
│       ├── SoundController.kt  # sound/DND control with volume fallback
│       ├── HttpExecutor.kt     # GET/POST execution
│       ├── Language.kt         # UI language selection
│       ├── Sync.kt             # sends commands to the watch
│       └── PhoneMessageService.kt  # receives commands from the watch
├── wear/                       # watch app (Wear OS)
│   └── src/main/java/com/watchbuttons/wear/
│       ├── MainActivity.kt     # watch screen, bezel scrolling
│       ├── WearButtonStore.kt  # reads buttons from the Data API
│       └── ...                 # matching controllers
├── build.sh                    # build script
└── build.gradle.kts, settings.gradle.kts
```

## Adding a new language

1. Copy `app/src/main/res/values/strings.xml` to `values-<code>/`
   (e.g. `values-de/` for German) and translate the strings
2. Same for `wear/src/main/res/values/strings.xml`
3. Add the language to `Language.options` in both
   `app/.../Language.kt` and `wear/.../Language.kt`:
   ```kotlin
   val options = listOf(
       "en" to "English",
       "ru" to "Русский",
       "zh" to "中文",
       "de" to "Deutsch"
   )
   ```

## Technical details

- **Minimum versions**: Android 8.0 (API 26) — phone, Wear OS 3 (API 30) — watch
- **UI**: Jetpack Compose (Material 3 on phone, Wear Compose on watch)
- **Phone ↔ watch**: Google Wearable Data API / Message API (play-services-wearable)
- **Storage**: local, SharedPreferences + JSON (no server)
- **Permissions**: `INTERNET`, `ACCESS_NOTIFICATION_POLICY` (optional, sound modes work without it)

## Roadmap ideas

- Custom HTTP headers and basic auth for requests
- Widgets and Tiles on the watch
- Button grouping by pages

---

<a name="русский"></a>

# Watch Buttons (по-русски)

**Watch Buttons** — связка приложений Android + Wear OS: одним нажатием управляй режимами звука телефона и смарт-часов и отправляй HTTP GET/POST запросы на любые адреса через свои кнопки.

## Возможности

### Режимы звука (телефон + часы)
| Кнопка | Действие |
|---|---|
| **Не беспокоить** | Полная тишина: звук и вибрация выключены на телефоне и на часах |
| **Только вибрация** | Звук звонка выключен, вибрация остаётся |
| **Обычный режим** | Обычные уведомления со звуком |

Нажатие режима в любом приложении (телефон или часы) применяется сразу на **обоих** устройствах.

Управление звуком использует системный DND-доступ, когда он доступен, и автоматически откатывается к управлению громкостью потоков (без специальных разрешений) — поэтому работает на часах, где экран выдачи DND-прав отсутствует.

### Свои кнопки (HTTP-запросы)
- Кнопки, отправляющие **GET** или **POST** запросы на любой URL / IP-адрес
  (Home Assistant, Node-RED, свой сервер, роутер и т.д.)
- **Два действия на кнопку**: одно нажатие — первое действие, быстрое двойное — второе
  (пример: один клик включает свет, двойной — выключает)
- Долгое нажатие по кнопке — редактирование (название, URL, метод, тело, цвет) или удаление
- Уведомление показывает результат выполнения; опционально — текст ответа сервера (настраивается)

### Интерфейс
- Раскладка кнопок: **1, 2 или 3 в ряд**, либо **одна по центру**
- Выбор **цвета** каждой кнопки из палитры
- На часах список листается **безелем/короной** или свайпом
- Свои кнопки и настройки **синхронизируются** с часами через Wearable Data API
- В настройках: скрытие кнопок режимов звука, показ текста ответа сервера, язык интерфейса
- Языки: **English** (по умолчанию), **Русский**, **中文**

## Сборка

### Требования
- JDK 17+
- Android SDK (platform 34, build-tools 34)

### Быстрый способ
```bash
./build.sh            # debug APK (телефон + часы)
./build.sh release    # подписанные release APK
./build.sh install    # сборка + установка на подключённые adb-устройства
./build.sh clean      # очистка
```
Скрипт сам находит JDK и Android SDK (JAVA_HOME / ANDROID_HOME / стандартные пути).

### Подпись release
Release-сборка подписывается автоматически, если в корне есть `keystore.properties`:
```properties
storeFile=keystore/watchbuttons.keystore
storePassword=<пароль>
keyAlias=watchbuttons
keyPassword=<пароль>
```
Файлы `keystore/` и `keystore.properties` в `.gitignore` — не потеряй их:
keystore нужен для обновления приложения без удаления старой версии.
Создать свой keystore:
```bash
keytool -genkeypair -v -keystore keystore/watchbuttons.keystore -alias watchbuttons \
  -keyalg RSA -keysize 2048 -validity 10000
```

### Вручную
```bash
./gradlew assembleDebug
# APK:
#   app/build/outputs/apk/debug/watchButtons-debug.apk
#   wear/build/outputs/apk/debug/watchButtons-wear-debug.apk
# release:
#   app/build/outputs/apk/release/watchButtons.apk
#   wear/build/outputs/apk/release/watchButtons-wear.apk
```

### Android Studio
Открой папку проекта как обычный Android-проект — модули `app` и `wear` подтянутся сами.
Важно: оба модуля используют **один applicationId** (`com.watchbuttons.app`) — это нужно,
чтобы Wearable API доставлял сообщения и данные между устройствами.

## Установка

```bash
adb install app/build/outputs/apk/debug/watchButtons-debug.apk       # телефон
adb install wear/build/outputs/apk/debug/watchButtons-wear-debug.apk # часы
```
При первом запуске выдай доступ к «Не беспокоить» на телефоне (баннер в приложении). На часах это опционально — режимы работают там через громкость.

## Использование

1. Открой приложение на телефоне, нажми **«+»** и создай кнопку, например:
   - Название: `Свет на кухне`
   - Одно нажатие: `POST http://192.168.1.10/api/light` + тело `{"state": "on"}`
   - Двойное нажатие (опционально): `POST http://192.168.1.10/api/light` + `{"state": "off"}`
2. Выбери цвет и раскладку (1/2/3 в ряд, по центру)
3. Кнопка появится на часах автоматически
4. Нажатие отправляет запрос; результат показывается в уведомлении (HTTP-код или ошибка)

## Структура проекта

См. английскую версию выше (одна и та же структура).

## Добавление нового языка

1. Скопируй `app/src/main/res/values/strings.xml` в `values-<код>/`
   (например, `values-de/` для немецкого) и переведи строки
2. То же для `wear/src/main/res/values/strings.xml`
3. Добавь язык в `Language.options` в `app/.../Language.kt` и `wear/.../Language.kt`

## Технические детали

- **Минимальные версии**: Android 8.0 (API 26) — телефон, Wear OS 3 (API 30) — часы
- **UI**: Jetpack Compose (Material 3 на телефоне, Wear Compose на часах)
- **Связь телефон ↔ часы**: Wearable Data API / Message API (play-services-wearable)
- **Хранение**: локально, SharedPreferences + JSON (без сервера)
- **Разрешения**: `INTERNET`, `ACCESS_NOTIFICATION_POLICY` (опционально, режимы работают и без него)

## Идеи на будущее

- Свои HTTP-заголовки и базовая авторизация для запросов
- Виджеты и Tiles на часах
- Группировка кнопок по страницам