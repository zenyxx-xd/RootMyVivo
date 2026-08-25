# RootMyVivo 🛡️

> One-click root для vivo/iQOO устройств с залоченным загрузчиком.
> Основан на CVE-2026-43499 (GhostLock) + KernelSU.

## Как работает

1. Определяет модель и версию ядра автоматически
2. Скачивает подходящий payload из [RootMyVivo-Payloads](https://github.com/zenyxx-xd/RootMyVivo-Payloads)
3. Запускает эксплойт (LD_PRELOAD / helper binary)
4. Получает root → загружает KernelSU через ksud late-load
5. Настраивает персистентность через service.d

## Поддерживаемые устройства

| Модель | SoC | Ядро | Статус |
|--------|-----|------|--------|
| iQOO Neo 11 (PD2520) | SM8750 | 6.6.89-android15-8 | ✅ протестировано |

⚠️ Требуется ядро < 6.6.140 (CVE-2026-43499 не закрыт)

## Установка

1. Скачай APK из [Releases](https://github.com/zenyxx-xd/RootMyVivo/releases)
2. Включи "Отладку по USB" и "OEM разблокировку"
3. Открой приложение → нажми ROOT

## Сборка из исходников

```sh
./gradlew :app:assembleDebug
```

Требуется JDK 17, Android SDK 35, NDK 28+.

## Дисклеймер

Используй только на устройствах, которыми владеешь. Авторы не несут 
ответственности за повреждение данных. Kernel panic при выполнении — 
это нормально, устройство перезагрузится.
