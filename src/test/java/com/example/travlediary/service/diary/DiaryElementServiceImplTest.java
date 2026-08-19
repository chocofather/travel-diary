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
        diaryElementService = new DiaryElementServiceImpl(diaryPageService, diaryElementMapper);
    }

    @Test
    void stickerIsStoredLikeAPhotoWithItsImageAndFreeLayoutValues() {
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page());
        when(diaryElementMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryElement.class).setId(101L);
            return 1;
        });
        when(diaryElementMapper.findByIdAndPageId(101L, 3L)).thenReturn(new DiaryElement());

        DiaryElement sticker = imageElement("STICKER", "/uploads/diary-stickers/heart.png");
        sticker.setPositionX(new BigDecimal("0.20000"));
        sticker.setRotation(new BigDecimal("12.00"));
        sticker.setZIndex(2);

        diaryElementService.create(10L, 3L, 7L, sticker);

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementMapper).insert(captor.capture());
        DiaryElement saved = captor.getValue();
        assertThat(saved.getElementType()).isEqualTo("STICKER");
        assertThat(saved.getImageUrl()).isEqualTo("/uploads/diary-stickers/heart.png");
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

        DiaryElement sticker = imageElement("STICKER", "/uploads/diary-stickers/heart.png");
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

        DiaryElement tape = imageElement("TAPE", "/uploads/diary-stickers/tape.png");

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
