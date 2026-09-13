<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=b3f79c&height=220&section=header&text=RDang%20V2&fontSize=72&fontAlignY=32&fontColor=1a1a1a&desc=мир%20→%20данжи&descAlignY=52&descSize=22&descColor=2D3E27&animation=fadeIn" alt="RDang V2"/>

<br/>

[![Paper](https://img.shields.io/badge/Paper-1.21+-00A98F?style=for-the-badge&logo=PaperMC&logoColor=white)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-supported-9B59B6?style=for-the-badge)](https://papermc.io/software/folia)
[![Leaf](https://img.shields.io/badge/Leaf-compatible-2ECC71?style=for-the-badge)](https://www.leafmc.one/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![WG + WE](https://img.shields.io/badge/WG%20%2B%20WE-required-E67E22?style=for-the-badge)](https://enginehub.org/)

Вставляет схематики, создаёт регионы WorldGuard, наполняет шалкеры лутом.  
Открытие по **ключу** или **кирке**.

</div>

---

## 🖥️ Поддерживаемые ядра

| Ядро | Статус |
|:----:|:------:|
| **Paper** | ✅ |
| **Folia** | ✅ |
| **Leaf** | ✅ |
| **Purpur / Pufferfish** | ✅ |

---

## ✨ Возможности

| | |
|---|---|
| 🏰 | Спавн данжей из `.schem` по **биому** и **миру** |
| 🗺️ | Бэкап территории и полное восстановление при удалении |
| 🛡️ | Регионы **WorldGuard** с настраиваемыми флагами |
| 📦 | Шалкеры: открытие **ключом** или **киркой** (`open-type`) |
| ⛏️ | Кирка взломщика — прочность ударов по шалкеру |
| 💎 | GUI-редактор лута: страницы, предметы, шансы |
| 🧭 | Компас поиска активного данжа |
| 🗝️ | Шанс ключа / компаса в ванильном луте (структуры, Vault) |
| ⏱️ | Автоудаление по таймеру + опциональный респавн |
| 📊 | Лимит активных данжей (`max-dungeons`) |
| 🗄️ | **SQLite** или **MySQL** |
| ⚡ | Folia-safe scheduler, тонкий jar |

---

## ⌨️ Команды

Право: `rdang.admin` *(по умолчанию — op)*  
Алиасы: `/rd`, `/dang`, `/bdang`, `/adang`, `/edang`, `/holydang`

| Команда | Описание |
|---------|----------|
| `/rdang` | 📖 Справка |
| `/rdang menu` | 🎛️ Главное меню |
| `/rdang spawn [кол-во] [мир]` | 🏰 Заспавнить данж(и) |
| `/rdang undo <id>` | 🗑️ Удалить данж + восстановить территорию |
| `/rdang give key\|compass\|pickaxe [ник] [N]` | 🎁 Выдать ключ, компас или кирку |
| `/rdang admin debug <true\|false>` | 🐛 Вкл/выкл debug |
| `/rdang admin test key\|pickaxe` | 📦 Зарегистрировать шалкер (ключ / кирка) |
| `/rdang admin remove` | 🗑️ Снять данжевый статус с шалкера |
| `/rdang admin loot` | 💎 Засыпать лут пула в шалкер |
| `/rdang reload` | ♻️ Перезагрузить конфиги |
| `/rdang update` | ⬆️ Обновление с GitHub |
