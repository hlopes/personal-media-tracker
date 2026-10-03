# Research: Quarkus modular-monolith packaging (2026)

- Ticket: [#39](https://github.com/hlopes/personal-media-tracker/issues/39) (child of map [#37](https://github.com/hlopes/personal-media-tracker/issues/37))
- Scope: single deployable, Quarkus 3.39.1, one `application.properties`, one Flyway chain V1–V8,
  `quarkus-maven-plugin` with `extensions:true`, JWT keys (`privateKey.pem`/`publicKey.pem`),
  REST-assured tests, spotless/checkstyle/pmd at `validate`. Only runnable module is `tracker-ui`.
- Method: official Quarkus guides (primary sources) + local repo state (`pom.xml`,
  `src/main/resources/application.properties`, `src/main/resources/db/migration/V1–V8`,
  `src/main/resources/*.pem`, `src/test/java/org/hlopes/*Test.java`). Facts + doc links only;
  no decisions (decisions stay in grilling tickets #40–#42).

## (1) `quarkus-maven-plugin` + `quarkus-bom` placement

- `quarkus-bom` (`io.quarkus.platform:quarkus-bom`) is imported once in the parent's
  `<dependencyManagement>` (`type=pom`, `scope=import`), with version via
  `quarkus.platform.group-id` / `quarkus.platform.artifact-id` / `quarkus.platform.version`
  properties. Child modules declare Quarkus dependencies without versions.
  Source: [Quarkus and Maven — project creation / platform coordinates](https://quarkus.io/guides/maven-tooling).
- The `quarkus` packaging provided by `quarkus-maven-plugin` "should be used only for the
  Quarkus application itself, not for the library modules that should keep the default jar
  packaging" (`quarkus` vs `jar`). It defines its own lifecycle
  (`generate-code` → `compile` → `generate-code-tests` → `test-compile` → `test` → `build`).
  Source: [Quarkus Maven plugin — Lifecycle](https://quarkus.io/guides/quarkus-maven-plugin).
- Consequence: only the runnable module (`tracker-ui`) gets the plugin with
  `<extensions>true</extensions>` (current root `pom.xml` lines 227–232 show this block).
  Library modules (`auth`, `catalog`, `library`) stay `jar` packaging and must not bind
  `quarkus:build`; duplicating the plugin per module makes every module attempt augmentation /
  runnable-artifact build, slows the build, and confuses dev-mode (known workaround is to skip
  the plugin execution in non-application modules).
  Source: [Maven tooling — multi-module / test plugin configuration](https://quarkus.io/guides/maven-tooling);
  community report of skipping plugin execution in non-app modules
  (e.g. `quarkus:dev` multi-module thread).
- Version handling: declare the plugin once in parent `<pluginManagement>` pinned to
  `${quarkus.platform.version}`; the runnable module references it without a version.
  Same pattern already used in this repo for `maven-compiler-plugin` / `surefire` /
  `failsafe`, and for Lombok/MapStruct annotation processor paths.

## (2) Single-owner `application.properties` / Flyway / keys pattern

- Config file loading: Quarkus reads `application.properties` from the classpath
  (`src/main/resources/application.properties`, `src/test/resources/application.properties`)
  and from any `jar` dependency containing an `application.properties` entry — each found file
  becomes a separate `ConfigSource`, merged per property by ordinal/classpath order — plus the
  `$PWD/config/application.properties` override. Per-module fragments therefore merge silently
  and same-key duplicates resolve by source order, not by module ownership.
  Source: [Configuration reference — 1.4 Application configuration file](https://quarkus.io/guides/config-reference).
- Build-time vs runtime split: many properties are fixed at build time and require repackaging
  after change. Scattering them across modules makes it unclear which module's rebuild picks
  them up. Single owner (`tracker-ui` `src/main/resources/application.properties`) avoids this.
  Source: [Configuring your application](https://quarkus.io/guides/config/) and its
  build-time/runtime notes; current single file owns datasource, Hibernate, Flyway, JWT,
  mailer, OpenAPI, logging, `mediashelf.*`, `quarkus.rest-client.*`, `quarkus.cache.*`.
- Flyway: default scan location is `db/migration` on the classpath
  (`quarkus.flyway.locations`, default `db/migration`); entries may be unprefixed/`classpath:`
  (SQL + Java migrations) or `filesystem:` (SQL only, recursive). `quarkus.flyway.migrate-at-start`
  runs migration at startup; named datasources take `quarkus.flyway."datasource-name".*`.
  Current repo: `quarkus.flyway.migrate-at-start=true`, `baseline-on-migrate=true`,
  `locations=db/migration`, V1–V8 under `src/main/resources/db/migration/`.
  Source: [Using Flyway](https://quarkus.io/guides/flyway)
  (incl. `src/main/resources/db/migration` convention and locations table).
- Multi-module implication: all module jars land on the runnable module's classpath, so one
  `db/migration` chain owned by `tracker-ui` keeps working without extra config. Splitting
  migrations per module would require an explicit multi-location list
  (e.g. `quarkus.flyway.locations=db/auth,db/catalog,…`) and discipline around the single
  `flyway_schema_history` table and version-number collisions. Note `quarkus.flyway.locations`
  is a build-time property (changing it per profile at runtime does not take effect).
  Source: [Using Flyway](https://quarkus.io/guides/flyway); build-time-only behavior discussed in
  [quarkusio/quarkus#10041](https://github.com/quarkusio/quarkus/issues/10041).
- JWT keys: `mp.jwt.verify.publickey.location=publicKey.pem` expects the PEM on the classpath;
  signing key `smallrye.jwt.sign.key.location=privateKey.pem` likewise; native builds additionally
  need `quarkus.native.resources.includes=publicKey.pem`. Current repo has both PEMs in
  `src/main/resources/` with `mp.jwt.verify.issuer=mediashelf` /
  `smallrye.jwt.sign.key.location` / `smallrye.jwt.new-token.*` keys in the single properties file.
  Keep both PEMs only in `tracker-ui`; duplicates in library jars would shadow each other on the
  classpath.
  Source: [Using JWT RBAC — configuring SmallRye JWT / adding a public key](https://quarkus.io/guides/security-jwt);
  token build side: [Build, sign and encrypt JWT](https://quarkus.io/guides/security-jwt-build/).

## (3) REST-assured / `quarkus-junit` test placement for non-runnable modules

- `@QuarkusTest` boots the full Quarkus application before the test run ("the application will
  be started before the test is run"; boot once, run all tests, shut down at the end), so any
  HTTP-level (REST-assured) test needs the whole application: routes, config, datasource,
  Flyway, keys.
  Source: [Testing your application](https://quarkus.io/guides/getting-started-testing).
- Therefore REST-assured `@QuarkusTest`s (current `src/test/java/org/hlopes/*Test.java`:
  `AuthResourceTest`, `CatalogResourceTest`, `LibraryResourceTest`, `PageResourceTest`,
  `AppFlowTest`, etc.) must live in the runnable module (`tracker-ui`), which has the
  `quarkus-junit` + `rest-assured` test dependencies, the surefire/failsafe
  (`@{argLine}`, `LogManager`) configuration, and the `application.properties` (incl.
  `%test.*` overrides such as `%test.quarkus.mailer.mock=true`,
  `%test.mediashelf.tmdb.api-key=test-key`). A library module on its own has no HTTP
  endpoint to test and no config/datasource to boot.
- Library modules test with plain JUnit (no container) for pure logic. Where CDI wiring without
  a full boot is wanted, `QuarkusComponentTest` starts only the CDI container + config service,
  not a full application.
  Source: [Testing components](https://quarkus.io/guides/testing-components).
- `@QuarkusIntegrationTest` (packaged `java -jar …` / native, prod profile) likewise only makes
  sense against the runnable module's artifact.
  Source: [Testing your application](https://quarkus.io/guides/getting-started-testing).

## (4) Known 3.x gotchas

- Qute templates outside the runnable module: all `src/main/resources/templates/**` files are
  discovered and build-time validated; templates coming from dependency jars are also picked up,
  with duplicate-path resolution via `quarkus.qute.duplicit-templates-strategy` (default
  `prioritize`): root application archive priority `30` beats other archives' `10`; remaining
  ties fail the build. Type-safe `CheckedTemplate` / `Template` injection checks run during the
  app-module build. Keep `templates/**` (current `app.html`, `base.html`, `library/*`,
  `auth/*`) plus `quarkus-qute` / `quarkus-rest-qute` in `tracker-ui`; library-owned templates
  risk silent shadowing or duplicate-path build failures.
  Source: [Qute reference — duplicate templates strategy / Quarkus integration](https://quarkus.io/guides/qute-reference);
  [Qute templating engine](https://quarkus.io/guides/qute/).
- REST client injection across modules: `@RegisterRestClient` (optionally
  `configKey="…"`) registers the interface as a CDI bean; inject with `@Inject @RestClient`
  (or plain `@Inject` for a single implementation); URL/scope/timeouts come from
  `quarkus.rest-client.<configKey|FQCN>.*` (e.g. `quarkus.rest-client.tmdb.url`,
  `quarkus.rest-client."org.hlopes.catalog.TmdbClient".url` — both patterns exist in the
  current properties file). The `quarkus-rest-client-jackson` extension must be on the
  runnable module's classpath for client generation; the interface itself may live in
  `tracker-catalog`, but all `quarkus.rest-client.*` keys stay in the single
  `application.properties`. Prefer `configKey` over FQCN-keyed properties to survive package moves.
  Source: [Using the REST Client](https://quarkus.io/guides/rest-client)
  (and legacy [resteasy-client](https://quarkus.io/guides/resteasy-client) for the
  `@RegisterRestClient`/`configKey` semantics).
- Arc bean discovery across jars: "By default, Quarkus will not discover CDI beans inside
  another module. The best way to enable CDI bean discovery for a module in a multi-module
  project would be to include the `jandex-maven-plugin`, unless it is the main application
  module already configured with the `quarkus-maven-plugin`, in which case it will be indexed
  automatically." Add to each library module:
  `io.smallrye:jandex-maven-plugin` (`jandex` goal, `make-index` execution).
  Alternative marker: `META-INF/beans.xml`. Background: ArC builds a single bean archive with
  `annotated` discovery from the Jandex index; unindexed jars contribute no beans.
  Source: [Quarkus and Maven — working with multi-module projects](https://quarkus.io/guides/maven-tooling);
  [CDI reference — bean discovery](https://quarkus.io/guides/cdi-reference);
  [CDI integration](https://quarkus.io/guides/cdi-integration);
  also summarized in [Baeldung — bean discovery with Jandex](https://www.baeldung.com/quarkus-bean-discovery-index).
- Related packaging notes: annotation-processor paths (Lombok, `lombok-mapstruct-binding`,
  `mapstruct-processor`, `-Amapstruct.defaultComponentModel=cdi`) and shared
  spotless/checkstyle/pmd `validate` bindings belong in the parent `pluginManagement` so all
  modules inherit them; `META-INF/resources/js/*` (chatbot, library-actions) is served from the
  runnable module's classpath like templates. Config-source merging note above applies to
  `META-INF/microprofile-config.properties` too (ordinal 100, below `application.properties`).
  Source: [Configuration reference — config sources](https://quarkus.io/guides/config-reference);
  [Quarkus and Maven](https://quarkus.io/guides/maven-tooling).

## Local pointers (unchanged code)

- `pom.xml`: `quarkus.platform.*` 3.39.1, `quarkus-bom` import, `quarkus-maven-plugin`
  `extensions:true`, compiler/surefire/failsafe/enforcer/spotless/checkstyle/pmd setup.
- `src/main/resources/application.properties`: single file (datasource/Hibernate/Flyway/JWT/
  mailer/OpenAPI/logging/`mediashelf.*`/rest-client/cache, `%test` overrides).
- `src/main/resources/db/migration/V1–V8`, `publicKey.pem`/`privateKey.pem`,
  `templates/**`, `META-INF/resources/js/*`, `src/test/java/org/hlopes/*Test.java`.
