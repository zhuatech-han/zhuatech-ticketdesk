[中文](README.md) | [English](README.en.md)

# TicketDesk · Event ticketing, seat reservations and online QR check-in

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Website](https://www.zhuatech.cn/)

For a 200-seat talk: attendees choose a seat, organizers manage ticket prices and staff access, and authorized operators check tickets online. TicketDesk connects **event/seat configuration → temporary hold → confirmed issuance → online admission → voiding, refunds and seat release** in one persistent workflow. Intended for small talks, performances, exhibitions, sporting events and implementation teams; not a large-scale ticket rush or multi-organizer collection marketplace.

**Public source, non-commercial learning edition for personal study and exchange.** Commercial use requires written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. under [LICENSE](LICENSE). Third-party licenses remain independent. No customer, revenue, production-operation or security-certification claims.

## Entrances and implemented behavior

|Entrance|Implemented|
|---|---|
|Attendee|Registration/sign-in, public events, ticket types and regular-grid seats, one-seat holds, order recovery, personal wallet, unique QR codes, browser print/save-PDF and personal refund requests|
|Organizer|Draft/publish/close events, ticket/seat CRUD, organization/event staff scopes, orders, refund/notification queues, reports/CSV separated by mode and currency, audit|
|Check-in|Authorized events, camera interface, JPEG/PNG image decoding and manual input, admitted/already-used/void/wrong-event/invalid decisions, online duplicate prevention|
|Identity/settings|BCrypt12, sessions/CSRF, live account/password/role/organization checks, disable/reset accounts, roles, permissions, menus, organizations/IANA zones, categories/parameters and last-admin protection|

Each order holds one seat for35 minutes and freezes server prices; replay restores the same order. One winner for the final seat and one successful admission per ticket. History locks pricing/layout, currency and time rules. Free tickets issue after confirmation; DEMO simulates funds explicitly. STRIPE implements REST Checkout and signed raw-body webhooks, matching session, amount, currency and live/test mode and deduplicating actual payment identity. A browser return does not prove payment. Late/extra funds create separate refund tasks.

Refund requests for unused tickets before the event deadline void immediately. Only confirmed full refunds release seats; unknown outcomes reuse the same idempotency key, while provider-confirmed failures require review before a new request. Email queues send a personal order and authenticated wallet link, without public ticket codes or PDF attachments; unconfigured, pending, failed and sent states remain distinct. No marketing email.

Chinese/English UI and editable business names, responsive layouts, search, state filters, pagination, sorting, save feedback and unsaved-change confirmation. Organizer order views contain no ticket codes or password hashes. [Business rules](docs/BUSINESS.md), [operations](docs/OPERATIONS.md) and [API](docs/API.md) provide details.

**Limits:** one seat per order; no multi-seat cart, free-form venue editor, native apps, offline multi-device redemption, marketplace settlements, WeChat/Alipay, tax invoices, email verification or self-service password recovery. Camera/payment/SMTP interfaces exist, but **physical-phone camera operation, actual Stripe merchant payments/refunds and real SMTP delivery are not externally verified**. Local contract tests are not live-provider acceptance. Ticket payloads are read by this system, not ordinary webpage links. PDF uses browser printing; printers/gates and production capacity are unverified.

## Actual running screens

Current running pages use explicitly marked QA fixtures, not customer cases or fabricated mockups. Wallet screenshots use privacy view to hide QR codes, manual ticket codes and order references; actual ticket generation and online redemption remain available.

![Sign-in and registration](docs/screenshots/login.jpg)
![Attendee events: venue, date and price](docs/screenshots/events.jpg)
![Seat map: real availability and reservation](docs/screenshots/seats.jpg)
![Personal wallet: privacy view with ticket codes and order references hidden](docs/screenshots/wallet.jpg)
![Organizer: events, types and staff](docs/screenshots/organizer.jpg)
![Online check-in and rejection](docs/screenshots/checkin.jpg)
![Mode/currency reports](docs/screenshots/reports.jpg)
![Accounts](docs/screenshots/accounts.jpg)
![Roles and permissions](docs/screenshots/roles.jpg)
![Organization and settings](docs/screenshots/settings.jpg)
![Mobile wallet](docs/screenshots/mobile.jpg)
![English interface](docs/screenshots/english.jpg)

## Architecture and requirements

Java21, Spring Boot4.0.7, Security/JPA/Flyway, MySQL8.4 via MariaDB Connector/J3.5.10; Vue3.5.43, Vite8.1.5, qrcode1.5.4, ZXing Browser0.1.5 and same-origin Nginx. Node≥24.19, Maven3.9, Python3.10+, Docker/Compose v2; at least 4GB available memory recommended. Docker startup does not require host Java/Node.

```text
backend/              API, live permissions, ticket transactions and queues
  src/main/resources/ configuration and Flyway V1/V2/V3
frontend/             attendee, organizer and check-in UI
  public/brand/       official brand assets
scripts/              init, HTTP QA, persistence, backup and restore
compose.yaml          persistent MySQL, backend and Nginx
compose.test.yaml     standalone disposable tests; never merge with production
compose.dev.yaml      localhost-only development database port
docs/                 rules, API, operations, deployment, tests, security, screens
.github/workflows/    continuous validation
```

Browser→Nginx/api→live authorization and transactions→MySQL. Flyway and JPA schema validation remain enabled; the frontend is not a security boundary. Queries have a 10000-record per entity limit. Small workflow validation, not a high-capacity promise.

## Install and initialize

```sh
git clone https://github.com/zhuatech-han/zhuatech-ticketdesk.git
cd zhuatech-ticketdesk
python3 scripts/init-env.py
docker compose -p ticketdesk config --quiet
docker compose -p ticketdesk up --build -d --wait --wait-timeout 1800
```

Open [localhost UI](http://127.0.0.1:8160/) and [health](http://127.0.0.1:8160/actuator/health). Initial username`admin`; the unique password is **ADMIN_PASSWORD in private.env**, with no shared demo password. Init uses0600 and refuses overwrite. Restart does not reset existing passwords. A phone’s localhost points to the phone, so use a reachable HTTPS domain.

Empty databases run [V1 identity](backend/src/main/resources/db/migration/V1__identity.sql), [V2 tickets](backend/src/main/resources/db/migration/V2__ticket_core.sql), [V3 events/queues](backend/src/main/resources/db/migration/V3__events_integrations.sql). Bootstrap creates only the administrator, roles, permissions, menus, categories and parameters; no fabricated events/customers. Configure organization and staff, draft an event, add types/seats, then publish.

|Configuration|Purpose|
|---|---|
|MYSQL_ROOT_PASSWORD / DATABASE_PASSWORD|Required independent strong database passwords|
|ADMIN_USERNAME / ADMIN_PASSWORD|Initial admin only;12–72 bytes, upper/lowercase and digit|
|TICKET_SIGNING_KEY|Stable256-bit-or-stronger signing key, privately retained|
|WEB_PORT / BIND_ADDRESS|Default8160 /127.0.0.1, overridable|
|PUBLIC_URL / COOKIE_SECURE|Reachable URL; Secure cookies behind HTTPS; update URL with port|
|PAYMENT_MODE|DEMO or STRIPE; zero-price tickets are FREE; no real funds in DEMO|
|STRIPE_SECRET_KEY / STRIPE_WEBHOOK_SECRET|Eligible merchant/server and webhook credentials; missing required real-mode configuration fails startup|
|MAIL_ENABLED / MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD / MAIL_FROM|SMTP, disabled by default; TLS and bounded timeouts|
|SCHEDULER_ENABLED|Default true; false for isolated restore to prevent external actions|
|DATABASE_URL / DATABASE_USER|Optional external MySQL, verify-full and trusted CA|
|MYSQL_DEV_PORT|Local compose.dev.yaml only, default33160|

.env.example provides names and guidance; actual secrets are not committed. Mainland China is absent from [Stripe’s merchant availability list](https://stripe.com/global); do not assume a Shanghai entity can directly activate Stripe. [Deployment/upgrade/backup/restore](docs/DEPLOYMENT.md): append migrations rather than editing applied SQL, keep backups private and validate isolated restoration before external actions.

## Tests, deployment and safety

```sh
mvn -B -f backend/pom.xml spotless:check test
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
# Disposable non-production database only
docker compose -p ticketdesk-tests -f compose.test.yaml run --rm backend-tests
docker compose -p ticketdesk-tests -f compose.test.yaml down -v
# Isolated complete DEMO environment; creates QA fixtures
python3 scripts/quality.py --base http://127.0.0.1:8160
python3 scripts/verify-persistence.py --state output/ticketdesk-quality-state.json
python3 scripts/release-check.py
git diff --check
```

38 backend and 17 frontend tests; real MySQL and complete DEMO HTTP workflows supplement H2. Image builds execute tests without skipping. Validate empty-schema startup, health, login, 200-seat workflow, personal scopes, frozen amount, duplicate issuance/admission, refund/notification queues, reports and persistence. Acceptance records describe actual results; physical devices, production load and real third-party environments require separate validation.

For502 wait for backend health; for empty events check publication/staff assignment; for sign-in check initial private credentials. Unknown writes require refreshing and verifying before retry. Unconfigured SMTP is not sent mail. Resolve official package-source download failures without skipping tests. Deployers maintain HTTPS, throttling, backup, monitoring, privacy notice and go-live review. [Security](docs/SECURITY.md), [testing](docs/TESTING.md), [API](docs/API.md), [third-party declarations](docs/THIRD_PARTY.md).

Contributions need sanitized reproduction and tests under[AGENTS.md](AGENTS.md). Use Issues for ordinary problems; report vulnerabilities privately through the website or contacts, without exposing business data or credentials. Source is provided as-is; deployment and operating risks remain with the operator.

## Licensing and contact

Personal learning, research and non-commercial exchange only. Commercial projects, paid deployment, SaaS, source resale and customer delivery need written company authorization under[LICENSE](LICENSE). The README and LICENSE agree; third-party rights remain independent.

ZhiHua Technology provides commercial source licensing, custom development, private deployment, implementation and system integration. [Website](https://www.zhuatech.cn/); [han@zhuatech.cn](mailto:han@zhuatech.cn), [jack@zhuatech.cn](mailto:jack@zhuatech.cn), WhatsApp [+86 17521234993](https://wa.me/8617521234993).
