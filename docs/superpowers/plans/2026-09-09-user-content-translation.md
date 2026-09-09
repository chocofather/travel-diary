# User Content Translation Implementation Plan

**Goal:** Add on-demand destination comment translation using local language detection, a reusable persistent cache with database leases, Google Cloud Translation v3, and compact original/translation toggling UI.

## Task 1: Define database contract and dependencies

- Add the Lingua and Google Cloud Translation libraries to Gradle.
- Add a reviewed SQL migration draft for `destination_comments.source_language` and `content_translation_cache`; do not execute it.
- Add translation configuration with environment-backed Google settings and configurable limits.

## Task 2: Implement and test local language detection

- Write detector tests for `ko`, `en`, `ja`, `zh-CN`, `zh-TW`, and ambiguous `und`.
- Implement a candidate-limited Lingua detector with Unicode/script and Chinese variant helpers.
- Persist the detected source language during destination comment create/update.

## Task 3: Implement and test the common translation cache workflow

- Define common content key, source snapshot, cache entry, provider client/result, and service result types.
- Add MyBatis cache mapper/XML with atomic insert-or-conditional-claim lease operations and token-guarded READY/FAILED completion.
- Implement exact UTF-8 SHA-256 matching, cache hit/miss behavior, stale-source protection, retry windows, and provider failure isolation.
- Test cache hit/miss, source hash replacement, concurrent first request, stale source, hidden/deleted content, provider failure, and request/provider rate limits.

## Task 4: Add destination comment and Google adapters

- Add a destination comment source reader that only returns public comments and can safely correct an `und` source language after a provider result.
- Implement `MachineTranslationClient` with Google Cloud Translation v3 and lazy ADC-backed client creation.
- Expose a locale-derived destination comment translation endpoint; never accept source text or target language from request input.

## Task 5: Add and test the destination comment UI

- Include `sourceLanguage` and a server-locale-derived translation availability flag in comment JSON.
- Render `번역 보기` only for different/unknown source languages, call the API on click, and toggle with `원문 보기`.
- Use DOM `textContent` for translated output, handle PROCESSING retry and recoverable errors, and never auto-translate.
- Add focused DTO/controller/render behavior tests where the repository already has matching test patterns.

## Task 6: Verify

- Run focused tests, `./gradlew compileJava`, `./gradlew test`, `git diff --check`, and `git status --short`.
- Review the final diff for accidental security, comment CRUD, locale, and schema changes.

