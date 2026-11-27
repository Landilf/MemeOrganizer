# Публикация signed APK

Release workflow создаёт Git-тег, запускает unit-тесты, собирает signed APK и
публикует GitHub Release с этим APK. Workflow не следует запускать, пока не
настроены секреты ниже.

## Создание ключа

Создайте один keystore локально и храните его вне репозитория и резервной копии
исходного кода:

```bash
keytool -genkeypair -v \
  -keystore meme-organizer-release.jks \
  -alias meme-organizer \
  -keyalg RSA -keysize 4096 -validity 10000
```

Не теряйте файл keystore, alias и оба пароля. Без них нельзя будет выпускать
обновления приложения с тем же идентификатором Android.

Для локальной release-сборки скопируйте `keystore.properties.example` в
`keystore.properties`, подставьте абсолютный путь к файлу и свои значения.
Этот файл и расширения keystore исключены через `.gitignore`.

## GitHub Secrets

В GitHub: `Settings` → `Secrets and variables` → `Actions` создайте secrets:

| Secret | Значение |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | keystore, закодированный одной строкой Base64 |
| `RELEASE_STORE_PASSWORD` | пароль keystore |
| `RELEASE_KEY_ALIAS` | alias ключа, например `meme-organizer` |
| `RELEASE_KEY_PASSWORD` | пароль ключа |

Для Linux значение первого секрета можно получить так:

```bash
base64 -w 0 meme-organizer-release.jks
```

Скопируйте весь вывод без пробелов и переносов строк в значение секрета.

## Выпуск версии

1. Слейте нужные PR в `main` и дождитесь успешного CI.
2. Откройте в GitHub `Actions` → `Release Android APK` → `Run workflow`.
3. Выберите ветку `main` и тип изменения версии:

   - `patch`: исправления, например `v1.0.0` → `v1.0.1`;
   - `minor`: новая обратносовместимая функциональность, например `v1.0.0` →
     `v1.1.0`;
   - `major`: несовместимые изменения, например `v1.0.0` → `v2.0.0`.

4. Запустите workflow. Он проверит unit-тесты, соберёт signed APK, а затем
   создаст аннотированный тег на актуальном `main` и GitHub Release с
   `MemeOrganizer.apk`. Если тесты или сборка не пройдут, номер версии не будет
   занят тегом.

`versionName` и `versionCode` в release APK рассчитываются по тегу. Для
`v1.2.3` это соответственно `1.2.3` и `1002003`; вручную менять
`app/build.gradle.kts` перед релизом не нужно.

Push существующего тега формата `v<major>.<minor>.<patch>` также остаётся
поддержанным и запускает ту же signed-сборку.
