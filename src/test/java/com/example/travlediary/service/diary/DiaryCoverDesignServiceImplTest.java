package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverMaterial;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverDesignMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 내 표지 디자인 보관함은 언제나 "내 것"만 다룬다.
 * 남의 번호를 넣어도 조회 단계에서 막히고, 저장 값은 DB 제약과 같은 규칙으로 걸러진다.
 */
@ExtendWith(MockitoExtension.class)
class DiaryCoverDesignServiceImplTest {

    @Mock
    private DiaryCoverDesignMapper diaryCoverDesignMapper;
    @Mock
    private DiaryCoverDesignElementMapper diaryCoverDesignElementMapper;

    private DiaryCoverDesignService service;

    @BeforeEach
    void setUp() {
        service = new DiaryCoverDesignServiceImpl(
                diaryCoverDesignMapper, diaryCoverDesignElementMapper);
    }

    @Test
    void anotherPersonsDesignIsNotFound() {
        when(diaryCoverDesignMapper.findByIdAndUserId(5L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> service.getMyDesign(5L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지 디자인을 찾을 수 없습니다.");
        // 소유권이 확인되기 전에는 요소도 읽지 않는다
        verify(diaryCoverDesignElementMapper, never()).findAllByDesignId(any());
    }

    @Test
    void theOwnerIsTakenFromTheSessionNotFromTheRequest() {
        DiaryCoverDesign requested = new DiaryCoverDesign();
        requested.setUserId(999L);          // 남의 번호를 실어 보내도
        requested.setName("  제주 여행  ");
        when(diaryCoverDesignMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCoverDesign.class).setId(5L);
            return 1;
        });
        when(diaryCoverDesignMapper.findByIdAndUserId(5L, 7L)).thenReturn(new DiaryCoverDesign());

        service.create(7L, requested);

        ArgumentCaptor<DiaryCoverDesign> captor = ArgumentCaptor.forClass(DiaryCoverDesign.class);
        verify(diaryCoverDesignMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        // 이름은 앞뒤 공백을 정리해서 저장한다
        assertThat(captor.getValue().getName()).isEqualTo("제주 여행");
        // 고르지 않은 바탕 표지는 기본 표지가 된다 (DB 기본값과 같은 값)
        assertThat(captor.getValue().getBaseCoverStyle()).isEqualTo("DEFAULT");
        // 색을 고르지 않으면 비워 둔다. 그때는 기본색으로 그린다
        assertThat(captor.getValue().getBackgroundColor()).isNull();
    }

    @Test
    void aDesignNeedsAName() {
        DiaryCoverDesign blank = new DiaryCoverDesign();
        blank.setName("   ");

        assertThatThrownBy(() -> service.create(7L, blank))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("디자인 이름을 입력해 주세요.");
        verify(diaryCoverDesignMapper, never()).insert(any());
    }

    @Test
    void onlyKnownCoverStylesAndColoursAreStored() {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setName("빈티지");
        design.setBaseCoverStyle("GOLD_PLATED");

        assertThatThrownBy(() -> service.create(7L, design))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지 스타일을 다시 선택해 주세요.");

        design.setBaseCoverStyle("LEATHER_DEEP_GREEN");
        design.setBackgroundColor("red");
        assertThatThrownBy(() -> service.create(7L, design))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지 색상을 다시 선택해 주세요.");

        verify(diaryCoverDesignMapper, never()).insert(any());
    }

    /**
     * 화면은 재질 세 갈래만 보여 주고 색은 따로 고른다.
     * 재질이 그대로면 쓰던 표지 값을 그대로 둔다 — 이름만 고쳤다고 색이 바뀌면 안 된다.
     */
    @Test
    void keepingTheSameMaterialKeepsTheExactShade() {
        DiaryCoverDesign existing = new DiaryCoverDesign();
        existing.setId(5L);
        existing.setUserId(7L);
        existing.setBaseCoverStyle("LEATHER_DEEP_GREEN");
        when(diaryCoverDesignMapper.findByIdAndUserId(5L, 7L)).thenReturn(existing);
        when(diaryCoverDesignMapper.update(any())).thenReturn(1);

        // 가죽 갈래의 대표 값(LEATHER_BLACK)이 와도 쓰던 딥그린이 그대로 남는다
        service.updateBasics(5L, 7L, "빈티지", "LEATHER_BLACK", null);

        ArgumentCaptor<DiaryCoverDesign> captor = ArgumentCaptor.forClass(DiaryCoverDesign.class);
        verify(diaryCoverDesignMapper).update(captor.capture());
        assertThat(captor.getValue().getBaseCoverStyle()).isEqualTo("LEATHER_DEEP_GREEN");
        assertThat(captor.getValue().getName()).isEqualTo("빈티지");
    }

    /** 재질을 실제로 바꾸면 그 갈래의 대표 값으로 옮긴다. */
    @Test
    void switchingMaterialMovesToThatMaterial() {
        DiaryCoverDesign existing = new DiaryCoverDesign();
        existing.setId(5L);
        existing.setUserId(7L);
        existing.setBaseCoverStyle("LEATHER_DEEP_GREEN");
        when(diaryCoverDesignMapper.findByIdAndUserId(5L, 7L)).thenReturn(existing);
        when(diaryCoverDesignMapper.update(any())).thenReturn(1);

        service.updateBasics(5L, 7L, "빈티지", "HARDCOVER_NAVY", "#7a5cc4");

        ArgumentCaptor<DiaryCoverDesign> captor = ArgumentCaptor.forClass(DiaryCoverDesign.class);
        verify(diaryCoverDesignMapper).update(captor.capture());
        assertThat(captor.getValue().getBaseCoverStyle()).isEqualTo("HARDCOVER_NAVY");
        // 색은 재질과 별개의 축이라 고른 값이 그대로 저장된다
        assertThat(captor.getValue().getBackgroundColor()).isEqualTo("#7a5cc4");
    }

    /** 예전에 만든 표지 값도 세 갈래 중 하나로 읽힌다. */
    @Test
    void everyExistingCoverStyleFallsIntoOneOfTheThreeMaterials() {
        assertThat(DiaryCoverMaterial.of("DEFAULT")).isEqualTo(DiaryCoverMaterial.PLAIN);
        assertThat(DiaryCoverMaterial.of("LEATHER_LIGHT_BROWN"))
                .isEqualTo(DiaryCoverMaterial.LEATHER);
        assertThat(DiaryCoverMaterial.of("HARDCOVER_BEIGE"))
                .isEqualTo(DiaryCoverMaterial.HARDCOVER);
        // 모르는 값과 빈 값은 기본으로 본다
        assertThat(DiaryCoverMaterial.of(null)).isEqualTo(DiaryCoverMaterial.PLAIN);
        assertThat(DiaryCoverMaterial.of("GOLD_PLATED")).isEqualTo(DiaryCoverMaterial.PLAIN);
        // 고르면 저장되는 값은 그 갈래의 대표 표지다
        assertThat(DiaryCoverMaterial.LEATHER.getCode()).isEqualTo("LEATHER_BLACK");
        assertThat(DiaryCoverMaterial.HARDCOVER.getCode()).isEqualTo("HARDCOVER_NAVY");
        assertThat(DiaryCoverMaterial.values()).hasSize(3);
    }

    /** 보관함이 비어 있으면 요소를 물으러 가지 않는다. (IN () 이 되지 않게) */
    @Test
    void anEmptyShelfDoesNotAskForElements() {
        DiaryCoverDesignElementService elementService = new DiaryCoverDesignElementServiceImpl(
                service, diaryCoverDesignElementMapper, null, null);

        assertThat(elementService.getElementsByDesign(java.util.List.of(), 7L)).isEmpty();
        verify(diaryCoverDesignElementMapper, never()).findAllByDesignIds(any(), any());
    }

    /** 지우고 나면 못 읽으므로, 사진 파일 정리에 쓸 목록을 먼저 확보해 돌려준다. */
    @Test
    void deletingADesignReturnsItsElementsForFileCleanup() {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setId(5L);
        when(diaryCoverDesignMapper.findByIdAndUserId(5L, 7L)).thenReturn(design);
        when(diaryCoverDesignElementMapper.findAllByDesignId(5L)).thenReturn(java.util.List.of());
        when(diaryCoverDesignMapper.deleteByIdAndUserId(5L, 7L)).thenReturn(1);

        assertThat(service.delete(5L, 7L)).isEmpty();

        // 요소를 먼저 읽고 그 다음에 지운다
        var order = org.mockito.Mockito.inOrder(
                diaryCoverDesignElementMapper, diaryCoverDesignMapper);
        order.verify(diaryCoverDesignElementMapper).findAllByDesignId(5L);
        order.verify(diaryCoverDesignMapper).deleteByIdAndUserId(5L, 7L);
    }
}
