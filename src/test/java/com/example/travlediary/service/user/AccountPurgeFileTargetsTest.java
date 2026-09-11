package com.example.travlediary.service.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FILE_DELETE task 로 남길 수 있는 경로인지의 계약.
 * 나중에 파일을 지우는 worker 가 target_value 를 그대로 믿어도 되도록 여기서 먼저 거른다.
 */
class AccountPurgeFileTargetsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/uploads/profiles/11111111-1111-4111-8111-111111111111.jpg",
            "/uploads/diary-covers/22222222-2222-4222-8222-222222222222.png",
            "/uploads/diary-pages/33333333-3333-4333-8333-333333333333.jpeg",
            "/uploads/diary-cover-designs/44444444-4444-4444-8444-444444444444.webp"
    })
    void personalUploadFoldersAreDeletable(String imageUrl) {
        assertThat(AccountPurgeFileTargets.normalizeDeletable(imageUrl)).isEqualTo(imageUrl);
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertThat(AccountPurgeFileTargets.normalizeDeletable(
                "  /uploads/profiles/a.jpg  ")).isEqualTo("/uploads/profiles/a.jpg");
    }

    /** 스티커는 업로드 파일이 아니라 서비스가 함께 배포하는 정적 리소스다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "/images/diary/stickers/travel/airplane.svg",
            "/images/diary/stickers/travel/suitcase.svg"
    })
    void staticStickerAssetsAreNeverDeletable(String imageUrl) {
        assertThat(AccountPurgeFileTargets.normalizeDeletable(imageUrl)).isNull();
    }

    /** 공개 콘텐츠와 관리자 콘텐츠 업로드 폴더는 탈퇴와 무관하게 남는다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "/uploads/posts/a.jpg",
            "/uploads/comments/a.jpg",
            "/uploads/editor/a.jpg",
            "/uploads/destinations/a.jpg",
            "/uploads/travel-info/festivals/a.jpg",
            "/uploads/a.jpg"
    })
    void publicAndAdminUploadFoldersAreNotDeletable(String imageUrl) {
        assertThat(AccountPurgeFileTargets.normalizeDeletable(imageUrl)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.test/uploads/profiles/a.jpg",
            "//example.test/uploads/profiles/a.jpg",
            "/uploads/profiles/../../../etc/passwd",
            "/uploads/profiles/nested/a.jpg",
            "/uploads/profiles/..",
            "/uploads/profiles/",
            "/uploads/profiles/a.jpg?x=1",
            "/uploads/profiles/a.jpg#f",
            "/uploads/profiles/a b.jpg",
            "/uploads/profiles/a\nb.jpg",
            "uploads/profiles/a.jpg",
            "   ",
            ""
    })
    void unsafeOrUnexpectedValuesAreRejected(String imageUrl) {
        assertThat(AccountPurgeFileTargets.normalizeDeletable(imageUrl)).isNull();
    }

    @Test
    void nullIsRejected() {
        assertThat(AccountPurgeFileTargets.normalizeDeletable(null)).isNull();
    }

    /** account_purge_tasks.target_value 컬럼 길이를 넘는 값은 저장 단계에서 잘리면 안 된다. */
    @Test
    void valuesLongerThanTheColumnAreRejected() {
        String tooLong = "/uploads/profiles/" + "a".repeat(1024);
        assertThat(AccountPurgeFileTargets.normalizeDeletable(tooLong)).isNull();
    }
}
