package com.example.travlediary.service.wikidata;

import com.example.travlediary.dto.wikidata.CommonsPhotoPreview.CreditLink;

import java.util.List;

/**
 * Commons imageinfo 한 건을 해석한 전체 길이 메타데이터.
 * 미리보기는 이 값을 화면용으로 줄여 쓰고, 저장은 줄이지 않은 이 값을 그대로 쓴다.
 *
 * @param thumbnailUrl 요청한 폭의 Commons 렌디션 URL (미리보기 240px, 저장 1920px)
 * @param reuseStatus 판별 결과: ELIGIBLE, LICENSE_EVIDENCE_MISSING, RIGHTS_UNCLEAR, UNSUPPORTED_FORMAT, RESTRICTED
 * @param saveBlockReason 자동 저장을 막는 이유. null 이면 자동 저장 가능
 * @param licenseEvidence 자동 저장 가능으로 본 근거(라이선스 코드·URL·템플릿). 저장 시 출처 테이블에 남긴다
 */
record CommonsFileMetadata(
        String title,
        String fileName,
        String source,
        boolean selectable,
        String thumbnailUrl,
        String originalUrl,
        String filePageUrl,
        int width,
        int height,
        String mime,
        long pageId,
        long lastRevisionId,
        String author,
        String sourceCredit,
        String customAttribution,
        List<CreditLink> creditLinks,
        String licenseCode,
        String licenseType,
        String licenseName,
        String licenseVersion,
        String licenseUrl,
        String reuseStatus,
        boolean attributionRequired,
        boolean changesRequired,
        boolean shareAlikeRequired,
        String conditions,
        String restrictions,
        String reviewReason,
        String saveBlockReason,
        String licenseEvidence
) {
}
