package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 표지 라벨기(TEXT). 페이지 다꾸의 라벨기와 같은 규칙을 쓰지만 서비스는 각자 따로 둔다.
 * 소유권은 늘 보관함 서비스가 확인해 준다 — 남의 디자인 번호는 요소가 만들어지기 전에 막힌다.
 */
@ExtendWith(MockitoExtension.class)
class DiaryCoverDesignElementLabelTest {

    @Mock
    private DiaryCoverDesignService diaryCoverDesignService;
    @Mock
    private DiaryCoverDesignElementMapper diaryCoverDesignElementMapper;

    private DiaryCoverDesignElementService service;

    @BeforeEach
    void setUp() {
        // 글꼴 목록은 실제 manifest 를 그대로 읽는다. (허용 목록이 두 벌이 되지 않게)
        DiaryLabelFontCatalog fonts = new DiaryLabelFontCatalog();
        fonts.load();
        service = new DiaryCoverDesignElementServiceImpl(
                diaryCoverDesignService, diaryCoverDesignElementMapper, null, fonts);
    }

    @Test
    void aLabelKeepsOnlyItsTextAndFont() {
        givenOwnedDesign();
        when(diaryCoverDesignElementMapper.findAllByDesignId(5L)).thenReturn(List.of());
        givenInsertedElement(100L);

        service.createLabel(5L, 7L, "  SUMMER\nTRIP  ", "bookk-myeongjo", "#C86B7C");

        ArgumentCaptor<DiaryCoverDesignElement> saved =
                ArgumentCaptor.forClass(DiaryCoverDesignElement.class);
        verify(diaryCoverDesignElementMapper).insert(saved.capture());
        assertThat(saved.getValue().getElementType()).isEqualTo("TEXT");
        // 한 줄로 다듬어 저장한다
        assertThat(saved.getValue().getTextContent()).isEqualTo("SUMMER TRIP");
        assertThat(saved.getValue().getTextFont()).isEqualTo("bookk-myeongjo");
        assertThat(saved.getValue().getTextColor()).isEqualTo("#C86B7C");
        // TEXT 가 쓰지 않는 칸은 손대지 않는다 (DB payload CHECK 와 같은 규칙)
        assertThat(saved.getValue().getImageUrl()).isNull();
        assertThat(saved.getValue().getStyleType()).isNull();
        assertThat(saved.getValue().getColorType()).isNull();
        assertThat(saved.getValue().getPhotoStyle()).isNull();
    }

    @Test
    void anEmptyLabelAndAnUnknownFontOrColorAreAllRejected() {
        givenOwnedDesign();

        assertThatThrownBy(() -> service.createLabel(5L, 7L, "   ", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("내용을 입력해 주세요.");
        assertThatThrownBy(() -> service.createLabel(5L, 7L, "JEJU", "comic-sans", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("지원하지 않는 글꼴입니다.");
        // 글자색은 페이지 다꾸와 같은 규칙으로 #RRGGBB 만 저장한다
        assertThatThrownBy(() -> service.createLabel(5L, 7L, "JEJU", null, "red"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("글자색을 다시 선택해 주세요.");
        verify(diaryCoverDesignElementMapper, never()).insert(any());
    }

    /** 남의 디자인 번호는 요소가 만들어지기 전에 막힌다. */
    @Test
    void aDesignThatIsNotMineIsBlockedBeforeAnythingIsCreated() {
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "표지 디자인을 찾을 수 없습니다."));

        assertThatThrownBy(() -> service.createLabel(5L, 7L, "JEJU", "nanum-square", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지 디자인을 찾을 수 없습니다.");
        verify(diaryCoverDesignElementMapper, never()).insert(any());
    }

    /** 다른 유형의 요소 번호로는 이 문을 지날 수 없다. (사진을 글씨 삭제로 지우지 못한다) */
    @Test
    void deletingALabelRejectsElementsOfOtherTypes() {
        givenOwnedDesign();
        DiaryCoverDesignElement photo = new DiaryCoverDesignElement();
        photo.setId(100L);
        photo.setElementType("PHOTO");
        photo.setImageUrl("/uploads/diary-cover-designs/a.jpg");
        when(diaryCoverDesignElementMapper.findById(100L, 5L)).thenReturn(photo);

        assertThatThrownBy(() -> service.deleteLabel(5L, 100L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("글씨 요소가 아닙니다.");
        verify(diaryCoverDesignElementMapper, never()).deleteById(any(), any());
    }

    private void givenOwnedDesign() {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setId(5L);
        design.setUserId(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design);
    }

    /** insert 는 PK 를 인자에 채워 준다. (useGeneratedKeys) */
    private void givenInsertedElement(Long elementId) {
        when(diaryCoverDesignElementMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCoverDesignElement.class).setId(elementId);
            return 1;
        });
        when(diaryCoverDesignElementMapper.findById(elementId, 5L))
                .thenReturn(new DiaryCoverDesignElement());
    }
}
