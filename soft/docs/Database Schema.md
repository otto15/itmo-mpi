# Схема базы данных Drakkar ERP Soft

База данных работает на PostgreSQL 16. Flyway создаёт схему автоматически при запуске приложения. Исходной версией схемы считается SQL-миграция [`V1__architecture_slice.sql`](../backend/src/main/resources/db/migration/V1__architecture_slice.sql).

## ER-схема

```mermaid
erDiagram
    APP_USER ||--o| USER_ACCOUNT : "имеет учётную запись"
    APP_USER ||--o{ USER_SESSION : "открывает сессии"
    APP_USER ||--o{ CREW_ASSIGNMENT : "участвует"
    EXPEDITION ||--o{ CREW_ASSIGNMENT : "включает команду"
    EXPEDITION ||--o{ WERGILD_ALLOCATION : "создаёт выплаты"
    SHIP ||--o{ SHIP_STAGE_REQUIREMENT : "требует ресурсы"
    WAREHOUSE_STOCK ||..o{ SHIP_STAGE_REQUIREMENT : "связан по коду ресурса"

    APP_USER {
        uuid id PK
        varchar display_name
        varchar system_role
    }

    USER_ACCOUNT {
        uuid user_id PK, FK
        varchar username UK
        bytea password_salt
        bytea password_hash
        boolean enabled
    }

    USER_SESSION {
        char token_hash PK
        uuid user_id FK
        timestamptz created_at
        timestamptz expires_at
        timestamptz revoked_at
    }

    EXPEDITION {
        uuid id PK
        varchar name
        varchar target
        varchar status
        date planned_departure
        varchar ship_name
        integer version
        timestamptz finalized_at
        integer loot_gold
        integer loot_provisions
        integer loot_thralls
    }

    CREW_ASSIGNMENT {
        uuid id PK
        uuid expedition_id FK
        uuid user_id FK
        varchar expedition_role
        varchar participation_status
        boolean alive
        integer version
    }

    WAREHOUSE_STOCK {
        varchar resource PK
        integer quantity
        integer version
    }

    SHIP {
        uuid id PK
        varchar name
        integer stage
        boolean blessed
        integer version
    }

    SHIP_STAGE_REQUIREMENT {
        uuid ship_id PK, FK
        integer stage PK
        varchar resource PK
        integer quantity
    }

    WERGILD_ALLOCATION {
        bigint id PK
        uuid expedition_id FK
        varchar recipient
        varchar category
        integer gold
        integer provisions
        integer thralls
    }

    AUDIT_EVENT {
        bigint id PK
        timestamptz happened_at
        varchar actor_role
        varchar event_type
        varchar aggregate_type
        uuid aggregate_id
        jsonb details
    }
```

## Как данные сгруппированы

| Область | Таблицы | Назначение |
|---|---|---|
| Пользователи и авторизация | `app_user`, `user_account`, `user_session` | пользователь, его роль, данные входа и активные сессии |
| Походы | `expedition`, `crew_assignment`, `wergild_allocation` | план похода, состав команды, добыча и распределение Вергельда |
| Верфь | `ship`, `ship_stage_requirement`, `warehouse_stock` | состояние корабля, требования этапов и остатки ресурсов |
| История | `audit_event` | журнал ключевых действий |

Служебную таблицу `flyway_schema_history` создаёт Flyway: в ней хранится список уже применённых миграций.

`audit_event` намеренно не имеет внешнего ключа на конкретную бизнес-таблицу: поля `aggregate_type` и `aggregate_id` позволяют хранить историю для разных типов объектов даже после изменения их структуры.

Связь между `ship_stage_requirement.resource` и `warehouse_stock.resource` логическая. При завершении этапа сервис блокирует нужные строки склада, проверяет остатки и в одной транзакции списывает ресурсы и меняет этап корабля.

Поля `expedition.ship_name` и `wergild_allocation.recipient` хранят снимок текста на момент операции. Это позволяет истории похода не зависеть от последующего переименования корабля или пользователя.

После утверждения итогов похода триггер `expedition_results_immutable` запрещает изменять статус, добычу и дату фиксации даже прямым SQL-запросом.

## Как посмотреть работающую базу

После запуска проекта список таблиц можно получить так:

```bash
cd soft
docker compose exec postgres psql -U drakkar -d drakkar -c '\dt'
```

Описание конкретной таблицы:

```bash
docker compose exec postgres psql -U drakkar -d drakkar -c '\d expedition'
```

В проекте нет отдельной административной панели для БД: ER-схема предназначена для чтения, а SQL-миграция остаётся точным исполняемым описанием.
