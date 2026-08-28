package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryElement;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.repository.diary.DiaryElementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 요소 유형 허용 범위. STICKER 는 PHOTO 와 같은 자유배치 이미지 요소로 다룬다.
 * (DB chk_diary_elements_type / chk_diary_elements_payload 와 같은 규칙)
 */
@ExtendWith(MockitoExtension.class)
class DiaryElementServiceImplTest {

    @Mock
    private DiaryPageService diaryPageService;
    @Mock
    private DiaryElementMapper diaryElementMapper;

    private DiaryElementService diaryElementService;

    @BeforeEach
    void setUp() {
        // 라벨/메모지 목록은 실제 manifest 를 그대로 읽는다. (허용 목록이 두 벌이 되지 않게)
        DiaryNoteCatalog diaryNoteCatalog = new DiaryNoteCatalog();
        diaryNoteCatalog.load();
        diaryElementService = new DiaryElementServiceImpl(
                diaryPageService, diaryElementMapper, diaryNoteCatalog);
    }

    @Test
    void stickerIsStoredLikeAPhotoWithItsImageAndFreeLayoutValues() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());
        when(diaryElementMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryElement.class).setId(101L);
            return 1;
        });
        when(diaryElementMapper.findByIdAndPageId(101L, 3L)).thenReturn(new DiaryElement());

        DiaryElement sticker = imageElement("STICKER", "/images/diary/stickers/emotion/heart.svg");
        sticker.setPositionX(new BigDecimal("0.20000"));
        sticker.setRotation(new BigDecimal("12.00"));
        sticker.setZIndex(2);

        diaryElementService.create(10L, 3L, 7L, sticker);

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementMapper).insert(captor.capture());
        DiaryElement saved = captor.getValue();
        assertThat(saved.getElementType()).isEqualTo("STICKER");
        assertThat(saved.getImageUrl()).isEqualTo("/images/diary/stickers/emotion/heart.svg");
        // 사진과 같은 payload 규칙: 본문은 비운다
        assertThat(saved.getTextContent()).isNull();
        // 좌표/회전/겹침 순서 검증도 사진과 똑같이 지나간다
        assertThat(saved.getPositionX()).isEqualByComparingTo("0.20000");
        assertThat(saved.getRotation()).isEqualByComparingTo("12.00");
        assertThat(saved.getZIndex()).isEqualTo(2);
    }

    @Test
    void stickerWithoutAnImageIsRejected() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());

        DiaryElement sticker = imageElement("STICKER", null);

        assertThatThrownBy(() -> diaryElementService.create(10L, 3L, 7L, sticker))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("스티커를 선택해 주세요.");
        verify(diaryElementMapper, never()).insert(any());
    }

    /** 좌표/크기 검증은 유형과 무관하게 같은 범위를 쓴다. */
    @Test
    void stickerOutsideTheAllowedAreaIsRejected() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());

        DiaryElement sticker = imageElement("STICKER", "/images/diary/stickers/emotion/heart.svg");
        sticker.setPositionY(new BigDecimal("9.00000"));

        assertThatThrownBy(() -> diaryElementService.create(10L, 3L, 7L, sticker))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("세로 위치가 허용 범위를 벗어났습니다.");
        verify(diaryElementMapper, never()).insert(any());
    }

    /** 기존 PHOTO 동작은 그대로다. */
    @Test
    void photoWithoutAnImageStillReportsThePhotoMessage() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());

        DiaryElement photo = imageElement("PHOTO", null);

        assertThatThrownBy(() -> diaryElementService.create(10L, 3L, 7L, photo))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("사진을 선택해 주세요.");
        verify(diaryElementMapper, never()).insert(any());
    }

    @Test
    void unknownElementTypesAreStillRejected() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());

        DiaryElement tape = imageElement("TAPE", "/images/diary/stickers/decoration/tape.svg");

        assertThatThrownBy(() -> diaryElementService.create(10L, 3L, 7L, tape))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("지원하지 않는 요소 유형입니다.");
        verify(diaryElementMapper, never()).insert(any());
    }

    /** 본문은 diary_pages.content 를 쓰지만 TEXT 요소 지원 자체는 그대로 남아 있다. */
    @Test
    void textElementsAreStillAccepted() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());
        when(diaryElementMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryElement.class).setId(102L);
            return 1;
        });
        when(diaryElementMapper.findByIdAndPageId(102L, 3L)).thenReturn(new DiaryElement());

        DiaryElement text = new DiaryElement();
        text.setElementType("TEXT");
        text.setTextContent("메모");

        diaryElementService.create(10L, 3L, 7L, text);

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementMapper).insert(captor.capture());
        assertThat(captor.getValue().getTextContent()).isEqualTo("메모");
        assertThat(captor.getValue().getImageUrl()).isNull();
    }

    // ── 라벨 / 떡메모지 (NOTE) ───────────────────────────────

    @Test
    void aNoteIsStoredWithItsTextAndDesignTogether() {
        givenPageAndInsert(201L);

        DiaryElement note = noteElement("MEMO_SQUARE", "제주 카페 투어");
        note.setPositionX(new BigDecimal("0.40000"));
        note.setRotation(new BigDecimal("-5.00"));
        note.setZIndex(3);

        diaryElementService.create(10L, 3L, 7L, note);

        DiaryElement saved = savedElement();
        assertThat(saved.getElementType()).isEqualTo("NOTE");
        assertThat(saved.getTextContent()).isEqualTo("제주 카페 투어");
        assertThat(saved.getStyleType()).isEqualTo("MEMO_SQUARE");
        // 배경을 그림으로 붙이지 않는다. 모양은 style_type 하나로 정해진다
        assertThat(saved.getImageUrl()).isNull();
        // 자리·회전·겹침 순서는 스티커와 똑같은 길을 지난다
        assertThat(saved.getPositionX()).isEqualByComparingTo("0.40000");
        assertThat(saved.getRotation()).isEqualByComparingTo("-5.00");
        assertThat(saved.getZIndex()).isEqualTo(3);
    }

    @Test
    void aNoteCanBePlacedBeforeAnythingIsWrittenOnIt() {
        givenPageAndInsert(202L);

        /*
          붙인 직후에는 적은 글이 없다. 여기서 글을 강요하면
          라벨을 붙이기도 전에 무슨 말을 쓸지 정해야 한다.
        */
        diaryElementService.create(10L, 3L, 7L, noteElement("DATE_LABEL", null));

        DiaryElement saved = savedElement();
        // DB 가 막는 것은 NULL 뿐이다. 빈 글은 그대로 저장된다
        assertThat(saved.getTextContent()).isEmpty();
        assertThat(saved.getStyleType()).isEqualTo("DATE_LABEL");
    }

    @Test
    void aNoteWithoutADesignIsRefused() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());

        assertThatThrownBy(() ->
                diaryElementService.create(10L, 3L, 7L, noteElement(null, "안녕")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("라벨/메모지 디자인을 선택해 주세요.");

        verify(diaryElementMapper, never()).insert(any());
    }

    @Test
    void aDesignThatIsNotOnTheListIsRefused() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());

        // 화면이 보낸 값을 그대로 믿지 않는다. 모르는 값은 그릴 모양이 없다
        assertThatThrownBy(() ->
                diaryElementService.create(10L, 3L, 7L, noteElement("MEMO_TRIANGLE", "안녕")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("라벨/메모지 디자인을 선택해 주세요.");

        verify(diaryElementMapper, never()).insert(any());
    }

    @Test
    void aNoteNeverKeepsAnImagePathEvenIfOneIsSent() {
        givenPageAndInsert(203L);

        DiaryElement note = noteElement("MEMO_ROUND", "메모");
        note.setImageUrl("/images/diary/stickers/emotion/heart.svg");

        diaryElementService.create(10L, 3L, 7L, note);

        assertThat(savedElement().getImageUrl()).isNull();
    }

    @Test
    void theOtherKindsNeverPickUpADesignValue() {
        givenPageAndInsert(204L);

        // 사진·스티커·글에는 style_type 이 붙지 않는다 (DB payload CHECK 와 같은 규칙)
        DiaryElement sticker =
                imageElement("STICKER", "/images/diary/stickers/emotion/heart.svg");
        sticker.setStyleType("MEMO_SQUARE");

        diaryElementService.create(10L, 3L, 7L, sticker);

        DiaryElement saved = savedElement();
        assertThat(saved.getElementType()).isEqualTo("STICKER");
        assertThat(saved.getStyleType()).isNull();
    }

    @Test
    void aTextElementNeverPicksUpADesignValueEither() {
        givenPageAndInsert(205L);

        DiaryElement text = new DiaryElement();
        text.setElementType("TEXT");
        text.setTextContent("오늘의 기록");
        text.setStyleType("DATE_LABEL");

        diaryElementService.create(10L, 3L, 7L, text);

        DiaryElement saved = savedElement();
        assertThat(saved.getElementType()).isEqualTo("TEXT");
        assertThat(saved.getStyleType()).isNull();
    }

    @Test
    void aStickerCannotBeTurnedIntoANoteByEditingIt() {
        DiaryElement existing = new DiaryElement();
        existing.setId(300L);
        existing.setElementType("STICKER");
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());
        when(diaryElementMapper.findByIdAndPageId(300L, 3L)).thenReturn(existing);

        /*
          유형은 등록할 때 정해진 값을 그대로 쓴다.
          바꿀 수 있으면 스티커가 글자를 갖거나 메모지가 그림을 갖게 된다.
          여기서는 STICKER 규칙이 적용되어 이미지가 없다는 이유로 막힌다.
        */
        assertThatThrownBy(() ->
                diaryElementService.update(10L, 3L, 300L, 7L, noteElement("MEMO_SQUARE", "메모")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("스티커를 선택해 주세요.");

        verify(diaryElementMapper, never()).update(any());
    }

    @Test
    void movingANoteKeepsItsDesignAndWords() {
        givenStoredNote(400L, "MEMO_SQUARE", "제주 카페 투어");

        diaryElementService.move(10L, 3L, 400L, 7L,
                new BigDecimal("0.55000"), new BigDecimal("0.62000"));

        /*
          옮기기는 자리만 바꾼다. 화면은 좌표만 보내므로 나머지는 저장된 값에서 온다.
          한 칸이라도 빠뜨리면 그 값이 비워진 채로 검사를 지나가 요청이 거부된다.
        */
        DiaryElement saved = updatedElement();
        assertThat(saved.getPositionX()).isEqualByComparingTo("0.55000");
        assertThat(saved.getPositionY()).isEqualByComparingTo("0.62000");
        assertThat(saved.getElementType()).isEqualTo("NOTE");
        assertThat(saved.getStyleType()).isEqualTo("MEMO_SQUARE");
        assertThat(saved.getTextContent()).isEqualTo("제주 카페 투어");
    }

    @Test
    void resizingAndRotatingANoteKeepItsDesignToo() {
        givenStoredNote(401L, "DATE_LABEL", "2026.08.28");

        diaryElementService.resize(10L, 3L, 401L, 7L,
                new BigDecimal("0.40000"), new BigDecimal("0.10000"));
        assertThat(updatedElement().getStyleType()).isEqualTo("DATE_LABEL");

        org.mockito.Mockito.clearInvocations(diaryElementMapper);
        givenStoredNote(401L, "DATE_LABEL", "2026.08.28");

        diaryElementService.rotate(10L, 3L, 401L, 7L, new BigDecimal("7.00"));
        assertThat(updatedElement().getStyleType()).isEqualTo("DATE_LABEL");
    }

    @Test
    void anEmptyNoteSurvivesBeingMoved() {
        // 아직 글을 쓰지 않은 라벨도 그대로 옮겨진다 (빈 글이 NULL 로 바뀌지 않는다)
        givenStoredNote(402L, "TITLE_LABEL", "");

        diaryElementService.move(10L, 3L, 402L, 7L,
                new BigDecimal("0.30000"), new BigDecimal("0.30000"));

        assertThat(updatedElement().getTextContent()).isEmpty();
        assertThat(updatedElement().getStyleType()).isEqualTo("TITLE_LABEL");
    }

    @Test
    void everyColumnTheDatabaseWritesIsCarriedOverWhenOnlyOneValueChanges() throws Exception {
        String service = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/travlediary/service/diary/DiaryElementServiceImpl.java"));
        String mapper = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/mapper/DiaryElementMapper.xml"));

        String copyOf = service.substring(service.indexOf("private DiaryElement copyOf("));
        copyOf = copyOf.substring(0, copyOf.indexOf("\n    }"));
        String update = mapper.substring(mapper.indexOf("<update id=\"update\""));
        update = update.substring(0, update.indexOf("</update>"));

        /*
          저장이 쓰는 칸은 모두 복사본에도 있어야 한다.
          하나라도 빠지면 옮기기·크기·회전에서 그 값이 조용히 비워진다.
          (style_type 을 빠뜨려 NOTE 를 옮길 수 없었던 일이 실제로 있었다)
        */
        for (String field : new String[]{
                "textContent", "imageUrl", "styleType",
                "positionX", "positionY", "width", "height", "rotation", "zIndex"}) {
            if (update.contains("#{" + field + "}")) {
                assertThat(copyOf).as("copyOf 가 %s 를 옮기지 않는다", field)
                        .contains("copy.set" + field.substring(0, 1).toUpperCase()
                                + field.substring(1) + "(existing.get");
            }
        }
    }

    // ── 라벨 / 떡메모지에 글쓰기 ──────────────────────────────

    @Test
    void writingOnANoteChangesTheWordsAndNothingElse() {
        givenStoredNote(500L, "MEMO_SQUARE", "");

        diaryElementService.updateNoteText(10L, 3L, 500L, 7L, "제주 카페 투어\n두 번째 줄");

        DiaryElement saved = updatedElement();
        assertThat(saved.getTextContent()).isEqualTo("제주 카페 투어\n두 번째 줄");
        // 디자인·자리·크기·회전·겹침 순서는 저장된 값 그대로다
        assertThat(saved.getStyleType()).isEqualTo("MEMO_SQUARE");
        assertThat(saved.getElementType()).isEqualTo("NOTE");
        assertThat(saved.getPositionX()).isEqualByComparingTo("0.41000");
        assertThat(saved.getWidth()).isEqualByComparingTo("0.26000");
        assertThat(saved.getRotation()).isEqualByComparingTo("0.00");
        assertThat(saved.getZIndex()).isZero();
    }

    @Test
    void everythingCanBeErasedFromANote() {
        givenStoredNote(501L, "DATE_LABEL", "2026.08.28");

        // 다 지우고 나가는 것도 사용자의 선택이다. 빈 글은 그대로 저장된다
        diaryElementService.updateNoteText(10L, 3L, 501L, 7L, "");

        assertThat(updatedElement().getTextContent()).isEmpty();
    }

    @Test
    void aLabelStaysOnOneLine() {
        givenStoredNote(502L, "TITLE_LABEL", "");

        // 여러 줄을 붙여 넣어도 한 줄로 들어온다. 붙여넣기를 통째로 거절하지 않는다
        diaryElementService.updateNoteText(10L, 3L, 502L, 7L, "JEJU\r\nDAY 1");

        assertThat(updatedElement().getTextContent()).isEqualTo("JEJU DAY 1");
    }

    @Test
    void aMemoKeepsTheLinesThatWereTyped() {
        givenStoredNote(503L, "MEMO_ROUND", "");

        diaryElementService.updateNoteText(10L, 3L, 503L, 7L, "첫 줄\r\n둘째 줄\r\n셋째 줄");

        // 줄바꿈은 그대로 남고 형식만 하나로 맞춘다
        assertThat(updatedElement().getTextContent()).isEqualTo("첫 줄\n둘째 줄\n셋째 줄");
    }

    @Test
    void aLabelHoldsAHundredLettersAndAMemoAThousand() {
        givenStoredNote(504L, "DATE_LABEL", "");
        diaryElementService.updateNoteText(10L, 3L, 504L, 7L, "가".repeat(100));
        assertThat(updatedElement().getTextContent()).hasSize(100);

        org.mockito.Mockito.clearInvocations(diaryElementMapper);
        givenStoredNote(505L, "MEMO_SQUARE", "");
        diaryElementService.updateNoteText(10L, 3L, 505L, 7L, "가".repeat(1000));
        assertThat(updatedElement().getTextContent()).hasSize(1000);
    }

    @Test
    void oneLetterTooManyIsRefused() {
        givenStoredNote(506L, "DATE_LABEL", "");
        assertThatThrownBy(() ->
                diaryElementService.updateNoteText(10L, 3L, 506L, 7L, "가".repeat(101)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("100자까지 입력할 수 있습니다.");

        givenStoredNote(507L, "MEMO_ROUND", "");
        assertThatThrownBy(() ->
                diaryElementService.updateNoteText(10L, 3L, 507L, 7L, "가".repeat(1001)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("1000자까지 입력할 수 있습니다.");

        verify(diaryElementMapper, never()).update(any());
    }

    @Test
    void aPhotoCannotBeGivenWordsThroughTheNoteDoor() {
        DiaryElement photo = imageElement("PHOTO", "/uploads/diary/photo.jpg");
        photo.setId(508L);
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());
        when(diaryElementMapper.findByIdAndPageId(508L, 3L)).thenReturn(photo);

        assertThatThrownBy(() ->
                diaryElementService.updateNoteText(10L, 3L, 508L, 7L, "몰래 넣은 글"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("라벨/메모지 요소가 아닙니다.");

        verify(diaryElementMapper, never()).update(any());
    }

    /** 이미 페이지에 놓여 있는 라벨/메모지 한 장. */
    private void givenStoredNote(Long elementId, String styleType, String textContent) {
        DiaryElement stored = noteElement(styleType, textContent);
        stored.setId(elementId);
        stored.setPageId(3L);
        stored.setPositionX(new BigDecimal("0.41000"));
        stored.setPositionY(new BigDecimal("0.41000"));
        stored.setWidth(new BigDecimal("0.26000"));
        stored.setHeight(new BigDecimal("0.28000"));
        stored.setRotation(new BigDecimal("0.00"));
        stored.setZIndex(0);

        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());
        when(diaryElementMapper.findByIdAndPageId(elementId, 3L)).thenReturn(stored);
        // 저장까지 가지 않고 검사에서 끊기는 시나리오도 이 준비를 함께 쓴다.
        org.mockito.Mockito.lenient().when(diaryElementMapper.update(any())).thenReturn(1);
    }

    /** 실제로 DB 로 넘어간 수정 값. */
    private DiaryElement updatedElement() {
        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementMapper).update(captor.capture());
        return captor.getValue();
    }

    /** 저장까지 지나가는 흐름의 공통 준비. 새로 만든 요소의 id 만 다르다. */
    private void givenPageAndInsert(Long newId) {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());
        when(diaryElementMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryElement.class).setId(newId);
            return 1;
        });
        when(diaryElementMapper.findByIdAndPageId(newId, 3L)).thenReturn(new DiaryElement());
    }

    /** 실제로 DB 로 넘어간 값. */
    private DiaryElement savedElement() {
        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementMapper).insert(captor.capture());
        return captor.getValue();
    }

    private DiaryElement noteElement(String styleType, String textContent) {
        DiaryElement element = new DiaryElement();
        element.setElementType("NOTE");
        element.setStyleType(styleType);
        element.setTextContent(textContent);
        return element;
    }

    private DiaryElement imageElement(String elementType, String imageUrl) {
        DiaryElement element = new DiaryElement();
        element.setElementType(elementType);
        element.setImageUrl(imageUrl);
        return element;
    }

    private DiaryPage page() {
        DiaryPage page = new DiaryPage();
        page.setId(3L);
        page.setDiaryId(10L);
        return page;
    }
}
