package com.tripbora.service.destination;

import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.DestinationTranslationForm;
import com.tripbora.model.Destination;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.model.DestinationTranslationSource;
import com.tripbora.repository.destination.DestinationTranslationSourceMapper;
import com.tripbora.config.i18n.SupportedLanguage;
import com.tripbora.service.wikidata.WikidataRegistrationService;
import org.springframework.test.util.ReflectionTestUtils;
import com.tripbora.model.DestinationType;
import com.tripbora.repository.bookmark.BookmarkMapper;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.amenity.AmenityService;
import com.tripbora.service.comment.DestinationCommentService;
import com.tripbora.service.course.CourseService;
import com.tripbora.service.info.AccommodationInfoService;
import com.tripbora.service.info.ActivityInfoService;
import com.tripbora.service.info.AttractionInfoService;
import com.tripbora.service.info.RestaurantInfoService;
import com.tripbora.service.info.ShopInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 여행지 기본정보 번역 슬롯 저장 정책.
 *
 * <p>한국어는 원본이라 늘 저장하고, 나머지 언어는 값이 있는 언어만 줄을 남긴다.
 * 비운 언어는 새로 만들지 않고, 있던 줄이면 그 언어만 지운다. (유형별 상세정보와 같은 규칙)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationTranslationSlotSaveTest {

    @Mock private DestinationMapper destinationMapper;
    @Mock private DestinationTranslationSourceMapper sourceMapper;
    @Mock private DestinationImageService destinationImageService;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private AmenityService amenityService;
    @Mock private DestinationCommentService destinationCommentService;
    @Mock private CourseService courseService;
    @Mock private AccommodationInfoService accommodationInfoService;
    @Mock private AttractionInfoService attractionInfoService;
    @Mock private RestaurantInfoService restaurantInfoService;
    @Mock private ActivityInfoService activityInfoService;
    @Mock private ShopInfoService shopInfoService;

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        destinationService = new DestinationService(destinationMapper, destinationImageService,
                bookmarkMapper, amenityService, destinationCommentService, courseService,
                accommodationInfoService, attractionInfoService, restaurantInfoService,
                activityInfoService, shopInfoService,
                new DestinationLocalizationService(destinationMapper));
        ReflectionTestUtils.setField(destinationService, "translationSourceMapper", sourceMapper);
    }

    @Test
    void editingWikipediaDescriptionKeepsOriginalRevisionAndUpdatesModifiedFlag() {
        givenExistingDestination();
        DestinationTranslation korean = stored("ko", "에펠탑");
        korean.setId(20L);
        korean.setDescription("원문");
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of(korean));
        DestinationTranslationSource source = new DestinationTranslationSource();
        source.setOriginalContentSha256(WikidataRegistrationService.sha256("원문"));
        source.setSourceRevisionId(123L);
        when(sourceMapper.findByTranslationId(20L)).thenReturn(source);
        DestinationForm form = form();
        fill(form, "ko", "에펠탑", "", "관리자 수정");

        destinationService.updateDestination(9L, form);

        verify(sourceMapper).updateModified(20L, true);
        assertThat(source.getSourceRevisionId()).isEqualTo(123L);
        verify(sourceMapper, never()).deleteByTranslationId(20L);
    }

    @Test
    void deletingDescriptionRemovesOnlyItsWikipediaSource() {
        givenExistingDestination();
        DestinationTranslation korean = stored("ko", "에펠탑");
        korean.setId(20L);
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of(korean));
        when(sourceMapper.findByTranslationId(20L)).thenReturn(new DestinationTranslationSource());
        DestinationForm form = form();
        fill(form, "ko", "에펠탑", "", "");

        destinationService.updateDestination(9L, form);

        verify(sourceMapper).deleteByTranslationId(20L);
    }

    @Test
    void explicitUnlinkRemovesSourceButPreservesEditedDescription() {
        givenExistingDestination();
        DestinationTranslation korean = stored("ko", "에펠탑");
        korean.setId(20L);
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of(korean));
        when(sourceMapper.findByTranslationId(20L)).thenReturn(new DestinationTranslationSource());
        DestinationForm form = form();
        fill(form, "ko", "에펠탑", "", "관리자 직접 설명");
        form.setWikipediaUnlinkLanguages(List.of("ko"));

        destinationService.updateDestination(9L, form);

        verify(sourceMapper).deleteByTranslationId(20L);
        assertThat(updatedTranslations().get(0).getDescription()).isEqualTo("관리자 직접 설명");
    }

    @Test
    void displayedFallbackUsesSourceOfActuallyDisplayedDescription() {
        DestinationTranslation korean = stored("ko", "에펠탑");
        korean.setId(20L);
        korean.setDescription("한국어 소개");
        DestinationTranslation english = stored("en", "Eiffel Tower");
        english.setId(21L);
        english.setDescription("");
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of(english, korean));
        DestinationTranslationSource source = new DestinationTranslationSource();
        when(sourceMapper.findByTranslationId(20L)).thenReturn(source);

        assertThat(destinationService.findDisplayedWikipediaSource(9L, SupportedLanguage.ENGLISH,
                "한국어 소개")).isSameAs(source);
        verify(sourceMapper, never()).findByTranslationId(21L);
    }

    @Test
    void registrationSkipsLanguagesLeftEmpty() {
        givenGeneratedId(9L);
        DestinationForm form = form();
        fill(form, "en", "Gyeongbokgung Palace", "English summary", "English description");
        // ja 는 공백만, zh-CN / zh-TW 는 그대로 빈 칸

        destinationService.registerDestination(form, 7L);

        assertThat(insertedTranslations())
                .extracting(DestinationTranslation::getLanguageCode,
                        DestinationTranslation::getName)
                .containsExactly(
                        tuple("ko", "경복궁"),
                        tuple("en", "Gyeongbokgung Palace"));
    }

    @Test
    void wikidataRegistrationUsesValidatedSourceTypeAndQid() {
        givenGeneratedId(9L);
        DestinationForm form = form();

        destinationService.registerDestination(form, 7L, "WIKIDATA", "Q243");

        ArgumentCaptor<Destination> captured = ArgumentCaptor.forClass(Destination.class);
        verify(destinationMapper).insertDestination(captured.capture());
        assertThat(captured.getValue().getSourceType()).isEqualTo("WIKIDATA");
        assertThat(captured.getValue().getExternalContentId()).isEqualTo("Q243");
    }

    @Test
    void oneFilledFieldIsEnoughToKeepTheLanguage() {
        givenGeneratedId(9L);
        DestinationForm form = form();
        fill(form, "ja", "", "  ", "日本語の説明");   // 상세 설명만 입력

        destinationService.registerDestination(form, 7L);

        assertThat(insertedTranslations())
                .extracting(DestinationTranslation::getLanguageCode)
                .containsExactly("ko", "ja");
        assertThat(insertedTranslations().stream()
                .filter(translation -> "ja".equals(translation.getLanguageCode()))
                .findFirst().orElseThrow().getDescription()).isEqualTo("日本語の説明");
    }

    @Test
    void clearingEveryFieldDeletesOnlyThatLanguage() {
        givenExistingDestination();
        when(destinationMapper.findTranslationsByDestinationId(9L)).thenReturn(List.of(
                stored("ko", "경복궁"), stored("en", "Gyeongbokgung Palace"),
                stored("ja", "景福宮")));

        DestinationForm form = form();
        fill(form, "en", "Gyeongbokgung Palace", "English summary", "English description");
        fill(form, "ja", "   ", "", null);   // 일본어만 비운다

        destinationService.updateDestination(9L, form);

        verify(destinationMapper).deleteTranslation(9L, "ja");
        verify(destinationMapper, never()).deleteTranslation(9L, "en");
        verify(destinationMapper, never()).deleteTranslation(9L, "ko");
        // 남은 언어는 예전처럼 갱신된다
        assertThat(updatedTranslations())
                .extracting(DestinationTranslation::getLanguageCode)
                .containsExactly("ko", "en");
        verify(destinationMapper, never()).insertTranslation(any());
    }

    @Test
    void anEmptyLanguageWithoutAStoredRowIsLeftAlone() {
        givenExistingDestination();
        when(destinationMapper.findTranslationsByDestinationId(9L))
                .thenReturn(List.of(stored("ko", "경복궁")));

        destinationService.updateDestination(9L, form());

        verify(destinationMapper, never()).deleteTranslation(any(), any());
        verify(destinationMapper, never()).insertTranslation(any());
        assertThat(updatedTranslations())
                .extracting(DestinationTranslation::getLanguageCode).containsExactly("ko");
    }

    @Test
    void koreanIsAlwaysKeptEvenWhenTheAdminLeavesItEmpty() {
        givenGeneratedId(9L);
        DestinationForm form = form();
        fill(form, "ko", "", "", "");

        destinationService.registerDestination(form, 7L);

        assertThat(insertedTranslations())
                .extracting(DestinationTranslation::getLanguageCode).containsExactly("ko");
    }

    @Test
    void aNewlyFilledLanguageIsInsertedOnUpdate() {
        givenExistingDestination();
        when(destinationMapper.findTranslationsByDestinationId(9L))
                .thenReturn(List.of(stored("ko", "경복궁")));

        DestinationForm form = form();
        fill(form, "zh-TW", "景福宮", "", "");

        destinationService.updateDestination(9L, form);

        assertThat(insertedTranslations())
                .extracting(DestinationTranslation::getLanguageCode,
                        DestinationTranslation::getName)
                .containsExactly(tuple("zh-TW", "景福宮"));
        verify(destinationMapper, never()).deleteTranslation(any(), any());
    }

    private void givenGeneratedId(Long id) {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Destination.class).setId(id);
            return null;
        }).when(destinationMapper).insertDestination(any(Destination.class));
    }

    private void givenExistingDestination() {
        Destination stored = new Destination();
        stored.setId(9L);
        when(destinationMapper.findById(9L)).thenReturn(stored);
        when(destinationMapper.findCategoryIdsByDestinationId(9L)).thenReturn(List.of());
    }

    private DestinationForm form() {
        DestinationForm form = new DestinationForm();
        form.setType(DestinationType.ATTRACTION);
        form.setRegionId(235L);
        form.setSeason("SPRING");
        form.setLatitude(BigDecimal.ONE);
        form.setLongitude(BigDecimal.ONE);
        fill(form, "ko", "경복궁", "한국어 요약", "한국어 설명");
        return form;
    }

    private void fill(DestinationForm form, String languageCode,
                      String name, String shortDescription, String description) {
        for (DestinationTranslationForm slot : form.getTranslations()) {
            if (slot.getLanguageCode().equals(languageCode)) {
                slot.setName(name);
                slot.setShortDescription(shortDescription);
                slot.setDescription(description);
            }
        }
    }

    private List<DestinationTranslation> insertedTranslations() {
        ArgumentCaptor<DestinationTranslation> captor =
                ArgumentCaptor.forClass(DestinationTranslation.class);
        verify(destinationMapper, org.mockito.Mockito.atLeast(0))
                .insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private List<DestinationTranslation> updatedTranslations() {
        ArgumentCaptor<DestinationTranslation> captor =
                ArgumentCaptor.forClass(DestinationTranslation.class);
        verify(destinationMapper, org.mockito.Mockito.atLeast(0))
                .updateTranslation(captor.capture());
        return captor.getAllValues();
    }

    private DestinationTranslation stored(String languageCode, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(9L);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }
}
