# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Travel Diary — a Spring Boot 3.4.3 / Java 17 server-rendered web app (Thymeleaf) for planning trips, writing travel diaries/posts, bookmarking destinations, and browsing admin-curated travel info. Uses MyBatis (XML mappers, not JPA) against MySQL, Spring Security with form login, and server-side session auth.

## Commands

```bash
./gradlew compileJava        # compile
./gradlew test               # run all tests (JUnit 5 / AssertJ)
./gradlew test --tests "com.example.travlediary.service.event.EventServiceAdminTest"   # single test class
./gradlew test --tests "*EventFormTest.editFormSplitsExistingLocalDatesIntoYearMonthDayFields"  # single test method
./gradlew bootRun            # run the app locally (needs local MySQL, see below)
```

There is no linter configured; rely on `compileJava` and `test`.

## Local environment

- MySQL must be running locally; connection config is in `src/main/resources/application.yml` (`jdbc:mysql://localhost:3306/mydb`, user `localmaster`).
- DB schema reference (dumped DDL, not auto-applied): `docs/db/travel_diary_schema_reference.md`.
- Uploaded files are written to the path in `custom.upload-path` (`application.yml`), separate from the `src/main/resources/static/uploads` and repo-root `uploads/` sample content.
- Mail (registration verification, password reset) uses Gmail SMTP; `MAIL_USERNAME`/`MAIL_PASSWORD` come from env vars.
- `security.user.name`/`password` in `application.yml` (`admin`/`admin`) is Spring Boot's default in-memory user, not the app's real admin account — real users/roles live in the DB via `CustomUserDetails`.

## Architecture

Standard layered flow per feature: `Controller -> Service -> Mapper interface -> Mapper XML -> MySQL`. Each feature area (`event`, `post`, `board`, `course`, `destination`, `bookmark`, `comment`, `notice`, `faq`, `inquiry`, `travelinfo`, `user`, `search`, `recommend`, `amenity`, `category`, `info`) has matching subpackages under `controller/`, `service/`, and `repository/`, plus a same-named `*Mapper.xml` in `src/main/resources/mapper/`. When touching a feature, expect to read all four layers together.

- **MyBatis, not JPA.** Mapper interfaces in `repository/<feature>/` are paired 1:1 with XML in `resources/mapper/`. Do not introduce JPA/Hibernate into these paths — `AGENTS.md` explicitly forbids converting MyBatis areas to JPA.
- **Admin vs. public split.** Most features have a public controller (`controller/<feature>/`) and, where admin management exists, a separate `controller/admin/Admin<Feature>Controller` — both usually driving the same service/mapper. Admin templates live under `templates/admin/<feature>/`, public ones under `templates/<feature>/`. `/admin/**` requires role `ADMIN`; `/mypage/**` requires any authenticated user (`AGENTS.md`).
- **Security config** (`config/SecurityConfig.java`) is the single source of truth for which routes are public vs. authenticated vs. admin-only, and lists exact CSRF-exempt POST/DELETE routes via regex matchers. When adding a new mutating endpoint, check whether it needs a CSRF matcher entry and an `authorizeHttpRequests` rule — the default is `anyRequest().authenticated()`.
- **Country/category data** is seeded at startup from `resources/json/country_categories.json` via `config/CountryCategoryLoader` (`@PostConstruct`) into `CountryCategoryMapper`, using a tree-to-flat-list conversion. Country category IDs must never be hardcoded in code (`AGENTS.md`) — resolve them through the mapper/service instead.
- **Global concerns**: `config/GlobalModelAttributes` injects shared Thymeleaf model attributes; `config/GlobalRequestControllerAdvice` centralizes controller-advice-level handling; `config/WebSecurityIgnoreConfig` excludes static/resource paths from the security filter chain entirely (distinct from `permitAll` in `SecurityConfig`); `config/CustomLoginSuccessHandler`/`CustomLogoutSuccessHandler` drive post-auth redirects.
- **Validation pattern (event feature)**: form DTOs (e.g. `dto/EventForm`) convert to/from the MyBatis model (e.g. `model/Event`), and services throw feature-specific exceptions (e.g. `service/event/EventValidationException`) rather than relying solely on Bean Validation — follow this pattern for other admin CRUD features if extending them.
- **Uploads**: `api/EditorImageUploadApi` handles rich-text editor image uploads (used by post/board editors); `service/file/` provides shared file-storage helpers. HTML from user-authored content is sanitized with Jsoup before storage/render.

## Working conventions (from AGENTS.md)

- Run `git status --short` before starting work; don't revert working-tree changes that aren't yours.
- Make the minimal change needed; follow existing structure/style in the touched files rather than introducing new patterns.
- Never connect to the DB directly or run SQL directly; never change DB schema without an explicit request — consult `docs/db/travel_diary_schema_reference.md` instead.
- Do not commit or push, and do not use `git reset`/`git restore`/`git checkout` to discard existing changes.
- After implementing a change, run the smallest relevant tests first when practical.
- Before declaring the implementation complete, run `./gradlew compileJava`, `./gradlew test`, `git diff --check`, and `git status --short`.
- Avoid broad refactors that risk breaking existing features.

## Development workflow

- Work in small, reviewable steps. Do not implement a large feature across many files in one pass.
- When a task has multiple stages, complete only the currently requested stage and stop.
- Prefer one logical change at a time so the user can review/test it before proceeding.
- Do not proactively continue into the next feature or improvement unless explicitly asked.

## Analysis vs implementation

- If the user asks to inspect, analyze, diagnose, review, or propose a plan, do not modify files.
- During analysis, read only the files necessary to answer the question.
- Wait for an explicit implementation request before editing code.
- Do not treat a proposed plan as permission to implement it.

## Context and token efficiency

- Do not scan the entire repository unless necessary.
- Start with the files directly related to the requested behavior and expand only when dependencies require it.
- Reuse information already established in the current session instead of repeatedly re-reading unchanged files.
- Avoid long reports of files inspected or unchanged code.
- Keep implementation summaries concise: changed files, key behavior, verification result, and remaining issues only.
- Task prompts may omit repository-wide conventions already defined in this file. Do not require repeated reminders about Git restrictions, DB restrictions, verification commands, or workflow unless the current task needs an exception.

## Verification order

Use this order unless the user explicitly requests otherwise:

1. Implement only the currently requested small change.
2. Run the smallest relevant test(s) first when practical.
3. Review the changed diff for unintended edits.
4. Before declaring the task fully complete, run `./gradlew compileJava`, `./gradlew test`, `git diff --check`, and `git status --short`.
5. Let the user perform browser/manual verification when UI or runtime behavior is involved.
6. Do not commit or push unless explicitly requested by the user.
7. When the user asks to commit or push, do not perform Git write operations automatically; provide the commands unless the user explicitly asks Claude Code to execute them.

## Browser/manual verification

- For UI, JavaScript, WebSocket, realtime, authentication/session, and other browser-dependent behavior, do not claim that the feature is fully verified only from automated tests.
- After automated verification succeeds, give the user a short list of only the important browser behaviors that still need manual confirmation.
- Do not repeat obvious or exhaustive browser test checklists. Limit manual verification guidance to behaviors that automated tests cannot reliably prove.
- If the user reports that browser verification succeeded, treat that stage as complete unless there is a specific unresolved issue.
- Do not proactively continue to the next feature after browser verification; wait for the user's instruction.

## Secrets and credentials

- Never write credentials, passwords, API keys, SMTP secrets, tokens, or private keys into source files, documentation, tests, prompts, or Git-tracked files.
- Keep secrets in environment variables or ignored local configuration only.
- Never ask the user to paste secret values when the task can be completed without them.
- If a secret appears in command output or configuration, do not repeat it in summaries.

- Do not use Python/Perl scripts, sed, or other bulk replacement commands to modify source files. Edit the target files directly and keep each change small and reviewable.