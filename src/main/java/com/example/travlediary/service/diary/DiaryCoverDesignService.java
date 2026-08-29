package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;

import java.util.List;

/**
 * 내 표지 디자인 보관함.
 * 모든 메서드가 userId 를 받아 본인 것만 다루게 한다. (다른 사용자의 designId 는 찾지 못한다)
 */
public interface DiaryCoverDesignService {

    /** 내 디자인 목록 (최근 손댄 것부터) */
    List<DiaryCoverDesign> getMyDesigns(Long userId);

    /** 내 디자인 1건 */
    DiaryCoverDesign getMyDesign(Long designId, Long userId);

    /** 요소가 없는 빈 디자인을 하나 만든다. (이름 / 바탕 표지 / 바탕색만 정한다) */
    DiaryCoverDesign create(Long userId, DiaryCoverDesign design);

    /**
     * 기본 정보(이름 / 바탕 표지 / 바탕색)를 한 번에 저장한다.
     * 편집 화면의 저장 버튼처럼 셋을 함께 고칠 때 쓴다. (한 트랜잭션이라 반만 저장되지 않는다)
     */
    DiaryCoverDesign updateBasics(Long designId, Long userId,
                                  String name, String baseCoverStyle, String backgroundColor);

    /** 이름만 바꾼다. */
    DiaryCoverDesign rename(Long designId, Long userId, String name);

    /** 바탕으로 쓸 기본 표지 스타일만 바꾼다. */
    DiaryCoverDesign changeBaseCoverStyle(Long designId, Long userId, String baseCoverStyle);

    /** 표지 바탕색만 바꾼다. (null 이면 기본색으로 되돌린다) */
    DiaryCoverDesign changeBackgroundColor(Long designId, Long userId, String backgroundColor);

    /** 디자인에 올린 요소 전체 (겹침 순서 그대로) */
    List<DiaryCoverDesignElement> getElements(Long designId, Long userId);

    /**
     * 디자인 삭제. 요소 행은 FK CASCADE 로 함께 지워진다.
     * 사진 파일 정리는 호출한 쪽(컨트롤러)의 몫이라, 지우기 전 요소 목록을 돌려준다.
     */
    List<DiaryCoverDesignElement> delete(Long designId, Long userId);
}
