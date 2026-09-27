package com.example.travlediary.dto.wikidata;

import java.util.List;

/**
 * Commons 사진 후보 한 묶음(처음 또는 '사진 더 보기').
 *
 * @param nextCursor     다음 묶음을 받을 때 그대로 돌려보내는 값. 더 볼 사진이 없으면 null
 * @param selectionLimit 한 번에 저장할 수 있는 사진 수. 저장 검증 한도와 같다
 */
public record CommonsPhotoPreview(String qid, String category, String status, String message,
                                  List<Photo> photos, String nextCursor, int selectionLimit) {
    public record Photo(String fileName, String source, boolean selectable, String thumbnailUrl,
                        String sourceImageUrl, String filePageUrl, int width, int height,
                        String author, String sourceCredit, String customAttribution,
                        List<CreditLink> creditLinks, String licenseType, String licenseName,
                        String licenseVersion, String licenseUrl, String reuseStatus,
                        boolean attributionRequired, boolean changesRequired,
                        boolean shareAlikeRequired, String conditions, String restrictions,
                        String reviewReason, boolean savable, String saveBlockReason,
                        String licenseEvidence) {
        // reuseStatus: ELIGIBLE, LICENSE_EVIDENCE_MISSING, RIGHTS_UNCLEAR, UNSUPPORTED_FORMAT, RESTRICTED
    }

    public record CreditLink(String label, String url) {
    }
}
