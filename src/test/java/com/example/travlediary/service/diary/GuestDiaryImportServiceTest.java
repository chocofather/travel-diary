package com.example.travlediary.service.diary;

import com.example.travlediary.dto.GuestDiaryImportManifest;
import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryCover;
import com.example.travlediary.model.DiaryCoverElement;
import com.example.travlediary.model.DiaryElement;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.repository.diary.DiaryCoverElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 체험 여행일기를 회원 여행일기로 옮겨 담는 자리의 규칙.
 *
 * <p>여기에 들어오는 값은 사용자가 마음대로 고칠 수 있는 브라우저 저장소에서 온다.
 * 그래서 검사 대부분은 "브라우저가 이미 걸렀을 것" 을 믿지 않는다는 것을 확인한다.
 *
 * <p>소유자는 인자로 받은 로그인 번호 하나로만 정해진다. 보내온 값에는 소유자 칸이 없다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestDiaryImportServiceTest {

    @Mock
    private DiaryService diaryService;
    @Mock
    private DiaryPageService diaryPageService;
    @Mock
    private DiaryElementService diaryElementService;
    @Mock
    private DiaryCoverMapper diaryCoverMapper;
    @Mock
    private DiaryCoverElementMapper diaryCoverElementMapper;
    @Mock
    private FileUploadService fileUploadService;

    private DiaryStickerCatalog stickerCatalog;
    private GuestDiaryImportService service;
    /** 목록 첫 스티커. 값을 적어 두지 않고 실제 목록에서 가져온다. */
    private String knownStickerUrl;

    @BeforeEach
    void setUp() {
        stickerCatalog = new DiaryStickerCatalog();
        stickerCatalog.load();
        knownStickerUrl = stickerCatalog.getCategories().get(0).stickers().get(0).imageUrl();

        DiaryNoteCatalog noteCatalog = new DiaryNoteCatalog();
        noteCatalog.load();
        DiaryLabelFontCatalog fontCatalog = new DiaryLabelFontCatalog();
        fontCatalog.load();

        service = new GuestDiaryImportService(diaryService, diaryPageService,
                diaryElementService, diaryCoverMapper, diaryCoverElementMapper,
                stickerCatalog, noteCatalog, fontCatalog,
                new DiaryContentSanitizer(new PostContentSanitizer()), fileUploadService);
        ReflectionTestUtils.setField(service, "uploadPath", "/tmp/travel-diary-import-test");

        when(diaryService.create(anyLong(), any())).thenAnswer(call -> {
            Diary saved = call.getArgument(1, Diary.class);
            saved.setId(77L);
            return saved;
        });
        when(diaryPageService.append(anyLong(), anyLong(), any())).thenAnswer(call -> {
            DiaryPage saved = call.getArgument(2, DiaryPage.class);
            saved.setId(100L + saved.getPageDate().getDayOfMonth());
            return saved;
        });
        when(diaryCoverMapper.insert(any())).thenAnswer(call -> {
            call.getArgument(0, DiaryCover.class).setId(500L);
            return 1;
        });
        when(diaryCoverElementMapper.insert(any())).thenReturn(1);
        when(fileUploadService.saveImportedDiaryPhoto(any(), anyString()))
                .thenReturn("/uploads/diary-pages/saved.jpg");
    }

    /* ===================== 인증 / 소유자 ===================== */

    /** 2) 소유자는 넘겨받은 로그인 번호로만 정해진다. */
    @Test
    void theDiaryBelongsToTheSignedInMember() {
        service.importDraft(7L, manifest(page(element("NOTE"))), Map.of());

        verify(diaryService).create(eq(7L), any(Diary.class));
        verify(diaryPageService).append(eq(77L), eq(7L), any(DiaryPage.class));
        verify(diaryElementService).create(eq(77L), anyLong(), eq(7L), any(DiaryElement.class));
    }

    /** 1) 로그인 번호가 없으면 아무것도 만들지 않는다. */
    @Test
    void withoutASignedInMemberNothingIsSaved() {
        assertThatThrownBy(() -> service.importDraft(null, manifest(page()), Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(failure -> ((ResponseStatusException) failure).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(diaryService, diaryPageService, diaryElementService);
    }

    /** 3) 보내온 값에는 소유자를 적을 칸 자체가 없다. */
    @Test
    void thereIsNowhereInTheRequestToWriteAnOwner() {
        assertThat(GuestDiaryImportManifest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("userId", "ownerId", "memberId");
        assertThat(GuestDiaryImportManifest.Element.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("userId", "pageId", "id");
    }

    /* ===================== 검증 ===================== */

    /** 5) 가져올 페이지가 하나도 없으면 시작하지 않는다. */
    @Test
    void anEmptyDraftIsRejected() {
        assertThatThrownBy(() -> service.importDraft(7L, manifestOfPages(List.of()), Map.of()))
                .hasMessageContaining("가져올 페이지가 없습니다.");
        assertThatThrownBy(() -> service.importDraft(7L, manifestOfPages(null), Map.of()))
                .hasMessageContaining("가져올 페이지가 없습니다.");
        verifyNoInteractions(diaryService);
    }

    /** 6) 체험은 3장까지다. 브라우저에서 늘려 보내도 서버가 다시 센다. */
    @Test
    void moreThanThreePagesIsRejected() {
        assertThatThrownBy(() -> service.importDraft(7L,
                manifestOfPages(List.of(page(), page(), page(), page())), Map.of()))
                .hasMessageContaining("3장까지");
        verifyNoInteractions(diaryService);
    }

    /** 7) 제목은 비어 있어도, 지나치게 길어도 받지 않는다. */
    @Test
    void aMissingOrOversizedTitleIsRejected() {
        assertThatThrownBy(() -> service.importDraft(7L,
                manifestWithTitle("   "), Map.of()))
                .hasMessageContaining("제목을 입력해 주세요.");
        assertThatThrownBy(() -> service.importDraft(7L,
                manifestWithTitle("가".repeat(151)), Map.of()))
                .hasMessageContaining("제목이 너무 깁니다.");
        verifyNoInteractions(diaryService);
    }

    /** 8) 날짜는 읽을 수 있어야 하고, 끝이 시작보다 빠를 수 없다. */
    @Test
    void brokenTravelDatesAreRejected() {
        assertThatThrownBy(() -> service.importDraft(7L,
                manifestWithDates("2026-13-45", "2026-03-02"), Map.of()))
                .hasMessageContaining("여행 시작일");
        assertThatThrownBy(() -> service.importDraft(7L,
                manifestWithDates("2026-03-05", "2026-03-02"), Map.of()))
                .hasMessageContaining("종료일이 시작일보다 빠릅니다.");
        assertThatThrownBy(() -> service.importDraft(7L,
                manifestWithDates("2026-03-01", null), Map.of()))
                .hasMessageContaining("여행 종료일");
        verifyNoInteractions(diaryService);
    }

    /** 9) 모르는 꾸미기 유형은 받지 않는다. */
    @Test
    void anUnknownElementTypeIsRejected() {
        assertThatThrownBy(() -> service.importDraft(7L,
                manifest(page(element("SCRIPT"))), Map.of()))
                .hasMessageContaining("지원하지 않는 꾸미기 유형입니다.");
        verify(diaryElementService, never()).create(anyLong(), anyLong(), anyLong(), any());
    }

    /** 10) 스티커는 우리 공용 asset 목록에 있는 경로만 그대로 쓴다. */
    @Test
    void onlyStickersFromOurOwnCatalogAreSaved() {
        GuestDiaryImportManifest.Element known =
                elementWith("STICKER", builder -> builder.imageUrl(knownStickerUrl));
        service.importDraft(7L, manifest(page(known)), Map.of());

        ArgumentCaptor<DiaryElement> saved = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(anyLong(), anyLong(), anyLong(), saved.capture());
        assertThat(saved.getValue().getImageUrl()).isEqualTo(knownStickerUrl);

        // 밖에서 끌어오는 주소도, 서버 안의 다른 경로도 스티커가 될 수 없다.
        for (String rejected : List.of("https://example.com/evil.png",
                "/uploads/diary-pages/someone-elses.jpg",
                "/images/diary/stickers/../../../application.yml",
                "/images/diary/stickers/does-not-exist.png")) {
            assertThatThrownBy(() -> service.importDraft(7L, manifest(page(
                    elementWith("STICKER", builder -> builder.imageUrl(rejected)))), Map.of()))
                    .hasMessageContaining("사용할 수 없는 스티커입니다.");
        }
    }

    /** 11) 라벨기 글꼴도 목록에 있는 값만 남는다. */
    @Test
    void anUnknownLabelFontIsRejected() {
        assertThatThrownBy(() -> service.importDraft(7L, manifest(page(
                elementWith("TEXT", builder -> builder.textFont("javascript-font")))), Map.of()))
                .hasMessageContaining("사용할 수 없는 글꼴입니다.");
    }

    /** 12) 표지 요소의 자리·크기·회전·겹침은 DB 가 허용하는 범위 안이어야 한다. */
    @Test
    void coverElementsOutsideTheAllowedRangeAreRejected() {
        record Case(String label, GuestDiaryImportManifest.Element element, String message) {
        }
        List<Case> cases = List.of(
                new Case("자리 밖", coverElement("NOTE", builder ->
                        builder.positionX(new BigDecimal("9"))), "범위를 벗어났습니다"),
                new Case("자리 없음", coverElement("NOTE", builder ->
                        builder.positionY(null)), "확인할 수 없습니다"),
                new Case("너비 0", coverElement("NOTE", builder ->
                        builder.width(BigDecimal.ZERO)), "범위를 벗어났습니다"),
                new Case("높이 1 초과", coverElement("NOTE", builder ->
                        builder.height(new BigDecimal("1.2"))), "범위를 벗어났습니다"),
                new Case("회전 과다", coverElement("NOTE", builder ->
                        builder.rotation(new BigDecimal("720"))), "회전 각도가 범위를 벗어났습니다"),
                new Case("겹침 음수", coverElement("NOTE", builder ->
                        builder.zIndex(-1)), "겹침 순서가 올바르지 않습니다"));

        for (Case each : cases) {
            assertThatThrownBy(() -> service.importDraft(7L,
                    customCoverManifest(each.element()), Map.of()))
                    .as(each.label())
                    .hasMessageContaining(each.message());
        }
    }

    /** 13) 표지 라벨/메모지의 모양과 색도 목록 안에서만 고를 수 있다. */
    @Test
    void unknownCoverNoteDesignsAreRejected() {
        assertThatThrownBy(() -> service.importDraft(7L, customCoverManifest(
                coverElement("NOTE", builder -> builder.styleType("MEMO_HACKED"))), Map.of()))
                .hasMessageContaining("라벨/메모지 디자인을 선택해 주세요.");
        assertThatThrownBy(() -> service.importDraft(7L, customCoverManifest(
                coverElement("NOTE", builder -> builder.colorType("#000000"))), Map.of()))
                .hasMessageContaining("라벨/메모지 색을 선택해 주세요.");
        assertThatThrownBy(() -> service.importDraft(7L, customCoverManifest(
                coverElement("TEXT", builder -> builder.textColor("red"))), Map.of()))
                .hasMessageContaining("글자색을 다시 선택해 주세요.");
        assertThatThrownBy(() -> service.importDraft(7L, customCoverManifest(
                coverElement("TEXT", builder -> builder.textContent("  "))), Map.of()))
                .hasMessageContaining("글씨 내용을 입력해 주세요.");
    }

    /** 14) 쓰겠다고 적은 사진과 실제로 올라온 파일이 맞아야 한다. */
    @Test
    void theDeclaredPhotosAndTheUploadedFilesMustMatchExactly() {
        GuestDiaryImportManifest.Element photo =
                elementWith("PHOTO", builder -> builder.photoRef("ref-1"));

        // 적어 두기만 하고 올리지 않았다.
        assertThatThrownBy(() -> service.importDraft(7L,
                photoManifest(photo, Map.of("ref-1", "photo0")), Map.of()))
                .hasMessageContaining("사진 정보가 맞지 않습니다.");

        // 쓰지도 않는 사진을 함께 올렸다.
        assertThatThrownBy(() -> service.importDraft(7L,
                photoManifest(photo, Map.of("ref-1", "photo0", "ref-2", "photo1")),
                Map.of("photo0", imageFile("photo0"), "photo1", imageFile("photo1"))))
                .hasMessageContaining("사진 정보가 맞지 않습니다.");

        // 두 사진이 같은 파일 칸을 가리킨다.
        GuestDiaryImportManifest.Element other =
                elementWith("PHOTO", builder -> builder.photoRef("ref-2"));
        assertThatThrownBy(() -> service.importDraft(7L,
                photoManifest(List.of(photo, other), Map.of("ref-1", "photo0", "ref-2", "photo0")),
                Map.of("photo0", imageFile("photo0"))))
                .hasMessageContaining("사진 정보가 맞지 않습니다.");

        // 이름표는 맞지만 내용이 빈 파일이다.
        assertThatThrownBy(() -> service.importDraft(7L,
                photoManifest(photo, Map.of("ref-1", "photo0")),
                Map.of("photo0", new MockMultipartFile("photo0", "a.jpg", "image/jpeg", new byte[0]))))
                .hasMessageContaining("사진 원본을 찾을 수 없습니다.");

        verifyNoInteractions(diaryService);
    }

    /** 15) 어떤 사진인지 가리키지 않은 PHOTO 는 저장하지 않는다. */
    @Test
    void aPhotoWithoutAReferenceIsRejected() {
        assertThatThrownBy(() -> service.importDraft(7L,
                manifest(page(elementWith("PHOTO", builder -> builder.photoRef("  ")))), Map.of()))
                .hasMessageContaining("사진 원본을 찾을 수 없습니다.");
    }

    /** 16) 본문은 회원 본문과 같은 규칙으로 걸러 저장한다. */
    @Test
    void thePageContentIsSanitizedLikeAnyMemberContent() {
        GuestDiaryImportManifest.Page page = new GuestDiaryImportManifest.Page(
                "2026-03-01", null, null, null, null, null,
                "<p>여행 첫날<script>alert(1)</script><img src=x onerror=alert(1)></p>", List.of());

        service.importDraft(7L, manifestOfPages(List.of(page)), Map.of());

        ArgumentCaptor<DiaryPage> saved = ArgumentCaptor.forClass(DiaryPage.class);
        verify(diaryPageService).append(anyLong(), anyLong(), saved.capture());
        assertThat(saved.getValue().getContent())
                .contains("여행 첫날")
                .doesNotContain("script", "onerror");
    }

    /** 16) 본문이 지나치게 길면 정리하기 전에 끊는다. */
    @Test
    void anOversizedPageContentIsRejected() {
        GuestDiaryImportManifest.Page page = new GuestDiaryImportManifest.Page(
                "2026-03-01", null, null, null, null, null,
                "가".repeat(60_001), List.of());

        assertThatThrownBy(() -> service.importDraft(7L, manifestOfPages(List.of(page)), Map.of()))
                .hasMessageContaining("본문이 너무 깁니다.");
    }

    /** 한 페이지에 담긴 꾸미기 개수도 서버가 다시 센다. */
    @Test
    void tooManyElementsOnOnePageAreRejected() {
        List<GuestDiaryImportManifest.Element> many = new ArrayList<>();
        for (int index = 0; index < 61; index++) {
            many.add(element("NOTE"));
        }
        assertThatThrownBy(() -> service.importDraft(7L,
                manifestOfPages(List.of(new GuestDiaryImportManifest.Page(
                        "2026-03-01", null, null, null, null, null, null, many))), Map.of()))
                .hasMessageContaining("꾸미기가 너무 많습니다.");
    }

    /* ===================== 저장 ===================== */

    /** 17) 여행일기 한 권. 제목·기간·노트 재질이 그대로 옮겨진다. */
    @Test
    void theDiaryItselfIsCreatedOnce() {
        service.importDraft(7L, manifest(page()), Map.of());

        ArgumentCaptor<Diary> saved = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).create(eq(7L), saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("체험 여행일기");
        assertThat(saved.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(saved.getValue().getEndDate()).isEqualTo(LocalDate.of(2026, 3, 3));
        assertThat(saved.getValue().getNotebookType()).isEqualTo("SPRING");
        // 대표 이미지는 체험에 없다.
        assertThat(saved.getValue().getCoverImageUrl()).isNull();
    }

    /**
     * 한 줄 메모(오늘의 한 줄)도 함께 옮겨진다.
     *
     * <p>장을 만드는 회원 규칙은 날짜·순서·배경·종이색·본문만 받는다. 그래서 한 줄 메모를
     * 그 요청에 실어 보내면 조용히 사라진다. 회원 화면처럼 장을 만든 뒤에 따로 저장한다.
     */
    @Test
    void theOneLineMemoIsCarriedOverWithItsFontAndWeight() {
        service.importDraft(7L, manifestOfPages(List.of(pageWithHeader(
                "테스트 하는중~", "diary-font-handwriting", true))), Map.of());

        verify(diaryPageService).updatePageHeader(
                eq(77L), eq(101L), eq(7L), eq("테스트 하는중~"), eq("diary-font-handwriting"),
                eq(true));
    }

    /** 적어 둔 글이 없으면 한 줄 메모를 저장하지 않는다. (빈 글에 글꼴만 남기지 않는다) */
    @Test
    void anEmptyOneLineMemoIsNotSaved() {
        service.importDraft(7L, manifestOfPages(List.of(pageWithHeader("   ", null, false))),
                Map.of());

        verify(diaryPageService, never()).updatePageHeader(
                anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyBoolean());
    }

    /**
     * 한 줄 메모의 길이·글꼴 판정은 회원 페이지와 같은 규칙이 맡는다.
     * 이 서비스가 따로 검사하거나 기본값을 지어내지 않는다.
     */
    @Test
    void theOneLineMemoIsJudgedByTheSameMemberRule() {
        when(diaryPageService.updatePageHeader(anyLong(), anyLong(), anyLong(),
                anyString(), any(), anyBoolean()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "지원하지 않는 글꼴입니다."));

        assertThatThrownBy(() -> service.importDraft(7L,
                manifestOfPages(List.of(pageWithHeader("메모", "made-up-font", false))), Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("지원하지 않는 글꼴입니다.");
    }

    /** 18) 페이지는 보낸 차례대로 붙는다. 자리 번호를 요청에서 받지 않는다. */
    @Test
    void pagesAreAppendedInTheOrderTheyWereSent() {
        service.importDraft(7L, manifestOfPages(List.of(
                pageOn("2026-03-03"), pageOn("2026-03-01"), pageOn("2026-03-02"))), Map.of());

        ArgumentCaptor<DiaryPage> saved = ArgumentCaptor.forClass(DiaryPage.class);
        verify(diaryPageService, org.mockito.Mockito.times(3))
                .append(eq(77L), eq(7L), saved.capture());
        assertThat(saved.getAllValues())
                .extracting(DiaryPage::getPageDate)
                .containsExactly(LocalDate.of(2026, 3, 3),
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2));
        // 자리 번호는 append 가 정한다. 여기에서 정해 보내지 않는다.
        assertThat(saved.getAllValues()).extracting(DiaryPage::getPageOrder)
                .containsOnlyNulls();
    }

    /** 19) 꾸미기는 자기 페이지에만 붙는다. */
    @Test
    void everyElementIsSavedOnItsOwnPage() {
        service.importDraft(7L, manifestOfPages(List.of(
                new GuestDiaryImportManifest.Page("2026-03-01", null, null, null, null, null,
                        null, List.of(element("NOTE"))),
                new GuestDiaryImportManifest.Page("2026-03-02", null, null, null, null, null,
                        null, List.of(element("NOTE"), element("TEXT"))))), Map.of());

        ArgumentCaptor<Long> pageIds = ArgumentCaptor.forClass(Long.class);
        verify(diaryElementService, org.mockito.Mockito.times(3))
                .create(eq(77L), pageIds.capture(), eq(7L), any());
        assertThat(pageIds.getAllValues()).containsExactly(101L, 102L, 102L);
    }

    /** 20) 꾸민 표지는 이 여행일기의 표지로 저장된다. */
    @Test
    void aCustomCoverBecomesThisDiarysCover() {
        service.importDraft(7L, customCoverManifest(
                coverElement("TEXT", builder -> builder.textContent("제주"))), Map.of());

        ArgumentCaptor<DiaryCover> cover = ArgumentCaptor.forClass(DiaryCover.class);
        verify(diaryCoverMapper).insert(cover.capture());
        assertThat(cover.getValue().getDiaryId()).isEqualTo(77L);
        assertThat(cover.getValue().getBaseCoverStyle()).isEqualTo("DEFAULT");
        assertThat(cover.getValue().getBackgroundColor()).isEqualTo("#ffd6e4");

        ArgumentCaptor<DiaryCoverElement> element =
                ArgumentCaptor.forClass(DiaryCoverElement.class);
        verify(diaryCoverElementMapper).insert(element.capture());
        assertThat(element.getValue().getCoverId()).isEqualTo(500L);
        assertThat(element.getValue().getTextContent()).isEqualTo("제주");
    }

    /** 21) 기본 표지를 쓰던 체험 여행일기는 표지 행을 만들지 않는다. (회원과 같은 규칙) */
    @Test
    void aPresetCoverStaysAPresetCoverWithNoCoverRow() {
        GuestDiaryImportManifest manifest = new GuestDiaryImportManifest(
                "체험 여행일기", "2026-03-01", "2026-03-03", "SPRING",
                "PRESET", "KRAFT", null, List.of(page()), Map.of());

        service.importDraft(7L, manifest, Map.of());

        ArgumentCaptor<Diary> saved = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).create(eq(7L), saved.capture());
        assertThat(saved.getValue().getCoverStyle()).isEqualTo("KRAFT");
        verifyNoInteractions(diaryCoverMapper, diaryCoverElementMapper);
    }

    /** 21) 꾸민 표지를 떼면 돌아갈 자리도 함께 적어 둔다. */
    @Test
    void aCustomCoverStillRecordsTheStyleToFallBackTo() {
        service.importDraft(7L, customCoverManifest(coverElement("NOTE", builder -> builder)),
                Map.of());

        ArgumentCaptor<Diary> saved = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).create(eq(7L), saved.capture());
        assertThat(saved.getValue().getCoverStyle()).isEqualTo("DEFAULT");
    }

    /** 22) 체험 표지는 "내 디자인 보관함" 의 원본이 아니다. 그 쪽은 건드리지 않는다. */
    @Test
    void theDesignLibraryIsNeverTouched() {
        assertThat(List.of(GuestDiaryImportService.class.getDeclaredFields()))
                .extracting(field -> field.getType().getSimpleName())
                .doesNotContain("DiaryCoverDesignService", "DiaryCoverDesignElementService",
                        "DiaryCoverDesignMapper", "DiaryCoverDesignElementMapper");
    }

    /** 23) 브라우저가 말한 사진 주소는 버리고, 올라온 원본을 저장해 새 주소를 만든다. */
    @Test
    void aPhotoReferenceNeverReachesTheDatabase() {
        when(fileUploadService.saveImportedDiaryPhoto(any(), eq("diary-pages")))
                .thenReturn("/uploads/diary-pages/new-name.jpg");

        GuestDiaryImportManifest.Element photo = elementWith("PHOTO", builder -> builder
                .photoRef("guest-photo-ref-1")
                .imageUrl("blob:http://localhost/8f2c"));
        service.importDraft(7L,
                photoManifest(photo, Map.of("guest-photo-ref-1", "photo0")),
                Map.of("photo0", imageFile("photo0")));

        ArgumentCaptor<DiaryElement> saved = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(anyLong(), anyLong(), anyLong(), saved.capture());
        assertThat(saved.getValue().getImageUrl()).isEqualTo("/uploads/diary-pages/new-name.jpg");
        // 임시 참조는 어디에도 남지 않는다.
        assertThat(saved.getValue().toString())
                .doesNotContain("guest-photo-ref-1")
                .doesNotContain("blob:");
    }

    /** 23) 표지 사진도 표지 자리에 저장된다. */
    @Test
    void aCoverPhotoIsStoredInTheCoverFolder() {
        when(fileUploadService.saveImportedDiaryPhoto(any(), eq("diary-covers")))
                .thenReturn("/uploads/diary-covers/new-name.jpg");

        GuestDiaryImportManifest.Element photo = coverElement("PHOTO", builder -> builder
                .photoRef("cover-ref").photoStyle("FULL"));
        GuestDiaryImportManifest manifest = new GuestDiaryImportManifest(
                "체험 여행일기", "2026-03-01", "2026-03-03", "SPRING", "CUSTOM", null,
                new GuestDiaryImportManifest.Cover("DEFAULT", "#ffd6e4", List.of(photo)),
                List.of(page()), Map.of("cover-ref", "photo0"));

        service.importDraft(7L, manifest, Map.of("photo0", imageFile("photo0")));

        ArgumentCaptor<DiaryCoverElement> saved =
                ArgumentCaptor.forClass(DiaryCoverElement.class);
        verify(diaryCoverElementMapper).insert(saved.capture());
        assertThat(saved.getValue().getImageUrl())
                .isEqualTo("/uploads/diary-covers/new-name.jpg");
        assertThat(saved.getValue().getPhotoStyle()).isEqualTo("FULL");
        verify(fileUploadService).saveImportedDiaryPhoto(any(), eq("diary-covers"));
    }

    /** 24) 스티커는 이미 서버에 있는 그림이다. 파일을 새로 만들지 않는다. */
    @Test
    void stickersDoNotCreateAnyFile() {
        service.importDraft(7L, manifest(page(
                elementWith("STICKER", builder -> builder.imageUrl(knownStickerUrl)))), Map.of());

        verifyNoInteractions(fileUploadService);
    }

    /* ===================== 트랜잭션과 파일 ===================== */

    /** 28) DB 저장은 한 덩어리다. */
    @Test
    void theWholeSaveIsOneTransaction() throws NoSuchMethodException {
        assertThat(GuestDiaryImportService.class
                .getMethod("importDraft", Long.class, GuestDiaryImportManifest.class, Map.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .isTrue();
    }

    /**
     * 25) 27) 되돌아가면 이번에 저장한 사진만 지운다.
     *
     * <p>트랜잭션이 살아 있는 상황을 흉내 내고, 그 트랜잭션이 되돌아갔다고 알린다.
     * 파일은 트랜잭션이 되돌려 주지 않으므로 이 정리가 유일한 수습이다.
     */
    @Test
    void aRolledBackImportDeletesOnlyTheFilesItCreated(@org.junit.jupiter.api.io.TempDir Path uploads)
            throws IOException {
        ReflectionTestUtils.setField(service, "uploadPath", uploads.toString());

        Path mine = uploads.resolve("diary-pages/mine.jpg");
        Path existing = uploads.resolve("diary-pages/someone-elses.jpg");
        Files.createDirectories(mine.getParent());
        Files.writeString(mine, "new");
        Files.writeString(existing, "old");

        when(fileUploadService.saveImportedDiaryPhoto(any(), anyString()))
                .thenReturn("/uploads/diary-pages/mine.jpg");

        List<TransactionSynchronization> callbacks = withActiveTransaction(() ->
                service.importDraft(7L,
                        photoManifest(elementWith("PHOTO", builder -> builder.photoRef("ref-1")),
                                Map.of("ref-1", "photo0")),
                        Map.of("photo0", imageFile("photo0"))));

        assertThat(callbacks).hasSize(1);
        callbacks.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(mine).doesNotExist();
        // 원래 있던 파일은 건드리지 않는다.
        assertThat(existing).exists();
    }

    /** 26) 저장이 끝났으면 사진은 그대로 남는다. */
    @Test
    void aCommittedImportKeepsItsPhotos(@org.junit.jupiter.api.io.TempDir Path uploads)
            throws IOException {
        ReflectionTestUtils.setField(service, "uploadPath", uploads.toString());
        Path mine = uploads.resolve("diary-pages/mine.jpg");
        Files.createDirectories(mine.getParent());
        Files.writeString(mine, "new");

        when(fileUploadService.saveImportedDiaryPhoto(any(), anyString()))
                .thenReturn("/uploads/diary-pages/mine.jpg");

        List<TransactionSynchronization> callbacks = withActiveTransaction(() ->
                service.importDraft(7L,
                        photoManifest(elementWith("PHOTO", builder -> builder.photoRef("ref-1")),
                                Map.of("ref-1", "photo0")),
                        Map.of("photo0", imageFile("photo0"))));

        callbacks.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(mine).exists();
    }

    /** 27) 업로드 폴더 밖을 가리키는 주소로는 아무것도 지우지 않는다. */
    @Test
    void cleanupNeverLeavesTheUploadFolder(@org.junit.jupiter.api.io.TempDir Path uploads)
            throws Exception {
        ReflectionTestUtils.setField(service, "uploadPath", uploads.toString());
        Path stayed = uploads.resolve("keep.txt");
        Files.writeString(stayed, "keep");

        java.lang.reflect.Method delete = GuestDiaryImportService.class
                .getDeclaredMethod("deleteSavedFile", String.class);
        delete.setAccessible(true);
        for (String outside : new String[]{null, "/images/diary/stickers/heart.png",
                "keep.txt", "../keep.txt"}) {
            delete.invoke(service, outside);
        }

        assertThat(stayed).exists();
    }

    /* ===================== 도우미 ===================== */

    /** 트랜잭션이 열려 있는 척하고 등록된 뒷정리 약속을 꺼내 온다. */
    private List<TransactionSynchronization> withActiveTransaction(Runnable body) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            body.run();
            return List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MultipartFile imageFile(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private GuestDiaryImportManifest manifest(GuestDiaryImportManifest.Page... pages) {
        return manifestOfPages(List.of(pages));
    }

    private GuestDiaryImportManifest manifestOfPages(List<GuestDiaryImportManifest.Page> pages) {
        return new GuestDiaryImportManifest("체험 여행일기", "2026-03-01", "2026-03-03",
                "SPRING", "PRESET", "DEFAULT", null, pages, Map.of());
    }

    private GuestDiaryImportManifest manifestWithTitle(String title) {
        return new GuestDiaryImportManifest(title, "2026-03-01", "2026-03-03",
                "SPRING", "PRESET", "DEFAULT", null, List.of(page()), Map.of());
    }

    private GuestDiaryImportManifest manifestWithDates(String startDate, String endDate) {
        return new GuestDiaryImportManifest("체험 여행일기", startDate, endDate,
                "SPRING", "PRESET", "DEFAULT", null, List.of(page()), Map.of());
    }

    private GuestDiaryImportManifest customCoverManifest(
            GuestDiaryImportManifest.Element coverElement) {
        return new GuestDiaryImportManifest("체험 여행일기", "2026-03-01", "2026-03-03",
                "SPRING", "CUSTOM", "KRAFT",
                new GuestDiaryImportManifest.Cover("DEFAULT", "#ffd6e4", List.of(coverElement)),
                List.of(page()), Map.of());
    }

    private GuestDiaryImportManifest photoManifest(GuestDiaryImportManifest.Element photo,
                                                   Map<String, String> photoParts) {
        return photoManifest(List.of(photo), photoParts);
    }

    private GuestDiaryImportManifest photoManifest(List<GuestDiaryImportManifest.Element> photos,
                                                   Map<String, String> photoParts) {
        return new GuestDiaryImportManifest("체험 여행일기", "2026-03-01", "2026-03-03",
                "SPRING", "PRESET", "DEFAULT", null,
                List.of(new GuestDiaryImportManifest.Page("2026-03-01", null, null, null,
                        null, null, null, photos)),
                photoParts);
    }

    private GuestDiaryImportManifest.Page page(GuestDiaryImportManifest.Element... elements) {
        return new GuestDiaryImportManifest.Page("2026-03-01", null, null, null, null, null,
                null, List.of(elements));
    }

    /** 한 줄 메모가 적힌 장. (날짜는 다른 장과 같은 기본값을 쓴다) */
    private GuestDiaryImportManifest.Page pageWithHeader(String header, String font,
                                                         boolean bold) {
        return new GuestDiaryImportManifest.Page("2026-03-01", null, null,
                header, font, bold, null, List.of());
    }

    private GuestDiaryImportManifest.Page pageOn(String pageDate) {
        return new GuestDiaryImportManifest.Page(pageDate, null, null, null, null, null,
                null, List.of());
    }

    private GuestDiaryImportManifest.Element element(String elementType) {
        return elementWith(elementType, builder -> builder);
    }

    /** 페이지 요소. 자리·크기 판정은 회원과 같은 요소 서비스가 맡으므로 여기서는 채워만 둔다. */
    private GuestDiaryImportManifest.Element elementWith(
            String elementType, java.util.function.UnaryOperator<ElementBuilder> customizer) {
        return customizer.apply(new ElementBuilder(elementType)).build();
    }

    /** 표지 요소. 표지는 이 서비스가 직접 판정하므로 기본값이 모두 정상 범위여야 한다. */
    private GuestDiaryImportManifest.Element coverElement(
            String elementType, java.util.function.UnaryOperator<ElementBuilder> customizer) {
        ElementBuilder builder = new ElementBuilder(elementType)
                .positionX(new BigDecimal("0.2"))
                .positionY(new BigDecimal("0.3"))
                .width(new BigDecimal("0.4"))
                .height(new BigDecimal("0.2"))
                .rotation(BigDecimal.ZERO)
                .zIndex(1);
        if ("NOTE".equals(elementType)) {
            builder.styleType("MEMO_SQUARE").colorType("IVORY");
        }
        if ("TEXT".equals(elementType)) {
            builder.textContent("제주");
        }
        if ("STICKER".equals(elementType)) {
            builder.imageUrl(knownStickerUrl);
        }
        return customizer.apply(builder).build();
    }

    /** 읽을 수 있게 값만 채우는 도우미. (record 의 인자가 많아 자리를 세지 않으려고 둔다) */
    private static final class ElementBuilder {
        private final String elementType;
        private String textContent;
        private String imageUrl;
        private String photoRef;
        private String styleType;
        private String colorType;
        private String photoStyle;
        private String textFont;
        private String textColor;
        private BigDecimal positionX = new BigDecimal("0.1");
        private BigDecimal positionY = new BigDecimal("0.1");
        private BigDecimal width = new BigDecimal("0.3");
        private BigDecimal height = new BigDecimal("0.2");
        private BigDecimal rotation = BigDecimal.ZERO;
        private Integer zIndex = 1;

        private ElementBuilder(String elementType) {
            this.elementType = elementType;
        }

        private ElementBuilder textContent(String value) {
            this.textContent = value;
            return this;
        }

        private ElementBuilder imageUrl(String value) {
            this.imageUrl = value;
            return this;
        }

        private ElementBuilder photoRef(String value) {
            this.photoRef = value;
            return this;
        }

        private ElementBuilder styleType(String value) {
            this.styleType = value;
            return this;
        }

        private ElementBuilder colorType(String value) {
            this.colorType = value;
            return this;
        }

        private ElementBuilder photoStyle(String value) {
            this.photoStyle = value;
            return this;
        }

        private ElementBuilder textFont(String value) {
            this.textFont = value;
            return this;
        }

        private ElementBuilder textColor(String value) {
            this.textColor = value;
            return this;
        }

        private ElementBuilder positionX(BigDecimal value) {
            this.positionX = value;
            return this;
        }

        private ElementBuilder positionY(BigDecimal value) {
            this.positionY = value;
            return this;
        }

        private ElementBuilder width(BigDecimal value) {
            this.width = value;
            return this;
        }

        private ElementBuilder height(BigDecimal value) {
            this.height = value;
            return this;
        }

        private ElementBuilder rotation(BigDecimal value) {
            this.rotation = value;
            return this;
        }

        private ElementBuilder zIndex(Integer value) {
            this.zIndex = value;
            return this;
        }

        private GuestDiaryImportManifest.Element build() {
            return new GuestDiaryImportManifest.Element(elementType, textContent, imageUrl,
                    photoRef, styleType, colorType, photoStyle, textFont, textColor,
                    positionX, positionY, width, height, rotation, zIndex);
        }
    }
}
