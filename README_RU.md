<p align="center">
  <b>Русский</b> · <a href="README.md">English</a>
</p>

# Mogged для NeoForge

Порт [Mogged](https://github.com/Cranch-fur/Mogged) на NeoForge. Возможности те же: переключайтесь между скином своего аккаунта и двумя своими пресетами одной клавишей, с плавной анимацией перехода. HD-скины, плащи и элитры, встроенный редактор скинов — и другие игроки с модом тоже видят ваш образ.

Единственное отличие от Fabric-версии: нет поддержки [Flashback](https://modrinth.com/mod/flashback). Этот реплей-мод
существует только для Fabric.

## Требования

| Minecraft | NeoForge | Java |
|---|---|---|
| 1.20.1 | 47.1+ | 17 |
| 1.21.1 | 21.1+ | 21 |
| 26.1.x | 26.1+ | 25 |
| 26.2 | 26.2+ | 25 |
| 26.3 | 26.3+ | 25 |

Для каждой версии нужен свой jar. Чтобы другие игроки видели ваши пресеты, положите тот же jar на сервер. Ванильный
сервер тоже подойдёт, но тогда пресеты видите только вы. Игроки с Fabric-, Forge- и NeoForge-версиями друг друга не
видят: сетевые каналы у загрузчиков разные.

## Сборка

Для запуска Gradle хватит любого JDK 17+. Нужный версии JDK (17, 21 или 25) скачается сам.

```sh
./gradlew build            # Minecraft 26.2 (по умолчанию)
./gradlew build -Pmc=26.1
./gradlew build -Pmc=26.3
./gradlew build -Pmc=1.21.1
./gradlew build -Pmc=1.20.1
```

На Windows используйте `gradlew.bat`. Jar появится в `build/libs/`, например `mogged-1.1.0+mc26.2-neoforge.jar`.
Для 1.20.1 в `build/devlibs/` будет ещё jar с именами Mojang: он нужен только для разработки, в игру кладите jar из
`build/libs/`.

Запуск игры из среды разработки: `./gradlew runClient -Pmc=<версия>`.

## Устройство проекта

```
src/main/java/mogged          общий код: протокол и серверная пересылка
src/client/java/mogged/client общий клиентский код (как у Fabric-версии)
  ├─ gui                      экраны, виджеты и редактор скинов
  ├─ render                   CPU-рендер модели для превью
  └─ compat                   заглушка Flashback (его нет на NeoForge)
src/mc26, src/mc1211, src/mc1201
                              код под версию: классы "platform" (сеть, отрисовка, ввод, текстуры), миксины
                              в поиск скина игрока и связка с событиями NeoForge (mogged/neoforge)
versions/                     настройки сборки под каждую версию Minecraft; "family" выбирает папку выше
```

От Fabric-версии отличаются только связка с загрузчиком и сеть: `mogged/neoforge` (точки входа `@Mod` и события
NeoForge), `platform/ModNetworking` и `client/platform/ClientNetworking` (пакеты NeoForge; на 1.20.1, где NeoForge — ещё
Forge 47, каналы Forge), `MoggedClient` (без API Fabric) и `SkinServer` (события вынесены наружу). Весь остальной код
(скины, анимация, экраны, редактор) скопирован без изменений.

Настройки хранятся в `config/mogged.json`, в том же формате, что у Fabric-версии.

## Авторы

@cranch_fur и @murkatel.

## Лицензия

Copyright (C) 2026 @cranch_fur, @murkatel

Mogged распространяется по лицензии [GNU General Public License v3.0](LICENSE).
