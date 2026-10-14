# Watch Buttons

Приложение для телефона (Android) и смарт-часов (Wear OS), позволяющее одной кнопкой управлять режимами звука и отправлять HTTP-запросы на любые URL-адреса.

## Возможности

### Режимы (телефон + часы)
| Кнопка | Действие |
|---|---|
| **Не беспокоить** | Полная тишина: звук и вибрация выключены на телефоне и на часах |
| **Только вибрация** | Звук звонка выключен, вибрация остаётся |
| **Обычный режим** | Обычные уведомления со звуком |

Нажатие режима в любом приложении (телефон или часы) применяет его сразу на **обоих** устройствах.

### Свои кнопки (HTTP-запросы)
- Создание кнопок, отправляющих **GET** или **POST** запросы на любой URL / IP-адрес
  (например, Home Assistant, Node-RED, свой сервер, роутер)
- **Два действия на кнопку**: одно нажатие — первое действие, быстрое двойное — второе
  (пример: один клик — свет включить, двойной — выключить)
- Долгое нажатие по кнопке — редактирование (название, URL, метод, тело, цвет) или удаление

### Интерфейс
- Раскладка кнопок: **1, 2 или 3 в ряд**, либо **одна по центру** — настраивается
- Выбор **цвета** каждой кнопки из палитры
- На часах список листается **безелем/короной** или свайпом
- Свои кнопки автоматически **синхронизируются** с часами через Wearable API
- Язык интерфейса: **English** (по умолчанию), **Русский**, **中文**

## Сборка

### Требования
- JDK 17+
- Android SDK (platform 34, build-tools 34)

### Быстрый способ
```bash
./build.sh            # debug APK (телефон + часы)
./build.sh release    # release APK (подписанный)
./build.sh install    # сборка + установка на подключённые adb-устройства
./build.sh clean      # очистка
```
Скрипт сам находит JDK и Android SDK (JAVA_HOME / ANDROID_HOME / стандартные пути).

### Подпись release
Release-сборка автоматически подписывается, если в корне есть `keystore.properties`:
```properties
storeFile=keystore/watchbuttons.keystore
storePassword=<пароль>
keyAlias=watchbuttons
keyPassword=<пароль>
```
Файлы `keystore/` и `keystore.properties` добавлены в `.gitignore` — не потеряй их:
keystore нужен для обновления приложения без удаления старой версии. Свой keystore можно создать так:
```bash
keytool -genkeypair -v -keystore keystore/watchbuttons.keystore -alias watchbuttons \
  -keyalg RSA -keysize 2048 -validity 10000
```

### Вручную
```bash
gradle assembleDebug
# APK:
#   app/build/outputs/apk/debug/watchButtons-debug.apk
#   wear/build/outputs/apk/debug/watchButtons-wear-debug.apk
# release:
#   app/build/outputs/apk/release/watchButtons.apk
#   wear/build/outputs/apk/release/watchButtons-wear.apk
```

### Android Studio
Открыть папку проекта как обычный Android-проект — студия сама подтянет модули `app` и `wear`.

## Установка

1. Установить `app-debug.apk` на телефон:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
2. Установить `wear-debug.apk` на часы:
   ```bash
   adb install wear/build/outputs/apk/debug/wear-debug.apk
   ```
3. При первом запуске разрешить доступ к режиму «Не беспокоить»
   (кнопка «Разрешить доступ» появится в приложении — на телефоне и на часах)

## Использование

1. Откройте приложение, нажмите **«+»** и создайте кнопку, например:
   - Название: `Свет на кухне`
   - Одно нажатие: `POST http://192.168.1.10/api/light` + тело `{"state": "on"}`
   - Двойное нажатие (опционально): `POST http://192.168.1.10/api/light` + `{"state": "off"}`
2. Выберите цвет кнопки и раскладку (1/2/3 в ряд, по центру)
3. Кнопка появится на часах автоматически
4. Нажатие выполняет запрос, результат показывается в уведомлении (HTTP-код или ошибка)

## Структура проекта

```
watchButtons/
├── app/                        # приложение для телефона
│   └── src/main/java/com/watchbuttons/app/
│       ├── MainActivity.kt     # экран (Compose): кнопки, диалоги, язык, раскладка
│       ├── ButtonData.kt      # модель кнопки и действий (ButtonAction)
│       ├── ButtonStore.kt      # хранение в SharedPreferences + синхронизация с часами
│       ├── SoundController.kt  # управление звуком/DND
│       ├── HttpExecutor.kt     # выполнение GET/POST
│       ├── Language.kt         # выбор языка интерфейса
│       ├── Sync.kt             # отправка команд на часы
│       └── PhoneMessageService.kt  # приём команд с часов
├── wear/                       # приложение для часов (Wear OS)
│   └── src/main/java/com/watchbuttons/wear/
│       ├── MainActivity.kt     # экран часов, прокрутка безелем
│       ├── WearButtonStore.kt  # чтение кнопок из Data API
│       └── ...                 # аналогичные контроллеры
├── build.sh                    # скрипт сборки
└── build.gradle.kts, settings.gradle.kts
```

## Добавление нового языка

1. Скопируйте `app/src/main/res/values/strings.xml` в `values-<код>/`
   (например, `values-de/` для немецкого) и переведите строки
2. То же для `wear/src/main/res/values/strings.xml`
3. Добавьте язык в список `Language.options` в
   `app/.../Language.kt` и `wear/.../Language.kt`:
   ```kotlin
   val options = listOf(
       "en" to "English",
       "ru" to "Русский",
       "zh" to "中文",
       "de" to "Deutsch"
   )
   ```

## Технические детали

- **Минимальные версии**: Android 8.0 (API 26) — телефон, Wear OS 3 (API 30) — часы
- **UI**: Jetpack Compose (Material 3 на телефоне, Wear Compose на часах)
- **Связь телефон ↔ часы**: Google Wearable Data API / Message API (play-services-wearable)
- **Хранение**: локально, SharedPreferences + JSON (без сервера)
- **Разрешения**: `INTERNET`, `ACCESS_NOTIFICATION_POLICY` (для режимов звука)

## Дальнейшие идеи

- Подписанные release-сборки (keystore)
- HTTP-заголовки и базовая авторизация для запросов
- Виджеты и Tiles на часах
- Группировка кнопок по страницам