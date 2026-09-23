package com.example.travlediary.service.wikidata;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikidataDestinationServiceTest {

    @Mock private WikidataApiClient apiClient;
    @Mock private CountryCategoryService countryCategoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WikidataDestinationService service;

    @BeforeEach
    void setUp() {
        service = new WikidataDestinationService(apiClient, countryCategoryService);
    }

    @Test
    void koreanSearchKeepsOnlyPlaceCandidatesAndShowsQidCountryAndImage() throws Exception {
        when(apiClient.searchIds("에펠탑", "ko")).thenReturn(List.of("Q243", "Q3821251"));
        stubEntities(Map.of(
                "Q243", entity("""
                        {"id":"Q243","labels":{"ko":{"value":"에펠탑"},"en":{"value":"Eiffel Tower"}},
                         "descriptions":{"ko":{"value":"프랑스 파리 마르스 광장의 건축물"}},
                         "claims":{"P17":[{"mainsnak":{"datavalue":{"value":{"id":"Q142"}}}}],
                                   "P131":[{"mainsnak":{"datavalue":{"value":{"id":"Q259463"}}}}],
                                   "P625":[{"mainsnak":{"datavalue":{"value":{"latitude":48.858296,"longitude":2.294479}}}}],
                                   "P18":[{"mainsnak":{"datavalue":{"value":"Tour Eiffel Wikimedia Commons.jpg"}}}]}}
                        """),
                "Q3821251", entity("{" + "\"id\":\"Q3821251\",\"labels\":{\"en\":{\"value\":\"The Eiffel Tower\"}}}"),
                "Q142", entity("{" + "\"id\":\"Q142\",\"labels\":{\"ko\":{\"value\":\"프랑스\"}}}"),
                "Q259463", entity("{" + "\"id\":\"Q259463\",\"labels\":{\"ko\":{\"value\":\"파리 7구\"}}}")));

        var candidates = service.search("에펠탑");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).qid()).isEqualTo("Q243");
        assertThat(candidates.get(0).name()).isEqualTo("에펠탑");
        assertThat(candidates.get(0).shortDescription()).isEqualTo("프랑스 파리 마르스 광장의 건축물");
        assertThat(candidates.get(0).country()).isEqualTo("프랑스");
        assertThat(candidates.get(0).region()).isEqualTo("파리 7구");
        assertThat(candidates.get(0).imageFileName()).isEqualTo("Tour Eiffel Wikimedia Commons.jpg");
        assertThat(candidates.get(0).imageUrl()).startsWith("https://commons.wikimedia.org/wiki/Special:FilePath/");
    }

    @Test
    void previewKeepsMissingLanguageEmptyAndMatchesParisOnlyThroughItsCountry() throws Exception {
        stubEntities(Map.of(
                "Q243", entity("""
                        {"id":"Q243","labels":{"ko":{"value":"에펠탑"},"en":{"value":"Eiffel Tower"},
                         "ja":{"value":"エッフェル塔"},"zh-hans":{"value":"埃菲尔铁塔"}},
                         "descriptions":{"ko":{"value":"프랑스 파리 마르스 광장의 건축물"},
                         "en":{"value":"tower located in Paris"}},
                         "claims":{"P17":[{"mainsnak":{"datavalue":{"value":{"id":"Q142"}}}}],
                                   "P131":[{"mainsnak":{"datavalue":{"value":{"id":"Q259463"}}}}],
                                   "P625":[{"mainsnak":{"datavalue":{"value":{"latitude":48.858296,"longitude":2.294479}}}}],
                                   "P18":[{"mainsnak":{"datavalue":{"value":"Tour Eiffel Wikimedia Commons.jpg"}}}]}}
                        """),
                "Q142", entity("{" + "\"id\":\"Q142\",\"labels\":{\"ko\":{\"value\":\"프랑스\"},\"en\":{\"value\":\"France\"}}}"),
                "Q259463", entity("{" + "\"id\":\"Q259463\",\"labels\":{\"ko\":{\"value\":\"파리 7구\"}},\"claims\":{\"P131\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q90\"}}}}]}}"),
                "Q90", entity("{" + "\"id\":\"Q90\",\"labels\":{\"ko\":{\"value\":\"파리\"},\"en\":{\"value\":\"Paris\"}}}")));
        when(countryCategoryService.getRegionsByDepth(2)).thenReturn(List.of(category(17L, null, "프랑스", "France")));
        when(countryCategoryService.getRegionsByParentId(17L)).thenReturn(List.of(category(106L, 17L, "파리", "Paris")));

        var preview = service.preview("Q243");

        assertThat(preview.names()).containsEntry("ko", "에펠탑").containsEntry("en", "Eiffel Tower")
                .containsEntry("ja", "エッフェル塔").containsEntry("zh-CN", "埃菲尔铁塔")
                .doesNotContainKey("zh-TW");
        assertThat(preview.shortDescriptions()).containsOnlyKeys("ko", "en");
        assertThat(preview.regionPath()).containsExactly("파리 7구", "파리");
        assertThat(preview.latitude()).isEqualTo(48.858296);
        assertThat(preview.longitude()).isEqualTo(2.294479);
        assertThat(preview.regionMatch().matched()).isTrue();
        assertThat(preview.regionMatch().countryId()).isEqualTo(17L);
        assertThat(preview.regionMatch().regionId()).isEqualTo(106L);
    }

    @Test
    void missingLocationAndImageStayMissingAndRequireAdminReview() throws Exception {
        stubEntities(Map.of("Q999", entity("{" + "\"id\":\"Q999\",\"labels\":{\"en\":{\"value\":\"Unnamed site\"}}}")));

        var preview = service.preview("Q999");

        assertThat(preview.names()).containsOnlyKeys("en");
        assertThat(preview.country()).isNull();
        assertThat(preview.latitude()).isNull();
        assertThat(preview.imageUrl()).isNull();
        assertThat(preview.regionMatch().matched()).isFalse();
        verify(countryCategoryService, never()).getRegionsByDepth(2);
    }

    @Test
    void fallbackLanguageIsNotPresentedAsAnOriginalTranslation() throws Exception {
        stubEntities(Map.of("Q999", entity("""
                {"id":"Q999","labels":{"en":{"language":"en","value":"Unnamed site"},
                 "zh-hant":{"language":"zh-hans","value":"简体名称"}},
                 "descriptions":{"zh-hant":{"language":"zh-hans","value":"简体说明"}}}
                """)));

        var preview = service.preview("Q999");

        assertThat(preview.names()).containsOnlyKeys("en");
        assertThat(preview.shortDescriptions()).doesNotContainKey("zh-TW");
    }

    @Test
    void emptySearchIsDistinctFromApiFailure() {
        when(apiClient.searchIds("Unknown place", "en")).thenReturn(List.of());

        assertThat(service.search("Unknown place")).isEmpty();
        verify(apiClient, never()).getEntities(anyList(), anyBoolean());
    }

    @Test
    void malformedQidIsRejectedBeforeCallingWikidata() {
        assertThatThrownBy(() -> service.preview("Q243|Q9202"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(apiClient, never()).getEntities(anyList(), anyBoolean());
    }

    private void stubEntities(Map<String, JsonNode> entities) {
        when(apiClient.getEntities(anyList(), anyBoolean())).thenAnswer(invocation -> {
            Map<String, JsonNode> found = new HashMap<>();
            for (String qid : invocation.<List<String>>getArgument(0)) {
                if (entities.containsKey(qid)) {
                    found.put(qid, entities.get(qid));
                }
            }
            return found;
        });
    }

    private JsonNode entity(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private CountryCategory category(Long id, Long parentId, String korean, String english) {
        CountryCategory category = new CountryCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setRegionName(korean);
        category.setNameEn(english);
        return category;
    }
}
