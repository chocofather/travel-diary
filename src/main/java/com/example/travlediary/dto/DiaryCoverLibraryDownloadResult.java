package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverDesign;

/**
 * 라이브러리 표지 받기 결과.
 *
 * @param design        새로 만들어진 내 보유 디자인
 * @param downloadCount 이번 처리까지 반영된 그 표지의 다운로드 수.
 *                      같은 회원이 다시 받은 경우에는 늘지 않은 값 그대로다.
 */
public record DiaryCoverLibraryDownloadResult(DiaryCoverDesign design, long downloadCount) {
}
