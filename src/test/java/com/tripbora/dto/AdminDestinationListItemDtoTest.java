package com.tripbora.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 관리자 목록 행의 번역 누락 언어와 상태 표시 여부. */
class AdminDestinationListItemDtoTest {

    @Test
    void missingLanguagesFollowTheFormSlotOrderAndIgnoreCase() {
        AdminDestinationListItemDto row = new AdminDestinationListItemDto();
        row.setCompleteTranslationLanguages("zh-cn,en");

        assertThat(row.getMissingTranslationLanguages()).containsExactly("ja", "zh-TW");
        assertThat(row.isMissingTranslation()).isTrue();
        assertThat(row.hasDataIssue()).isTrue();
    }

    @Test
    void noCompleteTranslationMeansAllFourLanguagesAreMissing() {
        AdminDestinationListItemDto row = new AdminDestinationListItemDto();

        assertThat(row.getMissingTranslationLanguages()).containsExactly("en", "ja", "zh-CN", "zh-TW");
    }

    /** 누락이 하나도 없으면 상태 표시를 붙이지 않는다. */
    @Test
    void completeRowHasNoDataIssue() {
        AdminDestinationListItemDto row = new AdminDestinationListItemDto();
        row.setCompleteTranslationLanguages("en,ja,zh-CN,zh-TW");

        assertThat(row.hasDataIssue()).isFalse();

        row.setMissingCategory(true);
        assertThat(row.hasDataIssue()).isTrue();
    }
}
