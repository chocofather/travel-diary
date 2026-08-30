package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesignElement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 표지 디자인에 올린 요소를 다룬다.
 *
 * <p>페이지 다꾸(DiaryElementService)와 같은 조작을 제공하지만 별개의 서비스다.
 * 페이지 쪽을 일반화하지 않고 표지 전용으로 따로 둔다. (정상 동작 중인 다꾸를 건드리지 않는다)
 * 모든 메서드가 designId 와 userId 를 함께 받아 본인 디자인만 다룬다.
 */
public interface DiaryCoverDesignElementService {

    /** 한 디자인의 요소 전체 (겹침 순서 그대로) */
    List<DiaryCoverDesignElement> getElements(Long designId, Long userId);

    /**
     * 여러 디자인의 요소를 한 번에 읽어 디자인 번호별로 묶어 돌려준다.
     * 보관함 목록이 카드마다 따로 묻지 않도록 쓰는 길이다. (요소가 없는 디자인은 빈 목록)
     */
    Map<Long, List<DiaryCoverDesignElement>> getElementsByDesign(List<Long> designIds, Long userId);

    /**
     * 공용 스티커(마스킹테이프 포함)를 한 장 붙인다.
     * 그림 경로는 서버가 허용 목록에서 고르고, 새 요소는 맨 위에 놓는다.
     */
    DiaryCoverDesignElement createSticker(Long designId, Long userId, String stickerId);

    /**
     * 올린 사진을 한 장 붙인다. 사진 한 장이 요소 한 행이다.
     *
     * @param placedBefore 이번에 함께 올리는 사진 중 몇 번째인지. 여러 장을 한꺼번에 올려도
     *                     같은 자리에 정확히 겹치지 않도록 조금씩 어긋나게 놓는 데 쓴다.
     * @param photoStyle   사진의 모습. 어느 자리에서 올렸는지에 따라 붙는 순간 정해진다.
     *                     (일반 사진 → FULL, 폴라로이드 → POLAROID)
     * @param photoRatio   사진 원본의 가로/세로. 폴라로이드의 처음 상자 비율을 정하는 데 쓴다.
     *                     알 수 없으면 0 이하를 넘긴다. (그때는 정사각으로 본다)
     */
    DiaryCoverDesignElement createPhoto(Long designId, Long userId,
                                        String imageUrl, int placedBefore, String photoStyle,
                                        double photoRatio);

    /**
     * 라벨기로 글씨(TEXT)를 붙인다. 페이지 다꾸의 라벨기와 같은 규칙이다.
     *
     * <p>문구는 한 줄로 다듬어 저장하고 빈 문구는 막는다. 글꼴은 라벨 글꼴 목록에 있는
     * 값만, 글자색은 #RRGGBB 형식만 저장하며, 고르지 않았으면 비워 두고 화면이
     * 기본 글꼴·기본 먹색으로 그린다.
     * 새 요소는 스티커·사진과 마찬가지로 맨 위에 놓는다.
     */
    DiaryCoverDesignElement createLabel(Long designId, Long userId,
                                        String text, String textFont, String textColor);

    /**
     * 라벨기로 붙인 글씨를 뗀다.
     * 파일을 갖지 않으므로 DB 행만 지운다. 다른 유형의 요소 번호는 여기서 걸린다.
     */
    DiaryCoverDesignElement deleteLabel(Long designId, Long elementId, Long userId);

    /** 자리 옮기기 */
    DiaryCoverDesignElement move(Long designId, Long elementId, Long userId,
                                 BigDecimal positionX, BigDecimal positionY);

    /** 크기 바꾸기 */
    DiaryCoverDesignElement resize(Long designId, Long elementId, Long userId,
                                   BigDecimal width, BigDecimal height);

    /** 돌리기 */
    DiaryCoverDesignElement rotate(Long designId, Long elementId, Long userId,
                                   BigDecimal rotation);

    /**
     * 사진의 모습 바꾸기 (FULL / POLAROID).
     * 사진 요소에만 쓸 수 있고, 그 칸 하나만 바꾼다.
     * 자리/크기/각도/겹침 순서는 그대로 남는다.
     */
    DiaryCoverDesignElement changePhotoStyle(Long designId, Long elementId, Long userId,
                                             String photoStyle);

    /** 한 칸 앞으로/뒤로. 정리된 전체 순서를 돌려준다. */
    List<DiaryCoverDesignElement> changeLayer(Long designId, Long elementId, Long userId,
                                              boolean forward);

    /**
     * 요소 떼기. DB 행만 지운다.
     *
     * @return 그 요소가 갖고 있던 그림 경로. 사진이면 올린 파일을 정리해야 하므로
     *         호출한 쪽이 쓰고, 스티커는 공용 asset 이라 그대로 두면 된다.
     *         (무엇을 지울지는 elementType 으로 가른다)
     */
    DiaryCoverDesignElement delete(Long designId, Long elementId, Long userId);
}
