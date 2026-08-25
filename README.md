# Personal Media Tracker — 0.2.0 (Quarkus + Postgres + Qute)

REST API + server-rendered auth pages for tracking movies, TV shows, video games (phase 2). `0.2.0` adds Qute login/register with pure Tailwind CDN (sharp/clean, no gradients) on top of `1.0.0` skeleton.

## Stack (per request)

- **Quarkus 3.38.3** (Java 21, `org.hlopes`), **PostgreSQL 16** via **Docker Compose persistent volume** for `dev` + Dev Services/Testcontainers for `test`, **Flyway** (`V1__create_users.sql`), **Hibernate ORM Panache**, **Lombok 1.18.46**, **MapStruct (CDI)**, **SmallRye JWT + JWT Build**, **Mailer (log fallback)**, **SmallRye OpenAPI/Swagger UI**, **Qute + REST-Qute** (`quarkus-qute`, `quarkus-rest-qute`, Tailwind 4 CDN)
- Project structure: `entity/`, `repository/`, `service/` (`AuthService`, `EmailService`, `JwtService` via `config/ApplicationConfig` `@ConfigMapping`), `resource/` (`AuthResource` JSON+form auth + `PageResource` Qute), `security/JwtCookieFilter` (cookie→Bearer), `dto/` (records), `mapper/`, `config/` (`ApplicationConfig.java:6` typed mapping + `OpenApiConfig`), `exception/`, `templates/` (`base.html`, `index.html`, `app.html`, `auth/login.html`, `auth/register.html`, `auth/verification-sent.html`, `auth/verify-result.html`) (`src/main/java/org/hlopes`)
- Domain: see `CONTEXT.md:1` (`User` identified by email, `Authentication`, `Email Verification`)

## Prerequisites

- **Docker Desktop** running (for Postgres Dev Services + tests). `docker ps` must succeed.
- `publicKey.pem` / `privateKey.pem` already generated in `src/main/resources` (RSA 2048, `mp.jwt.verify.issuer=mediashelf`, 1h expiry). Regenerate with: `java` GenKeys snippet in `src/main/resources`.

## Run in dev (live coding + Swagger + Qute + persistent DB)

```powershell
docker compose up -d postgres   # once — creates mediashelf_postgres_data volume (data survives restarts)
# or inside devcontainer: ./mvnw quarkus:dev  (host.docker.internal:5432 via docker-outside-of-docker)
```

- API: http://localhost:8080
- **Qute pages**: http://localhost:8080/ (index), http://localhost:8080/login, http://localhost:8080/register, http://localhost:8080/verification-sent, http://localhost:8080/verify?token=..., http://localhost:8080/app (protected, needs login)
- Swagger UI: http://localhost:8080/q/swagger-ui (OpenAPI at `/q/openapi`)
- Tailwind: CDN `https://cdn.tailwindcss.com` + Inter/JetBrains Mono, zinc palette, `rounded-sm`/`border`/`shadow-sm` only — no `bg-gradient`/`linear-gradient`, no single-side border, sharp clean per spec (`base.html:1`, `docs/adr/0002-qute-tailwind-cdn.md:1`)
- **Postgres**: `docker-compose.yml:1` (`postgres:16-alpine`, `mediashelf/mediashelf`/`mediashelf`, `5432:5432`, `postgres-data:/var/lib/postgresql/data` + `healthcheck pg_isready`). Dev profile (`%dev` `application.properties:8`) disables DevServices and uses `jdbc:postgresql://host.docker.internal:5432/mediashelf` (works on host via `localhost` and inside devcontainer via `host.docker.internal`). Data persists in `mediashelf_postgres_data` across `quarkus:dev` restarts; `docker compose down` keeps volume, `docker compose down -v` wipes it. Test profile still uses DevServices random port (isolated, `jdbc:postgresql://localhost:61951` in logs).

## Test the auth flow (Swagger or curl or browser)

Quarkus logs the verification link (Mailpit fallback) — copy it even if no SMTP on `localhost:1025`.

**Via browser (Qute, 0.2.0):**
1) `GET /register` → fill form → `POST /register` → `303 /verification-sent?email=...` → check logs for `http://localhost:8080/verify?token=...` (or `http://localhost:8080/api/auth/verify?token=...` both work)
2) `GET /verify?token=...` → `200` HTML `Email verified` (or `GET /api/auth/verify?token=...` JSON)
3) `GET /login` → `POST /login` → sets HttpOnly `jwt` cookie (1h, `SameSite=Lax`) via `JwtCookieFilter.java:1` (cookie → `Authorization: Bearer`) → `303 /app` → `GET /app` shows email + placeholder library
4) `POST /logout` clears cookie

**Via API (still supported):**
1) **Register** `POST /api/auth/register` — `{"email":"you@example.com","password":"password123"}` (min 8) → `201` + logs `http://localhost:8080/api/auth/verify?token=...`
2) **Verify** `GET /api/auth/verify?token=...` → `200 {"verified":true}`
3) **Login** `POST /api/auth/login` → `200 {"accessToken":"...","tokenType":"Bearer","expiresIn":3600}` → `403` before verification.
4) **Authorize in Swagger** top-right `Authorize` → `Bearer <accessToken>` → **GET /api/me** → `200 {"id","email","verified":true}`
5) **Resend** `POST /api/auth/resend-verification` if expired.

All endpoints are documented in Swagger with `bearerAuth` security scheme. Email is normalized to lower-case; `passwordHash` is bcrypt cost 12; token 24h, single-use, idempotent verify.

```powershell
# curl variant
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"email":"you@example.com","password":"password123"}'
# check logs for link, then:
curl "http://localhost:8080/api/auth/verify?token=<uuid>"
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"you@example.com","password":"password123"}'
curl http://localhost:8080/api/me -H "Authorization: Bearer <jwt>"
```

## Config (src/main/resources/application.properties)

- `quarkus.datasource` — `test`: DevServices random port (`quarkus.datasource.devservices.enabled=true`, `postgres:16-alpine`, `mediashelf`), `dev`: compose persistent (`%dev.quarkus.datasource.devservices.enabled=false`, `%dev.quarkus.datasource.jdbc.url=jdbc:postgresql://host.docker.internal:5432/mediashelf`), `prod`: `%prod.quarkus.datasource.jdbc.url` via env `QUARKUS_DATASOURCE_JDBC_URL`, `quarkus.flyway.migrate-at-start=true`
- `mp.jwt.verify.publickey.location=publicKey.pem`, `smallrye.jwt.sign.key.location=privateKey.pem`, `issuer=mediashelf`, `lifespan=3600` (also exposed as `mediashelf.jwt.issuer`/`lifespan` aliases for typed access — see below)
- `quarkus.mailer.from=noreply@mediashelf.local`, `host=localhost:1025`, `mock=false` (dev) / `mock=true` (test), `EmailService` always logs link as fallback.
- `mediashelf.verification.token-expiry-hours=24`, `base-url=http://localhost:8080` — accessed **only** via typed `config/ApplicationConfig.java:6` (`@ConfigMapping(prefix = "mediashelf")` from Quarkus/SmallRye Config). No `@ConfigProperty` remains in `service/` (`AuthService.java:7`, `EmailService.java:3`, `JwtService.java:6` inject `ApplicationConfig`): `verification.tokenExpiryHours()`/`baseUrl()` and `jwt.issuer()`/`lifespan()` (with `@WithDefault`). `application.properties:65` defines `mediashelf.jwt.issuer=${mp.jwt.verify.issuer}` / `lifespan=${smallrye.jwt.new-token.lifespan}` aliases so the single `mediashelf` mapping covers JWT without an empty-prefix mapping.

## Decisions

- **ADR 0001** `docs/adr/0001-auth-without-keycloak.md:1` — own JWT vs Keycloak: lightweight, reversible, defers Keycloak to phase with social login/refresh.
- **ADR 0002** `docs/adr/0002-qute-tailwind-cdn.md:1` — Qute + pure Tailwind CDN (sharp `rounded-sm`/`border`/`shadow-sm`, zinc, Inter) vs DaisyUI/Flowbite and vs `quarkus-web-bundler` (deferred to 0.3.0 for tables/cards).

## Next phases (not in this iteration)

- MediaItem / Library Entry / Status (movies, TV, games), Google OAuth deferred (add `POST /api/auth/google` ID-token exchange later), refresh tokens, roles beyond `User`.

## Packaging

```powershell
java -jar target/quarkus-app/quarkus-run.jar
```

## Troubleshooting

**`TypeTag :: UNKNOWN` or `BUILD FAILURE` on `quarkus:dev` with JDK 26**

```
Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

Cause: system `JAVA_HOME` points to JDK 26 (`C:\Projects\HOME\jdk-26.0.2.1`), but Quarkus 3.38 LTS + Lombok target JDK 21. Fix:

- Or set manually: `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"`
- Build now enforces `[21,22)` (`pom.xml:116`) and fails fast with `This project requires JDK 21...` instead of cryptic `TypeTag`. Lombok bumped to `1.18.46` so `-Denforcer.skip=true` compiles on JDK 26, but running Quarkus on 26 is not supported — use 21.

**Docker `Please configure the datasource URL ... or ensure the Docker daemon is up`**

`docker ps` must succeed. Start Docker Desktop: `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"` then retry.
