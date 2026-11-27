# Публикация signed APK

Release workflow запускается только после push аннотированного тега `v*`.
Он запускает unit-тесты, собирает signed APK и создаёт GitHub Release с этим
APK. Workflow не следует запускать, пока не настроены секреты ниже.

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

1. Убедитесь, что `versionName` в `app/build.gradle.kts` соответствует версии
   будущего тега.
2. Слейте PR с release-настройками в `main` и дождитесь успешного CI.
3. Создайте и отправьте тег от актуального `main`:

   ```bash
   git checkout main
   git pull --ff-only origin main
   git tag -a v1.0.0 -m "Meme Organizer v1.0.0"
   git push origin v1.0.0
   ```

4. Дождитесь workflow `Release Android APK`. Он создаст GitHub Release и
   приложит `MemeOrganizer.apk`.
