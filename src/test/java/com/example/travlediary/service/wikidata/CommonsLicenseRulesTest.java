package com.example.travlediary.service.wikidata;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommonsLicenseRulesTest {

    @Test
    void ccVersionsAndUrlSpellingsAreRecognizedWhenTheyAgree() {
        var bySa3 = CommonsLicenseRules.decide("cc-by-sa-3.0", "CC BY-SA 3.0",
                "http://creativecommons.org/licenses/by-sa/3.0/", "true", "True", Set.of());
        assertThat(bySa3.eligible()).isTrue();
        assertThat(bySa3.licenseType()).isEqualTo("CC_BY_SA");
        assertThat(bySa3.licenseVersion()).isEqualTo("3.0");
        assertThat(bySa3.licenseUrl()).isEqualTo("https://creativecommons.org/licenses/by-sa/3.0/");
        assertThat(bySa3.shareAlikeRequired()).isTrue();

        assertThat(CommonsLicenseRules.decide("cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0/legalcode", null, null, Set.of()).eligible()).isTrue();
        assertThat(CommonsLicenseRules.decide("cc-by-2.5", "CC BY 2.5",
                "https://creativecommons.org/licenses/by/2.5", null, null, Set.of()).licenseName()).isEqualTo("CC BY 2.5");
        var ported = CommonsLicenseRules.decide("cc-by-sa-3.0-de", "CC BY-SA 3.0 de",
                "https://creativecommons.org/licenses/by-sa/3.0/de/deed.en", null, null, Set.of());
        assertThat(ported.eligible()).isTrue();
        assertThat(ported.licenseUrl()).isEqualTo("https://creativecommons.org/licenses/by-sa/3.0/de/");
        assertThat(CommonsLicenseRules.decide("cc-by-sa-3.0-migrated", "CC BY-SA 3.0",
                "https://creativecommons.org/licenses/by-sa/3.0/", null, null, Set.of()).eligible()).isTrue();
    }

    @Test
    void conflictingNameUrlOrUnknownVersionIsBlockedWithTheReason() {
        var urlConflict = CommonsLicenseRules.decide("cc-by-sa-3.0", "CC BY-SA 3.0",
                "https://creativecommons.org/licenses/by-sa/4.0/", null, null, Set.of());
        assertThat(urlConflict.category()).isEqualTo("LICENSE_EVIDENCE_MISSING");
        assertThat(urlConflict.reason()).contains("라이선스 URL");
        assertThat(CommonsLicenseRules.decide("cc-by-sa-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/", null, null, Set.of()).reason()).contains("유형이 다릅니다");
        assertThat(CommonsLicenseRules.decide("cc-by-4.0", "CC BY 4.0",
                "https://evil.example/licenses/by/4.0/", null, null, Set.of()).eligible()).isFalse();
        assertThat(CommonsLicenseRules.decide("cc-by-5.0", "CC BY 5.0",
                "https://creativecommons.org/licenses/by/5.0/", null, null, Set.of()).reason()).contains("알 수 없는");
        assertThat(CommonsLicenseRules.decide("cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0/", "false", null, Set.of()).eligible()).isFalse();
        assertThat(CommonsLicenseRules.decide("cc-by-nc-4.0", "CC BY-NC 4.0", null, null, null, Set.of()).reason())
                .contains("지원하지 않는 라이선스");
        assertThat(CommonsLicenseRules.decide(null, null, null, null, null, Set.of()).reason())
                .contains("라이선스 표시가 없습니다");
    }

    @Test
    void missingLicenseUrlIsAcceptedOnlyWithTheMatchingTemplate() {
        var withTemplate = CommonsLicenseRules.decide("cc-by-sa-4.0", "CC BY-SA 4.0", null, null, null,
                Set.of("Cc-by-sa-4.0"));
        assertThat(withTemplate.eligible()).isTrue();
        assertThat(withTemplate.licenseUrl()).isEqualTo("https://creativecommons.org/licenses/by-sa/4.0/");
        assertThat(withTemplate.evidence()).contains("Template:Cc-by-sa-4.0");
        assertThat(CommonsLicenseRules.decide("cc-by-sa-4.0", "CC BY-SA 4.0", null, null, null, Set.of())
                .category()).isEqualTo("LICENSE_EVIDENCE_MISSING");
        assertThat(CommonsLicenseRules.decide("cc-zero", "CC0", null, null, null, Set.of("Cc-zero")).licenseUrl())
                .isEqualTo("https://creativecommons.org/publicdomain/zero/1.0/");
        assertThat(CommonsLicenseRules.decide("cc-zero", "CC0", null, null, null, Set.of()).eligible()).isFalse();
        assertThat(CommonsLicenseRules.decide("cc-zero", "CC0",
                "https://creativecommons.org/licenses/by/4.0/", null, null, Set.of()).eligible()).isFalse();
    }

    @Test
    void publicDomainNeedsConcreteWorldwideEvidence() {
        var old = CommonsLicenseRules.decide("pd", "Public domain", null, null, "False",
                Set.of("PD-old-100-expired", "PD-Art"));
        assertThat(old.eligible()).isTrue();
        assertThat(old.licenseType()).isEqualTo("PUBLIC_DOMAIN");
        assertThat(old.evidence()).contains("PD-old-100-expired", "PD-Art");
        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, "False", Set.of("PD-self"))
                .eligible()).isTrue();

        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, "False", Set.of()).reason())
                .contains("'Public domain' 표시만");
        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, "False", Set.of("PD-USGov")))
                .extracting("category", "reason")
                .containsExactly("RIGHTS_UNCLEAR", "미국 기준 퍼블릭 도메인으로만 확인돼 국외 적용 범위가 불명확합니다.");
        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, "False", Set.of("PD-old-auto"))
                .reason()).contains("사후 70년");
        // PD-old-auto-expired 는 "사후 100년 이하 국가에서 퍼블릭 도메인"이라 한국 기준도 충족한다.
        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, "False",
                Set.of("PD-old-auto", "PD-old-auto-expired")).eligible()).isTrue();
        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, "False", Set.of("PD-textlogo"))
                .category()).isEqualTo("RIGHTS_UNCLEAR");
        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, "True", Set.of("PD-self"))
                .category()).isEqualTo("RIGHTS_UNCLEAR");
    }

    @Test
    void licensedPdUsesThePhotoLicenseNotTheSubjectStatus() {
        var photo = CommonsLicenseRules.decide("pd", "Public domain", null, null, null,
                Set.of("Licensed-PD", "PD-old-70", "Cc-by-sa-3.0"));
        assertThat(photo.eligible()).isTrue();
        assertThat(photo.licenseType()).isEqualTo("CC_BY_SA");
        assertThat(photo.licenseName()).isEqualTo("CC BY-SA 3.0");
        assertThat(photo.attributionRequired()).isTrue();
        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, null,
                Set.of("Licensed-PD", "Cc-by-sa-3.0", "Cc-by-4.0")).category()).isEqualTo("RIGHTS_UNCLEAR");
        assertThat(CommonsLicenseRules.decide("pd", "Public domain", null, null, null,
                Set.of("Licensed-PD", "PD-old-70")).category()).isEqualTo("RIGHTS_UNCLEAR");
    }

    @Test
    void restrictionCodesAreShownAsTheActualRestriction() {
        assertThat(CommonsLicenseRules.restrictionReason("ita-mibac|personality"))
                .contains("ita-mibac", "personality");
        assertThat(CommonsLicenseRules.REQUESTED_TEMPLATES).hasSizeLessThanOrEqualTo(50)
                .allMatch(name -> name.startsWith("Template:"));
    }
}
