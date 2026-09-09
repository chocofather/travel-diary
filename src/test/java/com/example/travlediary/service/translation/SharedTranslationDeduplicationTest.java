package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.ContentTranslationCache;
import com.example.travlediary.model.translation.SharedTranslationCache;
import com.example.travlediary.repository.translation.ContentTranslationCacheMapper;
import com.example.travlediary.repository.translation.SharedTranslationCacheMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedTranslationDeduplicationTest {
    private static final Timestamp VERSION =
            Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));

    @Test
    void exactTextAcrossContentIdsCallsProviderAndUsageOnlyOnce() {
        TestContext context = context(Map.of(
                100L, source(TranslatableContentType.DESTINATION_COMMENT, 100L, "hello", "en"),
                200L, source(TranslatableContentType.DESTINATION_COMMENT, 200L, "hello", "en")));

        ContentTranslationResponse first = context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "ko", "10.0.0.1", 7L);
        ContentTranslationResponse second = context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 200L, "ko", "10.0.0.2", 9L);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.translatedText()).isEqualTo("ko:hello");
        assertThat(context.providerCalls()).hasValue(1);
        assertThat(context.usage().calls).isEqualTo(1);
        assertThat(context.rateLimiter().externalCalls).isEqualTo(1);
        assertThat(context.contentCaches().find(
                "DESTINATION_COMMENT", 200L, "content", "ko").getStatus()).isEqualTo("READY");
    }

    @Test
    void sharedHitReplacesAnExistingProcessingContentCache() {
        TestContext context = context(Map.of(
                100L, source(TranslatableContentType.DESTINATION_COMMENT, 100L, "hello", "en"),
                200L, source(TranslatableContentType.DESTINATION_COMMENT, 200L, "hello", "en")));
        context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "ko", "10.0.0.1", 7L);
        ContentTranslationCache processing = new ContentTranslationCache();
        processing.setContentType("DESTINATION_COMMENT");
        processing.setContentId(200L);
        processing.setSourceField("content");
        processing.setTargetLanguage("ko");
        processing.setSourceHash(ContentTranslationService.sourceHash("hello"));
        processing.setLeaseToken("00000000-0000-0000-0000-000000000001");
        processing.setLeaseExpiresAt(Timestamp.from(Instant.now().plusSeconds(30)));
        context.contentCaches().insertProcessing(processing);

        ContentTranslationResponse response = context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 200L, "ko", "10.0.0.2", 9L);

        assertThat(response.cached()).isTrue();
        assertThat(context.contentCaches().find(
                "DESTINATION_COMMENT", 200L, "content", "ko").getStatus()).isEqualTo("READY");
        assertThat(context.providerCalls()).hasValue(1);
    }

    @Test
    void exactTextIsSharedAcrossDestinationAndPostComments() {
        TestContext context = context(Map.of(
                100L, source(TranslatableContentType.DESTINATION_COMMENT, 100L, "hello", "en"),
                200L, source(TranslatableContentType.POST_COMMENT, 200L, "hello", "en")));

        context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "ko", "10.0.0.1", 7L);
        ContentTranslationResponse post = context.service().translate(
                TranslatableContentType.POST_COMMENT, 200L, "ko", "10.0.0.2", 99L);

        assertThat(post.cached()).isTrue();
        assertThat(post.translatedText()).isEqualTo("ko:hello");
        assertThat(context.providerCalls()).hasValue(1);
        assertThat(context.usage().calls).isEqualTo(1);
    }

    @Test
    void exactTextIsSharedFromPostToCourseWithoutAdditionalProviderOrUsageCalls() {
        TestContext context = context(Map.of(
                100L, source(TranslatableContentType.POST_COMMENT, 100L, "hello", "en"),
                200L, source(TranslatableContentType.COURSE_COMMENT, 200L, "hello", "en")));

        context.service().translate(
                TranslatableContentType.POST_COMMENT, 100L, "ko", "10.0.0.1", 7L);
        ContentTranslationResponse course = context.service().translate(
                TranslatableContentType.COURSE_COMMENT, 200L, "ko", "10.0.0.2", 99L);

        assertThat(course.cached()).isTrue();
        assertThat(course.translatedText()).isEqualTo("ko:hello");
        assertThat(context.providerCalls()).hasValue(1);
        assertThat(context.usage().calls).isEqualTo(1);
        assertThat(context.rateLimiter().externalCalls).isEqualTo(1);
    }

    @Test
    void whitespaceCasePunctuationAndDifferentTextRemainExact() {
        TestContext context = context(Map.of(
                1L, source(TranslatableContentType.DESTINATION_COMMENT, 1L, "hello", "en"),
                2L, source(TranslatableContentType.DESTINATION_COMMENT, 2L, "Hello", "en"),
                3L, source(TranslatableContentType.DESTINATION_COMMENT, 3L, "hello ", "en"),
                4L, source(TranslatableContentType.DESTINATION_COMMENT, 4L, "hello!", "en"),
                5L, source(TranslatableContentType.DESTINATION_COMMENT, 5L, "hello mr.gpt", "en"),
                6L, source(TranslatableContentType.DESTINATION_COMMENT, 6L, "hello mr.mj", "en")));

        for (long id = 1L; id <= 6L; id++) {
            context.service().translate(
                    TranslatableContentType.DESTINATION_COMMENT, id, "ko", "10.0.0." + id, id);
        }

        assertThat(context.providerCalls()).hasValue(6);
        assertThat(context.usage().calls).isEqualTo(6);
    }

    @Test
    void targetLanguageAndTranslationProfileArePartOfTheSharedKey() {
        Map<Long, TranslationSourceSnapshot> sources = Map.of(
                1L, source(TranslatableContentType.DESTINATION_COMMENT, 1L, "hello", "en"),
                2L, source(TranslatableContentType.DESTINATION_COMMENT, 2L, "hello", "en"),
                3L, source(TranslatableContentType.DESTINATION_COMMENT, 3L, "hello", "en"));
        FakeContentCacheMapper contentCaches = new FakeContentCacheMapper();
        FakeSharedCacheMapper sharedCaches = new FakeSharedCacheMapper();
        CountingUsageGate usage = new CountingUsageGate();
        CountingRateLimiter rateLimiter = new CountingRateLimiter();
        AtomicInteger calls = new AtomicInteger();
        MachineTranslationClient client = countingClient(calls,
                request -> new MachineTranslation(request.target() + ":" + request.text(), "en"));
        TranslationSourceRegistry registry = registry(sources);
        ContentTranslationService profileV1 = service(
                registry, contentCaches, sharedCaches, client, usage, rateLimiter, "google-v3-general-v1");
        ContentTranslationService profileV2 = service(
                registry, contentCaches, sharedCaches, client, usage, rateLimiter, "google-v3-general-v2");

        profileV1.translate(TranslatableContentType.DESTINATION_COMMENT, 1L, "ko", "10.0.0.1", 1L);
        profileV1.translate(TranslatableContentType.DESTINATION_COMMENT, 2L, "ja", "10.0.0.2", 2L);
        profileV2.translate(TranslatableContentType.DESTINATION_COMMENT, 3L, "ko", "10.0.0.3", 3L);

        assertThat(calls).hasValue(3);
        assertThat(usage.calls).isEqualTo(3);
    }

    @Test
    void sourceLanguageIsPartOfTheSharedKey() {
        TestContext context = context(Map.of(
                1L, source(TranslatableContentType.DESTINATION_COMMENT, 1L, "hello", "en"),
                2L, source(TranslatableContentType.DESTINATION_COMMENT, 2L, "hello", "ja")));

        context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 1L, "ko", "10.0.0.1", 1L);
        context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 2L, "ko", "10.0.0.2", 2L);

        assertThat(context.providerCalls()).hasValue(2);
        assertThat(context.usage().calls).isEqualTo(2);
    }

    @Test
    void concurrentExactRequestsForDifferentContentIdsHaveOneGlobalOwner() throws Exception {
        TestContext context = context(Map.of(
                100L, source(TranslatableContentType.DESTINATION_COMMENT, 100L, "hello", "en"),
                200L, source(TranslatableContentType.DESTINATION_COMMENT, 200L, "hello", "en")),
                true, false);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> context.service().translate(
                    TranslatableContentType.DESTINATION_COMMENT, 100L, "ko", "10.0.0.1", 7L));
            assertThat(context.providerStarted().await(2, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> context.service().translate(
                    TranslatableContentType.DESTINATION_COMMENT, 200L, "ko", "10.0.0.2", 9L));
            ContentTranslationResponse waiting = second.get(2, TimeUnit.SECONDS);
            assertThat(waiting.status()).isEqualTo("PROCESSING");

            context.releaseProvider().countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).status()).isEqualTo("READY");
            assertThat(context.service().translate(
                    TranslatableContentType.DESTINATION_COMMENT, 200L, "ko", "10.0.0.2", 9L)
                    .translatedText()).isEqualTo("ko:hello");
            assertThat(context.providerCalls()).hasValue(1);
            assertThat(context.usage().calls).isEqualTo(1);
        } finally {
            context.releaseProvider().countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedExactTranslationBacksOffGloballyWithoutAnotherProviderCall() {
        TestContext context = context(Map.of(
                100L, source(TranslatableContentType.DESTINATION_COMMENT, 100L, "hello", "en"),
                200L, source(TranslatableContentType.POST_COMMENT, 200L, "hello", "en")),
                false, true);

        assertThatThrownBy(() -> context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "ko", "10.0.0.1", 7L))
                .isInstanceOf(MachineTranslationException.class);
        ContentTranslationResponse retry = context.service().translate(
                TranslatableContentType.POST_COMMENT, 200L, "ko", "10.0.0.2", 9L);

        assertThat(retry.status()).isEqualTo("PROCESSING");
        assertThat(retry.retryAfterSeconds()).isGreaterThan(0L);
        assertThat(context.providerCalls()).hasValue(1);
        assertThat(context.usage().calls).isEqualTo(1);
    }

    @Test
    void changedSourceNeverUsesTheOldSharedResult() {
        Map<Long, TranslationSourceSnapshot> sources = new ConcurrentHashMap<>();
        sources.put(100L, source(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "hello", "en"));
        TestContext context = context(sources);

        context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "ko", "10.0.0.1", 7L);
        sources.put(100L, source(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "hello!", "en"));
        ContentTranslationResponse changed = context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "ko", "10.0.0.1", 7L);

        assertThat(changed.translatedText()).isEqualTo("ko:hello!");
        assertThat(context.providerCalls()).hasValue(2);
    }

    @Test
    void hiddenContentCannotExposeAnExistingSharedResult() {
        Map<Long, TranslationSourceSnapshot> sources = new ConcurrentHashMap<>();
        sources.put(100L, source(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "hello", "en"));
        sources.put(200L, source(
                TranslatableContentType.DESTINATION_COMMENT, 200L, "hello", "en"));
        TestContext context = context(sources);
        context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "ko", "10.0.0.1", 7L);
        sources.remove(200L);

        assertThatThrownBy(() -> context.service().translate(
                TranslatableContentType.DESTINATION_COMMENT, 200L, "ko", "10.0.0.2", 9L))
                .isInstanceOf(TranslationNotFoundException.class);
        assertThat(context.providerCalls()).hasValue(1);
    }

    @Test
    void unavailableCourseCommentCannotExposeAnExistingSharedResult() {
        Map<Long, TranslationSourceSnapshot> sources = new ConcurrentHashMap<>();
        sources.put(100L, source(
                TranslatableContentType.POST_COMMENT, 100L, "hello", "en"));
        sources.put(200L, source(
                TranslatableContentType.COURSE_COMMENT, 200L, "hello", "en"));
        TestContext context = context(sources);
        context.service().translate(
                TranslatableContentType.POST_COMMENT, 100L, "ko", "10.0.0.1", 7L);
        sources.remove(200L);

        assertThatThrownBy(() -> context.service().translate(
                TranslatableContentType.COURSE_COMMENT, 200L, "ko", "10.0.0.2", 9L))
                .isInstanceOf(TranslationNotFoundException.class);
        assertThat(context.providerCalls()).hasValue(1);
        assertThat(context.usage().calls).isEqualTo(1);
    }

    @Test
    void userPostTitleAndHtmlBodyReuseExactSharedEntriesWithoutAdditionalCost() {
        Map<FieldSourceKey, TranslationSourceSnapshot> sources = new ConcurrentHashMap<>();
        sources.put(new FieldSourceKey(1L, "title"),
                postSource(1L, "title", "Best places in Seoul", "en", "text/plain"));
        sources.put(new FieldSourceKey(2L, "title"),
                postSource(2L, "title", "Best places in Seoul", "en", "text/plain"));
        sources.put(new FieldSourceKey(1L, "content"),
                postSource(1L, "content", "<p>Same English body</p>", "en", "text/html"));
        sources.put(new FieldSourceKey(2L, "content"),
                postSource(2L, "content", "<p>Same English body</p>", "en", "text/html"));
        TestContext context = fieldContext(sources);

        context.service().translate(
                TranslatableContentType.USER_POST, 1L, "title", "ko", "10.0.0.1", 1L);
        ContentTranslationResponse titleHit = context.service().translate(
                TranslatableContentType.USER_POST, 2L, "title", "ko", "10.0.0.2", 2L);
        context.service().translate(
                TranslatableContentType.USER_POST, 1L, "content", "ko", "10.0.0.1", 1L);
        ContentTranslationResponse contentHit = context.service().translate(
                TranslatableContentType.USER_POST, 2L, "content", "ko", "10.0.0.2", 2L);

        assertThat(titleHit.cached()).isTrue();
        assertThat(contentHit.cached()).isTrue();
        assertThat(context.providerCalls()).hasValue(2);
        assertThat(context.usage().calls).isEqualTo(2);
        assertThat(context.rateLimiter().externalCalls).isEqualTo(2);
    }

    @Test
    void changingOneUserPostFieldKeepsTheOtherFieldContentCacheReady() {
        Map<FieldSourceKey, TranslationSourceSnapshot> sources = new ConcurrentHashMap<>();
        sources.put(new FieldSourceKey(1L, "title"),
                postSource(1L, "title", "Original title", "en", "text/plain"));
        sources.put(new FieldSourceKey(1L, "content"),
                postSource(1L, "content", "<p>Original body</p>", "en", "text/html"));
        TestContext context = fieldContext(sources);
        context.service().translate(
                TranslatableContentType.USER_POST, 1L, "title", "ko", "10.0.0.1", 1L);
        context.service().translate(
                TranslatableContentType.USER_POST, 1L, "content", "ko", "10.0.0.1", 1L);

        sources.put(new FieldSourceKey(1L, "title"),
                postSource(1L, "title", "Changed title", "en", "text/plain"));
        ContentTranslationResponse titleMiss = context.service().translate(
                TranslatableContentType.USER_POST, 1L, "title", "ko", "10.0.0.1", 1L);
        ContentTranslationResponse unchangedContent = context.service().translate(
                TranslatableContentType.USER_POST, 1L, "content", "ko", "10.0.0.1", 1L);

        assertThat(titleMiss.cached()).isFalse();
        assertThat(unchangedContent.cached()).isTrue();
        assertThat(context.providerCalls()).hasValue(3);

        sources.put(new FieldSourceKey(1L, "content"),
                postSource(1L, "content", "<p>Changed body</p>", "en", "text/html"));
        ContentTranslationResponse unchangedTitle = context.service().translate(
                TranslatableContentType.USER_POST, 1L, "title", "ko", "10.0.0.1", 1L);
        ContentTranslationResponse contentMiss = context.service().translate(
                TranslatableContentType.USER_POST, 1L, "content", "ko", "10.0.0.1", 1L);

        assertThat(unchangedTitle.cached()).isTrue();
        assertThat(contentMiss.cached()).isFalse();
        assertThat(context.providerCalls()).hasValue(4);
    }

    @Test
    void courseTitleAndHtmlContentReuseExactSharedEntriesWithoutAdditionalCost() {
        Map<FieldSourceKey, TranslationSourceSnapshot> sources = new ConcurrentHashMap<>();
        sources.put(new FieldSourceKey(1L, "title"), fieldSource(
                TranslatableContentType.COURSE, 1L, "title",
                "Seoul One Day Trip", "en", "text/plain"));
        sources.put(new FieldSourceKey(2L, "title"), fieldSource(
                TranslatableContentType.COURSE, 2L, "title",
                "Seoul One Day Trip", "en", "text/plain"));
        sources.put(new FieldSourceKey(1L, "content"), fieldSource(
                TranslatableContentType.COURSE, 1L, "content",
                "<p>Same course description</p>", "en", "text/html"));
        sources.put(new FieldSourceKey(2L, "content"), fieldSource(
                TranslatableContentType.COURSE, 2L, "content",
                "<p>Same course description</p>", "en", "text/html"));
        TestContext context = fieldContext(TranslatableContentType.COURSE, sources);

        context.service().translate(TranslatableContentType.COURSE, 1L,
                "title", "ko", "10.0.0.1", 1L);
        ContentTranslationResponse titleHit = context.service().translate(
                TranslatableContentType.COURSE, 2L,
                "title", "ko", "10.0.0.2", 2L);
        context.service().translate(TranslatableContentType.COURSE, 1L,
                "content", "ko", "10.0.0.1", 1L);
        ContentTranslationResponse contentHit = context.service().translate(
                TranslatableContentType.COURSE, 2L,
                "content", "ko", "10.0.0.2", 2L);

        assertThat(titleHit.cached()).isTrue();
        assertThat(contentHit.cached()).isTrue();
        assertThat(context.providerCalls()).hasValue(2);
        assertThat(context.usage().calls).isEqualTo(2);
        assertThat(context.rateLimiter().externalCalls).isEqualTo(2);
    }

    @Test
    void changingOneCourseFieldKeepsTheOtherFieldContentCacheReady() {
        Map<FieldSourceKey, TranslationSourceSnapshot> sources = new ConcurrentHashMap<>();
        sources.put(new FieldSourceKey(1L, "title"), fieldSource(
                TranslatableContentType.COURSE, 1L, "title",
                "Original course", "en", "text/plain"));
        sources.put(new FieldSourceKey(1L, "content"), fieldSource(
                TranslatableContentType.COURSE, 1L, "content",
                "<p>Original course body</p>", "en", "text/html"));
        TestContext context = fieldContext(TranslatableContentType.COURSE, sources);
        context.service().translate(TranslatableContentType.COURSE, 1L,
                "title", "ko", null, 1L);
        context.service().translate(TranslatableContentType.COURSE, 1L,
                "content", "ko", null, 1L);

        sources.put(new FieldSourceKey(1L, "title"), fieldSource(
                TranslatableContentType.COURSE, 1L, "title",
                "Changed course", "en", "text/plain"));
        ContentTranslationResponse titleMiss = context.service().translate(
                TranslatableContentType.COURSE, 1L, "title", "ko", null, 1L);
        ContentTranslationResponse contentHit = context.service().translate(
                TranslatableContentType.COURSE, 1L, "content", "ko", null, 1L);

        assertThat(titleMiss.cached()).isFalse();
        assertThat(contentHit.cached()).isTrue();
        assertThat(context.providerCalls()).hasValue(3);

        sources.put(new FieldSourceKey(1L, "content"), fieldSource(
                TranslatableContentType.COURSE, 1L, "content",
                "<p>Changed course body</p>", "en", "text/html"));
        ContentTranslationResponse titleHit = context.service().translate(
                TranslatableContentType.COURSE, 1L, "title", "ko", null, 1L);
        ContentTranslationResponse contentMiss = context.service().translate(
                TranslatableContentType.COURSE, 1L, "content", "ko", null, 1L);

        assertThat(titleHit.cached()).isTrue();
        assertThat(contentMiss.cached()).isFalse();
        assertThat(context.providerCalls()).hasValue(4);
    }

    private TestContext context(Map<Long, TranslationSourceSnapshot> sources) {
        return context(sources, false, false);
    }

    private TestContext context(Map<Long, TranslationSourceSnapshot> sources,
                                boolean blockProvider,
                                boolean failProvider) {
        FakeContentCacheMapper contentCaches = new FakeContentCacheMapper();
        FakeSharedCacheMapper sharedCaches = new FakeSharedCacheMapper();
        CountingUsageGate usage = new CountingUsageGate();
        CountingRateLimiter rateLimiter = new CountingRateLimiter();
        AtomicInteger providerCalls = new AtomicInteger();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        MachineTranslationClient client = countingClient(providerCalls, request -> {
            providerStarted.countDown();
            if (blockProvider) {
                try {
                    releaseProvider.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failProvider) throw new MachineTranslationException("provider unavailable");
            return new MachineTranslation(request.target() + ":" + request.text(), request.source());
        });
        TranslationSourceRegistry registry = registry(sources);
        ContentTranslationService service = service(
                registry, contentCaches, sharedCaches, client, usage, rateLimiter,
                "google-v3-general-v1");
        return new TestContext(service, contentCaches, providerCalls, usage, rateLimiter,
                providerStarted, releaseProvider);
    }

    private TestContext fieldContext(Map<FieldSourceKey, TranslationSourceSnapshot> sources) {
        return fieldContext(TranslatableContentType.USER_POST, sources);
    }

    private TestContext fieldContext(TranslatableContentType type,
                                     Map<FieldSourceKey, TranslationSourceSnapshot> sources) {
        FakeContentCacheMapper contentCaches = new FakeContentCacheMapper();
        FakeSharedCacheMapper sharedCaches = new FakeSharedCacheMapper();
        CountingUsageGate usage = new CountingUsageGate();
        CountingRateLimiter rateLimiter = new CountingRateLimiter();
        AtomicInteger providerCalls = new AtomicInteger();
        MachineTranslationClient client = countingClient(providerCalls,
                request -> new MachineTranslation(request.target() + ":" + request.text(), request.source()));
        TranslationSourceRegistry registry = new TranslationSourceRegistry(
                List.of(new FieldSnapshotReader(type, sources)));
        ContentTranslationService service = service(
                registry, contentCaches, sharedCaches, client, usage, rateLimiter,
                "google-v3-general-v1");
        return new TestContext(service, contentCaches, providerCalls, usage, rateLimiter,
                new CountDownLatch(0), new CountDownLatch(0));
    }

    private ContentTranslationService service(
            TranslationSourceRegistry registry,
            FakeContentCacheMapper contentCaches,
            FakeSharedCacheMapper sharedCaches,
            MachineTranslationClient client,
            CountingUsageGate usage,
            CountingRateLimiter rateLimiter,
            String profile) {
        return new ContentTranslationService(
                registry, contentCaches, sharedCaches, client, rateLimiter, properties(),
                new TranslationCacheFinalizer(registry, contentCaches), usage,
                new TranslationProviderMetadata(profile));
    }

    private TranslationSourceRegistry registry(Map<Long, TranslationSourceSnapshot> sources) {
        return new TranslationSourceRegistry(List.of(
                new SnapshotReader(TranslatableContentType.DESTINATION_COMMENT, sources),
                new SnapshotReader(TranslatableContentType.POST_COMMENT, sources),
                new SnapshotReader(TranslatableContentType.COURSE_COMMENT, sources)));
    }

    private MachineTranslationClient countingClient(
            AtomicInteger calls,
            Function<TranslationRequest, MachineTranslation> translator) {
        return (text, source, target) -> {
            calls.incrementAndGet();
            return translator.apply(new TranslationRequest(text, source, target));
        };
    }

    private TranslationProperties properties() {
        return new TranslationProperties(
                2_000, Duration.ofSeconds(30), Duration.ofSeconds(30),
                1_000, 1_000, 1_000, Duration.ofMinutes(1), true, 400_000L,
                20_000L, 5_000L);
    }

    private static TranslationSourceSnapshot source(
            TranslatableContentType type, Long id, String text, String language) {
        return new TranslationSourceSnapshot(type, id, "content", text, language, VERSION);
    }

    private static TranslationSourceSnapshot postSource(
            Long id, String field, String text, String language, String mimeType) {
        return fieldSource(TranslatableContentType.USER_POST,
                id, field, text, language, mimeType);
    }

    private static TranslationSourceSnapshot fieldSource(
            TranslatableContentType type, Long id, String field,
            String text, String language, String mimeType) {
        return new TranslationSourceSnapshot(type, id, field, text, language, VERSION, mimeType);
    }

    private record TranslationRequest(String text, String source, String target) { }

    private record FieldSourceKey(Long id, String field) { }

    private record TestContext(
            ContentTranslationService service,
            FakeContentCacheMapper contentCaches,
            AtomicInteger providerCalls,
            CountingUsageGate usage,
            CountingRateLimiter rateLimiter,
            CountDownLatch providerStarted,
            CountDownLatch releaseProvider) { }

    private static final class SnapshotReader implements TranslationSourceReader {
        private final TranslatableContentType type;
        private final Map<Long, TranslationSourceSnapshot> sources;

        private SnapshotReader(TranslatableContentType type,
                               Map<Long, TranslationSourceSnapshot> sources) {
            this.type = type;
            this.sources = sources;
        }

        @Override
        public TranslatableContentType contentType() {
            return type;
        }

        @Override
        public Optional<TranslationSourceSnapshot> findVisible(Long contentId) {
            return Optional.ofNullable(sources.get(contentId)).filter(source -> source.contentType() == type);
        }

        @Override
        public Optional<TranslationSourceSnapshot> findVisibleForUpdate(Long contentId) {
            return findVisible(contentId);
        }
    }

    private static final class FieldSnapshotReader implements TranslationSourceReader {
        private final TranslatableContentType type;
        private final Map<FieldSourceKey, TranslationSourceSnapshot> sources;

        private FieldSnapshotReader(TranslatableContentType type,
                                    Map<FieldSourceKey, TranslationSourceSnapshot> sources) {
            this.type = type;
            this.sources = sources;
        }

        @Override
        public TranslatableContentType contentType() {
            return type;
        }

        @Override
        public Optional<TranslationSourceSnapshot> findVisible(Long contentId) {
            return findVisible(contentId, "content");
        }

        @Override
        public Optional<TranslationSourceSnapshot> findVisible(Long contentId, String sourceField) {
            return Optional.ofNullable(sources.get(new FieldSourceKey(contentId, sourceField)));
        }

        @Override
        public Optional<TranslationSourceSnapshot> findVisibleForUpdate(Long contentId) {
            return findVisibleForUpdate(contentId, "content");
        }

        @Override
        public Optional<TranslationSourceSnapshot> findVisibleForUpdate(
                Long contentId, String sourceField) {
            return findVisible(contentId, sourceField);
        }
    }

    private static final class CountingUsageGate implements TranslationUsageReservationGate {
        private int calls;

        @Override
        public synchronized void reserve(String sourceText, String ipAddress, Long userId) {
            calls++;
        }
    }

    private static final class CountingRateLimiter implements TranslationRateLimiter {
        private int requestCalls;
        private int externalCalls;

        @Override
        public synchronized void checkRequest(String ipAddress, Long userId) {
            requestCalls++;
        }

        @Override
        public synchronized void checkExternalCall(String ipAddress, Long userId) {
            externalCalls++;
        }
    }

    private record ContentKey(String type, Long id, String field, String target) { }

    private static final class FakeContentCacheMapper implements ContentTranslationCacheMapper {
        private final Map<ContentKey, ContentTranslationCache> caches = new ConcurrentHashMap<>();

        @Override
        public synchronized ContentTranslationCache find(
                String contentType, Long contentId, String sourceField, String targetLanguage) {
            return caches.get(new ContentKey(contentType, contentId, sourceField, targetLanguage));
        }

        @Override
        public synchronized int insertProcessing(ContentTranslationCache cache) {
            ContentKey key = key(cache);
            if (caches.containsKey(key)) return 0;
            cache.setStatus("PROCESSING");
            caches.put(key, cache);
            return 1;
        }

        @Override
        public synchronized int tryClaim(
                String contentType, Long contentId, String sourceField, String targetLanguage,
                byte[] sourceHash, String leaseToken, Timestamp now, Timestamp leaseExpiresAt) {
            ContentTranslationCache current = find(contentType, contentId, sourceField, targetLanguage);
            if (current == null) return 0;
            boolean changed = !Arrays.equals(current.getSourceHash(), sourceHash);
            boolean expired = "PROCESSING".equals(current.getStatus())
                    && (current.getLeaseExpiresAt() == null
                    || !current.getLeaseExpiresAt().after(now));
            boolean retryable = "FAILED".equals(current.getStatus())
                    && (current.getRetryAfter() == null || !current.getRetryAfter().after(now));
            if (!changed && !expired && !retryable) return 0;
            current.setSourceHash(sourceHash);
            current.setTranslatedText(null);
            current.setDetectedSourceLanguage(null);
            current.setStatus("PROCESSING");
            current.setLeaseToken(leaseToken);
            current.setLeaseExpiresAt(leaseExpiresAt);
            current.setRetryAfter(null);
            return 1;
        }

        @Override
        public synchronized int markReady(
                String contentType, Long contentId, String sourceField, String targetLanguage,
                byte[] sourceHash, String leaseToken, String translatedText,
                String detectedSourceLanguage, Timestamp now) {
            ContentTranslationCache current = find(contentType, contentId, sourceField, targetLanguage);
            if (current == null || !Arrays.equals(current.getSourceHash(), sourceHash)
                    || !"PROCESSING".equals(current.getStatus())
                    || !leaseToken.equals(current.getLeaseToken())) return 0;
            current.setTranslatedText(translatedText);
            current.setDetectedSourceLanguage(detectedSourceLanguage);
            current.setStatus("READY");
            current.setLeaseToken(null);
            return 1;
        }

        @Override
        public synchronized int markFailed(
                String contentType, Long contentId, String sourceField, String targetLanguage,
                byte[] sourceHash, String leaseToken, Timestamp retryAfter, Timestamp now) {
            ContentTranslationCache current = find(contentType, contentId, sourceField, targetLanguage);
            if (current == null || !leaseToken.equals(current.getLeaseToken())) return 0;
            current.setStatus("FAILED");
            current.setTranslatedText(null);
            current.setLeaseToken(null);
            current.setRetryAfter(retryAfter);
            return 1;
        }

        @Override
        public synchronized int upsertReady(ContentTranslationCache cache) {
            cache.setStatus("READY");
            cache.setLeaseToken(null);
            ContentTranslationCache previous = caches.put(key(cache), cache);
            return previous == null ? 1 : 2;
        }

        @Override
        public synchronized List<ContentTranslationCache> findReadyAfter(Long afterId, int limit) {
            return caches.values().stream()
                    .filter(cache -> "READY".equals(cache.getStatus()))
                    .filter(cache -> cache.getId() != null && cache.getId() > afterId)
                    .sorted(Comparator.comparing(ContentTranslationCache::getId))
                    .limit(limit)
                    .toList();
        }

        private ContentKey key(ContentTranslationCache cache) {
            return new ContentKey(cache.getContentType(), cache.getContentId(),
                    cache.getSourceField(), cache.getTargetLanguage());
        }
    }

    private record SharedKey(
            String hash, String source, String target, String provider, String profile) { }

    private static final class FakeSharedCacheMapper implements SharedTranslationCacheMapper {
        private final Map<SharedKey, SharedTranslationCache> caches = new ConcurrentHashMap<>();

        @Override
        public synchronized SharedTranslationCache find(
                byte[] sourceHash, String sourceLanguage, String targetLanguage,
                String provider, String translationProfile) {
            return caches.get(key(sourceHash, sourceLanguage, targetLanguage, provider, translationProfile));
        }

        @Override
        public synchronized int insertProcessing(SharedTranslationCache cache) {
            SharedKey key = key(cache);
            if (caches.containsKey(key)) return 0;
            cache.setStatus("PROCESSING");
            caches.put(key, cache);
            return 1;
        }

        @Override
        public synchronized int tryClaim(
                byte[] sourceHash, String sourceLanguage, String targetLanguage,
                String provider, String translationProfile, String leaseToken,
                Timestamp now, Timestamp leaseExpiresAt) {
            SharedTranslationCache current = find(
                    sourceHash, sourceLanguage, targetLanguage, provider, translationProfile);
            if (current == null) return 0;
            boolean expired = "PROCESSING".equals(current.getStatus())
                    && (current.getLeaseExpiresAt() == null
                    || !current.getLeaseExpiresAt().after(now));
            boolean retryable = "FAILED".equals(current.getStatus())
                    && (current.getRetryAfter() == null || !current.getRetryAfter().after(now));
            if (!expired && !retryable) return 0;
            current.setStatus("PROCESSING");
            current.setLeaseToken(leaseToken);
            current.setLeaseExpiresAt(leaseExpiresAt);
            current.setRetryAfter(null);
            return 1;
        }

        @Override
        public synchronized int markReady(
                byte[] sourceHash, String sourceLanguage, String targetLanguage,
                String provider, String translationProfile, String leaseToken,
                String translatedText, String detectedSourceLanguage, Timestamp now) {
            SharedTranslationCache current = find(
                    sourceHash, sourceLanguage, targetLanguage, provider, translationProfile);
            if (current == null || !"PROCESSING".equals(current.getStatus())
                    || !leaseToken.equals(current.getLeaseToken())) return 0;
            current.setTranslatedText(translatedText);
            current.setDetectedSourceLanguage(detectedSourceLanguage);
            current.setStatus("READY");
            current.setLeaseToken(null);
            return 1;
        }

        @Override
        public synchronized int markFailed(
                byte[] sourceHash, String sourceLanguage, String targetLanguage,
                String provider, String translationProfile, String leaseToken,
                Timestamp retryAfter, Timestamp now) {
            SharedTranslationCache current = find(
                    sourceHash, sourceLanguage, targetLanguage, provider, translationProfile);
            if (current == null || !leaseToken.equals(current.getLeaseToken())) return 0;
            current.setStatus("FAILED");
            current.setTranslatedText(null);
            current.setLeaseToken(null);
            current.setRetryAfter(retryAfter);
            return 1;
        }

        @Override
        public synchronized int deleteProcessing(
                byte[] sourceHash, String sourceLanguage, String targetLanguage,
                String provider, String translationProfile, String leaseToken) {
            SharedKey key = key(sourceHash, sourceLanguage, targetLanguage, provider, translationProfile);
            SharedTranslationCache current = caches.get(key);
            if (current == null || !"PROCESSING".equals(current.getStatus())
                    || !leaseToken.equals(current.getLeaseToken())) return 0;
            caches.remove(key);
            return 1;
        }

        @Override
        public synchronized int insertReadyIfAbsent(SharedTranslationCache cache) {
            SharedKey key = key(cache);
            if (caches.containsKey(key)) return 0;
            cache.setStatus("READY");
            caches.put(key, cache);
            return 1;
        }

        private SharedKey key(SharedTranslationCache cache) {
            return key(cache.getSourceHash(), cache.getSourceLanguage(), cache.getTargetLanguage(),
                    cache.getProvider(), cache.getTranslationProfile());
        }

        private SharedKey key(byte[] sourceHash, String sourceLanguage, String targetLanguage,
                              String provider, String translationProfile) {
            return new SharedKey(Base64.getEncoder().encodeToString(sourceHash),
                    sourceLanguage, targetLanguage, provider, translationProfile);
        }
    }
}
