package com.tripbora.service.wikidata;

import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.wikidata.WikidataDestinationPreview;
import com.tripbora.dto.wikidata.WikipediaDescriptionPreview;
import com.tripbora.dto.wikidata.WikipediaDescriptionPreview.LanguageEntry;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.DestinationType;
import com.tripbora.service.category.CountryCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikidataRegistrationServiceTest {
    @Mock WikidataDestinationService wikidata;
    @Mock WikipediaDescriptionService wikipedia;
    @Mock CountryCategoryService categories;
    @Mock WikidataApiClient api;
    @Mock CommonsPhotoImportService photos;
    WikidataRegistrationService service;
    final com.fasterxml.jackson.databind.node.ObjectNode freshEntity =
            new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("id", "Q243");

    @BeforeEach void setUp() {
        service = new WikidataRegistrationService(wikidata, wikipedia, categories, api, photos);
    }

    @Test void oneFreshEntityIsSharedAndTheThreeChecksRunAtTheSameTime() throws Exception {
        DestinationForm form = form();
        givenValidCandidate();
        form.setWikipediaRevisionIds(Arrays.asList(101L, null, null, null, null));
        form.getTranslations().get(0).setDescription("Text ko");
        var selections = List.of(new CommonsPhotoImportService.Selection("A.jpg", true));
        var prepared = List.of(new PreparedCommonsPhoto("/uploads/destinations/a.jpg", true,
                new com.tripbora.model.DestinationImageCommonsSource()));
        // 세 재검증이 모두 시작돼야 풀리는 문. 순서대로 실행하면 제한 시간 안에 풀리지 않는다.
        java.util.concurrent.CountDownLatch allStarted = new java.util.concurrent.CountDownLatch(3);
        when(wikidata.revalidate("Q243", freshEntity)).thenAnswer(invocation -> {
            allStarted.countDown();
            assertThat(allStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return candidate();
        });
        when(wikipedia.revalidate("Q243", freshEntity)).thenAnswer(invocation -> {
            allStarted.countDown();
            assertThat(allStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return new WikipediaDescriptionPreview("Q243", List.of(entry("ko", "ko", null, 101L)));
        });
        when(photos.prepare("Q243", selections, false, freshEntity)).thenAnswer(invocation -> {
            allStarted.countDown();
            assertThat(allStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return prepared;
        });

        var result = service.prepareRegistration(form, selections, false);

        assertThat(result.sources()).containsOnlyKeys("ko");
        assertThat(result.photos()).isSameAs(prepared);
        org.mockito.Mockito.verify(api, org.mockito.Mockito.times(1)).getAutofillEntities(List.of("Q243"));
        org.mockito.Mockito.verify(photos, org.mockito.Mockito.never()).cleanup(org.mockito.ArgumentMatchers.any());
    }

    @Test void anotherFailedCheckWaitsForDownloadsAndRemovesThem() {
        DestinationForm form = form();
        givenFreshEntityAndRegion();
        var selections = List.of(new CommonsPhotoImportService.Selection("A.jpg", true));
        var prepared = List.of(new PreparedCommonsPhoto("/uploads/destinations/a.jpg", true,
                new com.tripbora.model.DestinationImageCommonsSource()));
        when(wikidata.revalidate("Q243", freshEntity)).thenReturn(new WikidataDestinationPreview("Q243",
                null, null, null, null, List.of(), null, null, null, null, null, null, null, null));
        when(photos.prepare("Q243", selections, false, freshEntity)).thenAnswer(invocation -> {
            Thread.sleep(100);
            return prepared;
        });

        assertThatThrownBy(() -> service.prepareRegistration(form, selections, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Wikidata 후보");
        org.mockito.Mockito.verify(photos).cleanup(prepared);
    }

    @Test void missingEntityOrInvalidFormStopsBeforeAnyRevalidation() {
        DestinationForm form = form();
        givenFreshEntityAndRegion();
        when(api.getAutofillEntities(List.of("Q243"))).thenReturn(java.util.Map.of());
        assertThatThrownBy(() -> service.prepareRegistration(form, List.of(), false))
                .isInstanceOf(java.util.NoSuchElementException.class);

        DestinationForm invalid = form();
        invalid.setSeason("NOT_A_SEASON");
        assertThatThrownBy(() -> service.prepareRegistration(invalid, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("시즌");
        org.mockito.Mockito.verify(api, org.mockito.Mockito.times(1)).getAutofillEntities(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verifyNoInteractions(wikidata, wikipedia, photos);
    }

    @Test void fiveLanguagesKeepTheirOwnVerifiedRevisionAndModifiedHash() {
        DestinationForm form = form();
        givenValidCandidate();
        List<LanguageEntry> entries = List.of(
                entry("ko", "ko", null, 101L), entry("en", "en", null, 102L),
                entry("ja", "ja", null, 103L), entry("zh-CN", "zh", "zh-cn", 104L),
                entry("zh-TW", "zh", "zh-tw", 105L));
        when(wikipedia.revalidate("Q243", freshEntity)).thenReturn(new WikipediaDescriptionPreview("Q243", entries));
        form.setWikipediaRevisionIds(Arrays.asList(101L, 102L, 103L, 104L, 105L));
        for (int i = 0; i < 5; i++) {
            form.getTranslations().get(i).setName("Name " + i);
            form.getTranslations().get(i).setDescription(entries.get(i).description());
        }
        form.getTranslations().get(1).setDescription("Editor changed English");

        var sources = service.prepare(form);

        assertThat(sources.keySet()).containsExactlyInAnyOrder("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(sources.get("zh-CN").getSourceVariant()).isEqualTo("zh-cn");
        assertThat(sources.get("zh-TW").getSourceVariant()).isEqualTo("zh-tw");
        assertThat(sources.get("ko").getSourceUrl()).contains("oldid=101");
        assertThat(sources.get("ko").getOriginalContentSha256()).hasSize(32);
        assertThat(sources.get("ko").isContentModified()).isFalse();
        assertThat(sources.get("en").isContentModified()).isTrue();
    }

    @Test void staleRevisionStopsRegistrationBeforePersistence() {
        DestinationForm form = form();
        givenValidCandidate();
        form.setWikipediaRevisionIds(Arrays.asList(999L, null, null, null, null));
        form.getTranslations().get(0).setDescription("Text");
        when(wikipedia.revalidate("Q243", freshEntity)).thenReturn(new WikipediaDescriptionPreview("Q243",
                List.of(entry("ko", "ko", null, 101L))));

        assertThatThrownBy(() -> service.prepare(form)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("판본");
    }

    @Test void unmatchedCountryRequiresAdministratorRegionSelection() {
        DestinationForm form = form();
        when(categories.getRegionPath(2L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.prepare(form)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("국가·지역");
        // 입력값 검사로 막히면 외부 재검증을 시작하지 않는다.
        org.mockito.Mockito.verifyNoInteractions(api, wikidata, wikipedia);
    }

    @Test void wikipediaRevisionWithoutWikidataCandidateIsRejected() {
        DestinationForm form = new DestinationForm();
        form.setWikipediaRevisionIds(Arrays.asList(101L, null, null, null, null));

        assertThatThrownBy(() -> service.prepare(form)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wikidata 후보");
    }

    private void givenValidCandidate() {
        givenFreshEntityAndRegion();
        org.mockito.Mockito.lenient().when(wikidata.revalidate("Q243", freshEntity)).thenReturn(candidate());
    }

    private void givenFreshEntityAndRegion() {
        org.mockito.Mockito.lenient().when(api.getAutofillEntities(List.of("Q243")))
                .thenReturn(java.util.Map.of("Q243", freshEntity));
        CountryCategory root = new CountryCategory(); root.setId(1L);
        CountryCategory country = new CountryCategory(); country.setId(2L);
        when(categories.getRegionPath(2L)).thenReturn(List.of(root, country));
        when(categories.getOverseasRootIds()).thenReturn(List.of(1L));
    }

    private WikidataDestinationPreview candidate() {
        return new WikidataDestinationPreview("Q243",
                null, null, "Q142", null, null, null, null, null, null, null, null, null, null);
    }

    private DestinationForm form() {
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        form.setType(DestinationType.ATTRACTION);
        form.setSeason("ALL_SEASONS");
        form.setRegionId(2L);
        form.getTranslations().get(0).setName("에펠탑");
        return form;
    }

    private LanguageEntry entry(String language, String sourceLanguage, String variant, long revision) {
        return new LanguageEntry(language, "AVAILABLE", "Title " + language, "Text " + language,
                7, "https://" + sourceLanguage + ".wikipedia.org/wiki/Title",
                sourceLanguage, variant, "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/", revision, null, "Title " + language);
    }
}
