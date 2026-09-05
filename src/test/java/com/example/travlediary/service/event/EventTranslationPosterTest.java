package com.example.travlediary.service.event;

import com.example.travlediary.dto.EventForm;
import com.example.travlediary.dto.EventTranslationForm;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventTranslation;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.event.EventMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 언어별 인포그래픽 포스터의 업로드·교체·삭제를 본다.
 *
 * <p>줄의 생명주기는 최종 세 값(title / description / poster_img)으로 정해지고,
 * 파일 삭제 대상은 언제나 그 언어 줄에 저장돼 있던 DB 값 하나뿐이다.
 */
@ExtendWith(MockitoExtension.class)
class EventTranslationPosterTest {

    private static final Long EVENT_ID = 10L;
    private static final String BASE_TITLE = "여름 여행 이벤트";
    private static final String BASE_DESCRIPTION = "이벤트 상세 설명";
    private static final String BASE_POSTER = "/uploads/events/posters/base.png";
    private static final String OLD_EN_POSTER = "/uploads/events/posters/old-en.png";
    private static final String OLD_JA_POSTER = "/uploads/events/posters/old-ja.png";
    private static final String NEW_POSTER = "/uploads/events/posters/new-en.png";

    @TempDir Path uploadDir;

    @Mock private EventMapper eventMapper;
    @Mock private FileUploadService fileUploadService;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventMapper, fileUploadService);
        ReflectionTestUtils.setField(eventService, "uploadDir", uploadDir.toString());
    }

    /* === 신규 등록 === */

    @Test
    void posterOnlyForeignRowIsInsertedEvenWithoutAnyText() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER)));
        when(fileUploadService.saveFile(any(), eq("events/posters"))).thenReturn(NEW_POSTER);

        save(List.of(withPoster(form("en", "", "  "), image("en-poster.png"))));

        EventTranslation inserted = insertOf("en");
        assertThat(inserted.getTitle()).isNull();
        assertThat(inserted.getDescription()).isNull();
        assertThat(inserted.getPosterImg()).isEqualTo(NEW_POSTER);
    }

    @Test
    void textAndPosterAreInsertedTogether() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER)));
        when(fileUploadService.saveFile(any(), eq("events/posters"))).thenReturn(NEW_POSTER);

        save(List.of(withPoster(
                form("en", "English title", "English body"), image("en-poster.png"))));

        EventTranslation inserted = insertOf("en");
        assertThat(inserted.getTitle()).isEqualTo("English title");
        assertThat(inserted.getDescription()).isEqualTo("English body");
        assertThat(inserted.getPosterImg()).isEqualTo(NEW_POSTER);
    }

    @Test
    void createSavesTheForeignPosterForTheNewlyGeneratedEventId() {
        when(eventMapper.insert(any(Event.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Event.class).setId(21L);
            return 1;
        });
        when(fileUploadService.saveFile(any(), eq("events/posters")))
                .thenReturn(BASE_POSTER, NEW_POSTER);

        EventForm form = infographicForm();
        withPoster(form.getTranslations().get(1), image("en-poster.png"));

        eventService.create(form, 7L);

        EventTranslation english = insertOf("en");
        assertThat(english.getEventId()).isEqualTo(21L);
        assertThat(english.getPosterImg()).isEqualTo(NEW_POSTER);
    }

    /* === 수정: 업로드 없음 === */

    @Test
    void savingTextWithoutAFileKeepsTheStoredPoster() throws IOException {
        Path storedFile = storedFile("old-en.png");
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", "English title", "English body", OLD_EN_POSTER)));

        save(List.of(form("en", "새 제목", "새 본문")));

        assertThat(updateOf("en").getPosterImg()).isEqualTo(OLD_EN_POSTER);
        assertThat(storedFile).exists();
        verify(fileUploadService, never()).saveFile(any(), anyString());
    }

    /* === 교체 === */

    @Test
    void uploadingAFileReplacesThePathAndDeletesOnlyThatLanguageFile() throws IOException {
        Path oldEnglish = storedFile("old-en.png");
        Path oldJapanese = storedFile("old-ja.png");
        Path basePoster = storedFile("base.png");
        Path newFile = storedFile("new-en.png");

        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", "English title", "English body", OLD_EN_POSTER),
                translation("ja", "日本語タイトル", "日本語本文", OLD_JA_POSTER)));
        when(fileUploadService.saveFile(any(), eq("events/posters"))).thenReturn(NEW_POSTER);

        save(List.of(
                withPoster(form("en", "English title", "English body"), image("en-poster.png")),
                form("ja", "日本語タイトル", "日本語本文")));

        assertThat(updateOf("en").getPosterImg()).isEqualTo(NEW_POSTER);
        // 다른 언어와 base 는 값도 파일도 그대로다.
        assertThat(updateOf("ja").getPosterImg()).isEqualTo(OLD_JA_POSTER);
        assertThat(updateOf("ko").getPosterImg()).isEqualTo(BASE_POSTER);
        assertThat(oldEnglish).doesNotExist();
        assertThat(oldJapanese).exists();
        assertThat(basePoster).exists();
        assertThat(newFile).exists();
    }

    @Test
    void aFailedDatabaseWriteRemovesOnlyTheFileItJustUploaded() throws IOException {
        Path oldEnglish = storedFile("old-en.png");
        Path newFile = storedFile("new-en.png");

        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", "English title", "English body", OLD_EN_POSTER)));
        when(fileUploadService.saveFile(any(), eq("events/posters"))).thenReturn(NEW_POSTER);
        when(eventMapper.updateTranslation(any())).thenAnswer(invocation -> {
            EventTranslation translation = invocation.getArgument(0);
            if ("en".equals(translation.getLanguageCode())) {
                throw new IllegalStateException("저장 실패");
            }
            return 1;
        });

        assertThatThrownBy(() -> save(List.of(
                withPoster(form("en", "English title", "English body"), image("en-poster.png")))))
                .isInstanceOf(IllegalStateException.class);

        // 예전 파일은 아직 DB 가 가리키고 있으므로 남고, 쓰이지 못한 새 파일만 지운다.
        assertThat(oldEnglish).exists();
        assertThat(newFile).doesNotExist();
    }

    /* === 삭제 === */

    @Test
    void removePosterClearsTheColumnAndDeletesTheStoredFile() throws IOException {
        Path oldEnglish = storedFile("old-en.png");
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", "English title", "English body", OLD_EN_POSTER)));

        save(List.of(removingPoster(form("en", "English title", "English body"))));

        // 글자가 남아 있으므로 줄은 살아 있고 포스터만 비워진다.
        assertThat(updateOf("en").getPosterImg()).isNull();
        assertThat(updateOf("en").getTitle()).isEqualTo("English title");
        assertThat(oldEnglish).doesNotExist();
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void removingThePosterOfARowWithoutTextDeletesTheRow() throws IOException {
        Path oldEnglish = storedFile("old-en.png");
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", null, null, OLD_EN_POSTER)));

        save(List.of(removingPoster(form("en", "", "  "))));

        verify(eventMapper).deleteTranslation(EVENT_ID, "en");
        assertThat(oldEnglish).doesNotExist();
    }

    @Test
    void blankTextAloneStillKeepsARowThatHasAStoredPoster() throws IOException {
        Path oldEnglish = storedFile("old-en.png");
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", "English title", "English body", OLD_EN_POSTER)));

        save(List.of(form("en", "", "")));

        assertThat(updateOf("en").getTitle()).isNull();
        assertThat(updateOf("en").getPosterImg()).isEqualTo(OLD_EN_POSTER);
        assertThat(oldEnglish).exists();
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    /* === 삭제 체크 + 새 파일 동시 === */

    @Test
    void aNewUploadWinsOverTheRemoveCheckbox() throws IOException {
        Path oldEnglish = storedFile("old-en.png");
        Path newFile = storedFile("new-en.png");
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", "English title", "English body", OLD_EN_POSTER)));
        when(fileUploadService.saveFile(any(), eq("events/posters"))).thenReturn(NEW_POSTER);

        EventTranslationForm form = removingPoster(form("en", "English title", "English body"));
        save(List.of(withPoster(form, image("en-poster.png"))));

        // 새 파일을 저장했다가 곧 지우는 이상 동작이 없어야 한다.
        assertThat(updateOf("en").getPosterImg()).isEqualTo(NEW_POSTER);
        assertThat(newFile).exists();
        assertThat(oldEnglish).doesNotExist();
        verify(eventMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    /* === 한국어 / base 동기화 === */

    @Test
    void koreanPosterAlwaysFollowsTheBaseEventPoster() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean("/uploads/events/posters/stale-ko.png")));

        // ko 슬롯에 파일과 삭제 체크가 실려 와도 한국어 포스터는 base 를 따른다.
        save(List.of(withPoster(removingPoster(form("ko", "폼 한국어", "폼 본문")),
                image("ko-poster.png"))));

        assertThat(updateOf("ko").getPosterImg()).isEqualTo(BASE_POSTER);
        verify(fileUploadService, never()).saveFile(any(), anyString());
    }

    @Test
    void replacingTheBasePosterSyncsTheKoreanTranslationRow() {
        Event existing = existingInfographicEvent();
        when(eventMapper.selectEventById(EVENT_ID)).thenReturn(existing);
        when(eventMapper.updateEvent(existing)).thenReturn(1);
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean("/uploads/events/posters/old-base.png")));
        when(fileUploadService.saveFile(any(), eq("events/posters")))
                .thenReturn("/uploads/events/posters/new-base.png");

        EventForm form = infographicForm();
        form.setPosterFile(image("base-poster.png"));

        eventService.update(EVENT_ID, form);

        assertThat(existing.getPosterImg()).isEqualTo("/uploads/events/posters/new-base.png");
        assertThat(updateOf("ko").getPosterImg())
                .isEqualTo("/uploads/events/posters/new-base.png");
    }

    /* === 파일 삭제 안전장치 === */

    @Test
    void theFormCarriesNoPosterPathForTheClientToTamperWith() {
        assertThat(Arrays.stream(EventTranslationForm.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .containsExactlyInAnyOrder(
                        "languageCode", "title", "description", "posterFile", "removePoster");
    }

    @Test
    void onlyTheStoredDatabasePathIsEverDeleted() throws IOException {
        Path oldEnglish = storedFile("old-en.png");
        Path unrelated = storedFile("someone-elses.png");
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", null, null, OLD_EN_POSTER)));

        save(List.of(removingPoster(form("en", "", ""))));

        assertThat(oldEnglish).doesNotExist();
        // DB 가 가리키지 않는 파일은 어떤 경우에도 손대지 않는다.
        assertThat(unrelated).exists();
    }

    @Test
    void deletingAnEventAlsoRemovesTheStoredForeignPosterFiles() throws IOException {
        Path basePoster = storedFile("base.png");
        Path oldEnglish = storedFile("old-en.png");
        Event event = existingInfographicEvent();
        event.setPosterImg(BASE_POSTER);
        when(eventMapper.selectEventById(EVENT_ID)).thenReturn(event);
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                korean(BASE_POSTER),
                translation("en", "English title", null, OLD_EN_POSTER)));

        eventService.deleteEventById(EVENT_ID);

        assertThat(basePoster).doesNotExist();
        assertThat(oldEnglish).doesNotExist();
        verify(eventMapper).deleteEventById(EVENT_ID);
    }

    /* === 수정 화면 preload === */

    @Test
    void previewPathsAreLookedUpByLanguageCodeNotByRowOrder() {
        when(eventMapper.findTranslationsByEventId(EVENT_ID)).thenReturn(List.of(
                translation("zh-TW", "繁體標題", null, "/uploads/events/posters/zh-tw.png"),
                korean(BASE_POSTER),
                translation("en", "English title", null, OLD_EN_POSTER),
                translation("ja", "日本語タイトル", null, "   ")));

        Map<String, String> posters = eventService.getTranslationPosterImages(EVENT_ID);

        assertThat(posters)
                .containsEntry("en", OLD_EN_POSTER)
                .containsEntry("zh-TW", "/uploads/events/posters/zh-tw.png")
                .containsEntry("ko", BASE_POSTER);
        // 값이 비어 있는 언어는 아예 담기지 않아 화면에서 미리보기가 그려지지 않는다.
        assertThat(posters).doesNotContainKey("ja");
        assertThat(eventService.getTranslationPosterImages(null)).isEmpty();
    }

    /* === helpers === */

    private void save(List<EventTranslationForm> forms) {
        eventService.saveTranslations(
                EVENT_ID, BASE_TITLE, BASE_DESCRIPTION, BASE_POSTER, forms);
    }

    private Path storedFile(String name) throws IOException {
        Path directory = uploadDir.resolve("events/posters");
        Files.createDirectories(directory);
        Path file = directory.resolve(name);
        Files.writeString(file, "image");
        return file;
    }

    private EventTranslation insertOf(String languageCode) {
        return capturedOf(languageCode, true);
    }

    private EventTranslation updateOf(String languageCode) {
        return capturedOf(languageCode, false);
    }

    private EventTranslation capturedOf(String languageCode, boolean insert) {
        ArgumentCaptor<EventTranslation> captor =
                ArgumentCaptor.forClass(EventTranslation.class);
        if (insert) {
            verify(eventMapper, atLeastOnce()).insertTranslation(captor.capture());
        } else {
            verify(eventMapper, atLeastOnce()).updateTranslation(captor.capture());
        }
        return captor.getAllValues().stream()
                .filter(translation -> languageCode.equals(translation.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private EventTranslationForm form(String languageCode, String title, String description) {
        return new EventTranslationForm(languageCode, title, description);
    }

    private EventTranslationForm withPoster(EventTranslationForm form, MultipartFile file) {
        form.setPosterFile(file);
        return form;
    }

    private EventTranslationForm removingPoster(EventTranslationForm form) {
        form.setRemovePoster(true);
        return form;
    }

    private EventTranslation korean(String posterImg) {
        return translation("ko", BASE_TITLE, BASE_DESCRIPTION, posterImg);
    }

    private EventTranslation translation(String languageCode, String title,
                                         String description, String posterImg) {
        EventTranslation translation = new EventTranslation();
        translation.setEventId(EVENT_ID);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setDescription(description);
        translation.setPosterImg(posterImg);
        return translation;
    }

    private EventForm infographicForm() {
        EventForm form = new EventForm();
        form.setTitle(BASE_TITLE);
        form.setEventType(EventType.INFOGRAPHIC);
        form.setDescription(BASE_DESCRIPTION);
        form.setSlide(false);
        form.setStartDate(LocalDate.of(2026, 8, 1));
        form.setEndDate(LocalDate.of(2026, 8, 31));
        form.setPosterFile(image("poster.png"));
        return form;
    }

    private Event existingInfographicEvent() {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setTitle("기존 이벤트");
        event.setDescription("기존 설명");
        event.setEventType(EventType.INFOGRAPHIC);
        event.setPosterImg("/uploads/events/posters/old-base.png");
        event.setSlide(false);
        event.setUserId(3L);
        event.setStartDate(LocalDate.of(2026, 7, 1));
        event.setEndDate(LocalDate.of(2026, 7, 31));
        return event;
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("posterFile", name, "image/png", new byte[]{1, 2, 3});
    }
}
