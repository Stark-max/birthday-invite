# Birthday Invite App

## Русский

Персональное веб-приложение для приглашений на день рождения. Именинник создаёт событие, добавляет гостей, копирует персональные ссылки, а гости отвечают RSVP. Принявшие видят место, wish list, список гостей и подключённые активности.

### Возможности

- Роли `SUPER_ADMIN` и `ADMIN` с отдельными учётными записями.
- Супер-админ создаёт и отключает администраторов, сбрасывает пароли и открывает любое приглашение.
- Каждый обычный администратор управляет только своим событием, гостями, wish list, темами и активностями.
- Первичная настройка супер-админа и первого события через `/setup`.
- Админка с гостями, статистикой, wish list и настройками.
- Публичная страница отдельного события `/e/{publicSlug}`.
- Персональная ссылка `/invite/{code}` для каждого гостя.
- RSVP-ответы: придёт, не сможет, пожелание.
- Система тем: глобальная тема, персональная тема гостя, кастомные пресеты.
- Активности: викторина, колесо фортуны, правда или действие, мем-челлендж.
- Общий лидерборд по очкам активностей.

### Технологии

Java 17, Spring Boot 3, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Flyway, Thymeleaf, HTML/CSS/JavaScript, Docker Compose.

### Запуск

```powershell
docker compose up --build -d
```

После запуска открой:

- приложение: http://localhost:8080
- PostgreSQL на хосте: `localhost:5433`
- база: `birthday_db`
- пользователь: `birthday`
- пароль: `birthday123`

Для остановки и повторного запуска:

```powershell
docker compose stop
docker compose start
docker compose restart app
```

### Администраторы

- Вход для обеих ролей: http://localhost:8080/admin/login
- Центр супер-админа: http://localhost:8080/super-admin
- После миграции существующего проекта логин владельца: `superadmin`, пароль остаётся прежним.
- Новый администратор входит с временным паролем, обязательно меняет его и создаёт своё приглашение.
- Саморегистрации нет: учётные записи создаёт только супер-админ.

### Локальная разработка

В проекте есть `pom.xml` по ТЗ и дополнительный `build.gradle` для локальной проверки там, где установлен Gradle:

```powershell
gradle test --no-daemon
```

### Система тем

Темы хранятся в PostgreSQL JSONB. Глобальная тема события находится в `events.global_theme`, персональная тема гостя в `guests.personal_theme`, пресеты в `theme_presets.theme_data`. На страницу тема попадает через Thymeleaf-фрагмент `fragments/theme-variables.html`, который задаёт CSS-переменные.

Приоритет темы:

1. персональная тема гостя;
2. глобальная тема события;
3. встроенный пресет `elegant-gold`.

### Активности

Активности реализованы как модули `ActivityModule`. Каждый модуль предоставляет slug, название, описание, иконку, дефолтную конфигурацию, валидацию и обработку действия. `ActivityRegistry` автоматически получает все Spring-компоненты модулей.

Чтобы добавить новую активность:

1. создать класс в `src/main/java/kg/birthday/invite/activity/modules`;
2. реализовать `ActivityModule`;
3. добавить `@Component`;
4. добавить гостевой и админский Thymeleaf-фрагменты при необходимости.

### API и страницы

- `GET /setup`, `POST /setup`
- `GET /`, `GET /e/{publicSlug}`, `GET /invite/{code}`, `POST /invite/{code}/respond`
- `GET /admin/login`, `POST /admin/login`, `GET /admin`, `GET /admin/logout`
- `GET|POST /admin/password/change`, `GET|POST /admin/event/new`
- `GET /super-admin`, `GET /super-admin/admins`, `GET /super-admin/events`
- `POST /super-admin/admins`, `POST /super-admin/admins/{id}/enabled`
- `POST /super-admin/admins/{id}/password`, `POST /super-admin/admins/{id}/reset-password`, `POST /super-admin/events/{id}/open`
- `POST /admin/guests`, `POST /admin/guests/{id}/delete`
- `GET /admin/wishlist`, `POST /admin/wishlist`, `POST /admin/settings/toggle-guestlist`
- `GET /admin/themes`, `POST /admin/themes/global`, `POST /admin/themes/effect`
- `POST /admin/themes/guest/{guestId}`, `POST /admin/themes/guest/{guestId}/reset`
- `POST /admin/themes/presets`, `DELETE /admin/themes/presets/{id}`
- `GET /admin/activities`, `POST /admin/activities/enable`
- `POST /admin/activities/{instanceId}/disable`
- `POST /admin/activities/{instanceId}/config`
- `POST /activities/{instanceId}/play`
- `GET /activities/{instanceId}/leaderboard`
- `POST /wishlist/{itemId}/toggle`

## English

Birthday Invite App is a personal birthday invitation web app. The host creates an event, adds guests, shares unique invite links, and guests respond through RSVP pages. Accepted guests can see the location, wish list, guest list, activities, and leaderboard.

### Features

- Super admin and admin roles with isolated event data.
- First-run super-admin and event setup at `/setup`.
- Admin dashboard for guests, stats, wish list, settings, themes, and activities.
- Unique `/invite/{code}` link per guest.
- RSVP statuses: accepted, declined, pending.
- Theme system with built-in presets, global event theme, personal guest theme, and custom presets.
- Modular activities: quiz, wheel of fortune, truth or dare, and meme challenge with unique meme cards per guest.
- Event-wide leaderboard.

### Stack

Java 17, Spring Boot 3, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Flyway, Thymeleaf, HTML/CSS/JavaScript, Docker Compose.

### Run

```bash
docker-compose up --build
```

Open http://localhost:8080 after the containers are ready.

### Screenshots

Screenshots are intentionally left as placeholders until the app is run with real event data.

- Setup page
- Admin dashboard
- Guest invite
- Theme manager
- Activity manager
