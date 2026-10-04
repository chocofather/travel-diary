package com.tripbora.service.wikidata;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Commons 파일의 라이선스 판별 규칙. 미리보기와 저장 전 재검증이 같은 규칙을 쓴다.
 *
 * <p>근거는 서버가 Commons API에서 받은 값뿐이다: extmetadata(License·LicenseShortName·LicenseUrl·
 * AttributionRequired·Copyrighted)와 파일 페이지에 실제로 쓰인 라이선스 템플릿. 클라이언트 값은 쓰지 않는다.</p>
 *
 * <ul>
 *   <li>CC BY / CC BY-SA 1.0~4.0(지역 포팅 포함)과 CC0 1.0: 템플릿 코드·표기명·URL의 유형·버전·지역이 서로 맞아야 한다.
 *       URL의 http/https·끝 슬래시·legalcode 차이는 같은 라이선스로 본다. URL이 없으면 템플릿 코드로 공식 URL을 만들고
 *       그 사실을 근거에 남긴다.</li>
 *   <li>퍼블릭 도메인: "Public domain" 표시만으로는 허용하지 않는다. 권리자가 전 세계에 포기했거나(PD-self 등)
 *       저작자 사후 70년 이상 지나 한국을 포함한 대부분 국가에서 보호기간이 끝났음을 밝히는 템플릿이 있어야 한다.
 *       미국 한정·창작성 기준·사망 연도 자동 계산 템플릿만 있으면 적용 범위가 불명확해 제외한다.</li>
 *   <li>Licensed-PD: 촬영 대상은 퍼블릭 도메인이고 사진 자체에는 별도 라이선스가 있다. 사진 라이선스 템플릿이
 *       정확히 하나일 때 그 CC 라이선스로 판별한다.</li>
 * </ul>
 */
final class CommonsLicenseRules {

    static final String ELIGIBLE = "ELIGIBLE";
    static final String LICENSE_EVIDENCE_MISSING = "LICENSE_EVIDENCE_MISSING";
    static final String RIGHTS_UNCLEAR = "RIGHTS_UNCLEAR";
    static final String UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT";
    static final String RESTRICTED = "RESTRICTED";

    /**
     * 권리자가 전 세계에 포기했거나 사후 70년 이상 지나 보호기간이 끝난 근거.
     * PD-old-auto-expired 는 "사후 100년 이하 보호 국가에서 퍼블릭 도메인 + 1931년 이전 공표"를 뜻한다.
     */
    static final Set<String> PD_WORLDWIDE = Set.of(
            "PD-self", "PD-author", "PD-user",
            "PD-old-70", "PD-old-70-expired", "PD-old-80", "PD-old-80-expired",
            "PD-old-90", "PD-old-90-expired", "PD-old-100", "PD-old-100-expired", "PD-old-auto-expired");
    static final Set<String> PD_US_ONLY = Set.of(
            "PD-US", "PD-US-expired", "PD-US-no-notice", "PD-US-not-renewed", "PD-USGov", "PD-USGov-NASA");
    static final Set<String> PD_THRESHOLD = Set.of("PD-textlogo", "PD-shape", "PD-ineligible", "PD-simple");
    /** 사망 연도로 보호기간을 계산하는 템플릿. 템플릿 이름만으로는 몇 년이 지났는지 알 수 없다. */
    static final Set<String> PD_OLD_AUTO = Set.of("PD-old-auto");
    static final String LICENSED_PD = "Licensed-PD";
    /** Licensed-PD 의 사진 라이선스로 인정하는 단일 CC 템플릿. */
    static final Set<String> PHOTO_CC_TEMPLATES = Set.of(
            "Cc-by-1.0", "Cc-by-2.0", "Cc-by-2.5", "Cc-by-3.0", "Cc-by-4.0",
            "Cc-by-sa-1.0", "Cc-by-sa-2.0", "Cc-by-sa-2.5", "Cc-by-sa-3.0", "Cc-by-sa-4.0", "Cc-zero");
    /** Commons API tltemplates 로 요청할 템플릿(최대 50개). 판별에 쓰는 템플릿만 받아 응답을 작게 유지한다. */
    static final List<String> REQUESTED_TEMPLATES = requestedTemplates();

    private static final Set<String> CC_VERSIONS = Set.of("1.0", "2.0", "2.1", "2.5", "3.0", "4.0");
    private static final Pattern CC_CODE = Pattern.compile("cc-(by|by-sa)-(\\d\\.\\d)(?:-(migrated|[a-z]{2,3}))?");
    private static final Pattern CC_URL = Pattern.compile(
            "https?://creativecommons\\.org/licenses/(by|by-sa)/(\\d\\.\\d)(?:/([a-z]{2,3}))?"
                    + "(?:/(?:(?:legalcode|deed)(?:\\.[a-z-]+)?)?)?/?");
    private static final Pattern CC_SHORT = Pattern.compile(
            "cc[ -]by(-sa)?[ -](\\d\\.\\d)(?:[ -]([a-z]{2,3}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CC0_URL = Pattern.compile(
            "https?://creativecommons\\.org/publicdomain/zero/1\\.0(?:/(?:(?:legalcode|deed)(?:\\.[a-z-]+)?)?)?/?");

    private CommonsLicenseRules() {
    }

    /**
     * @param category ELIGIBLE 이면 자동 저장 가능. 아니면 reason 에 구체 사유가 있다.
     * @param evidence 판단 근거. 저장 시 license_evidence_detail 에 남긴다.
     */
    record Decision(String category, String reason, String licenseType, String licenseName, String licenseVersion,
                    String licenseUrl, boolean attributionRequired, boolean changesRequired,
                    boolean shareAlikeRequired, String conditions, String evidence) {
        boolean eligible() {
            return ELIGIBLE.equals(category);
        }
    }

    static Decision decide(String licenseCode, String shortName, String licenseUrl, String attributionFlag,
                           String copyrighted, Set<String> templates) {
        String code = licenseCode == null ? "" : licenseCode.strip().toLowerCase(Locale.ROOT);
        Set<String> used = templates == null ? Set.of() : templates;
        if (CC_CODE.matcher(code).matches()) {
            if (licenseUrl == null && !used.contains(templateName(code))) {
                return blocked(LICENSE_EVIDENCE_MISSING, "라이선스 URL이 없고 파일 페이지에서 라이선스 템플릿("
                        + templateName(code) + ")도 확인하지 못했습니다.");
            }
            return creativeCommons(code, shortName, licenseUrl, attributionFlag, "License=" + code);
        }
        if (Set.of("cc0", "cc-zero", "cc0-1.0").contains(code)) {
            if (licenseUrl == null && !used.contains("Cc-zero")) {
                return blocked(LICENSE_EVIDENCE_MISSING, "라이선스 URL이 없고 파일 페이지에서 CC0 템플릿(Cc-zero)도 확인하지 못했습니다.");
            }
            return cc0(licenseUrl, "License=" + code);
        }
        if (code.equals("pd") || code.startsWith("pd-")) {
            return publicDomain(code, copyrighted, used);
        }
        return blocked(LICENSE_EVIDENCE_MISSING, code.isEmpty()
                ? "Commons 메타데이터에 라이선스 표시가 없습니다."
                : "자동 저장을 지원하지 않는 라이선스입니다(" + code + "). CC BY·CC BY-SA·CC0·확인된 퍼블릭 도메인만 저장합니다.");
    }

    private static Decision creativeCommons(String code, String shortName, String licenseUrl, String attributionFlag,
                                            String evidencePrefix) {
        Matcher codeMatch = CC_CODE.matcher(code);
        codeMatch.matches();
        boolean shareAlike = codeMatch.group(1).equals("by-sa");
        String version = codeMatch.group(2);
        String jurisdiction = "migrated".equals(codeMatch.group(3)) ? null : codeMatch.group(3);
        String label = (shareAlike ? "CC BY-SA " : "CC BY ") + version + (jurisdiction == null ? "" : " " + jurisdiction);
        if (!CC_VERSIONS.contains(version) || (version.equals("4.0") && jurisdiction != null)) {
            return blocked(LICENSE_EVIDENCE_MISSING, "알 수 없는 CC 라이선스 버전입니다(" + label + ").");
        }
        String canonicalUrl = "https://creativecommons.org/licenses/" + (shareAlike ? "by-sa/" : "by/") + version + "/"
                + (jurisdiction == null ? "" : jurisdiction + "/");
        StringBuilder evidence = new StringBuilder(evidencePrefix);
        if (shortName != null) {
            Matcher shortMatch = CC_SHORT.matcher(shortName.strip());
            if (shortMatch.matches() && (shortMatch.group(1) != null) == shareAlike) {
                String shortJurisdiction = shortMatch.group(3) == null ? null : shortMatch.group(3).toLowerCase(Locale.ROOT);
                if (!shortMatch.group(2).equals(version) || !Objects.equals(shortJurisdiction, jurisdiction)) {
                    return blocked(LICENSE_EVIDENCE_MISSING, "라이선스 표기(" + shortName + ")와 템플릿(" + label + ")의 버전이 다릅니다.");
                }
            } else if (shortMatch.matches()) {
                return blocked(LICENSE_EVIDENCE_MISSING, "라이선스 표기(" + shortName + ")와 템플릿(" + label + ")의 유형이 다릅니다.");
            }
            evidence.append(" · LicenseShortName=").append(shortName);
        }
        if (licenseUrl != null) {
            Matcher urlMatch = CC_URL.matcher(licenseUrl.strip());
            if (!urlMatch.matches() || urlMatch.group(1).equals("by-sa") != shareAlike
                    || !urlMatch.group(2).equals(version) || !Objects.equals(urlMatch.group(3), jurisdiction)) {
                return blocked(LICENSE_EVIDENCE_MISSING, "라이선스 이름(" + label + ")과 라이선스 URL(" + licenseUrl + ")이 서로 다릅니다.");
            }
            evidence.append(" · LicenseUrl=").append(licenseUrl);
        } else {
            evidence.append(" · LicenseUrl 없음: 파일 페이지의 Template:").append(templateName(code))
                    .append("으로 공식 URL 확인");
        }
        if ("false".equalsIgnoreCase(attributionFlag)) {
            return blocked(LICENSE_EVIDENCE_MISSING, "저작자 표시가 필요 없다고 표시돼 " + label + " 조건과 맞지 않습니다.");
        }
        return new Decision(ELIGIBLE, null, shareAlike ? "CC_BY_SA" : "CC_BY", label, version, canonicalUrl,
                true, true, shareAlike,
                shareAlike ? "저작자·출처·라이선스 표시, 변경사항 표시, 수정물 동일조건변경허락 필요"
                        : "저작자·출처·라이선스 표시 및 변경사항 표시 필요",
                evidence.toString());
    }

    private static Decision cc0(String licenseUrl, String evidencePrefix) {
        StringBuilder evidence = new StringBuilder(evidencePrefix);
        if (licenseUrl != null) {
            if (!CC0_URL.matcher(licenseUrl.strip()).matches()) {
                return blocked(LICENSE_EVIDENCE_MISSING, "CC0 표시와 라이선스 URL(" + licenseUrl + ")이 서로 다릅니다.");
            }
            evidence.append(" · LicenseUrl=").append(licenseUrl);
        } else {
            evidence.append(" · LicenseUrl 없음: 파일 페이지의 Template:Cc-zero로 공식 URL 확인");
        }
        return new Decision(ELIGIBLE, null, "CC0", "CC0 1.0", "1.0",
                "https://creativecommons.org/publicdomain/zero/1.0/", false, false, false,
                "저작자 표시와 변경사항 표시 의무는 없지만 출처 표시를 권장합니다.", evidence.toString());
    }

    /** extmetadata License 코드(cc-by-sa-3.0)에 대응하는 Commons 템플릿 이름(Cc-by-sa-3.0). */
    private static String templateName(String code) {
        return "C" + code.substring(1);
    }

    private static Decision publicDomain(String code, String copyrighted, Set<String> used) {
        if (used.contains(LICENSED_PD)) {
            // 촬영 대상만 퍼블릭 도메인이고 사진에는 별도 라이선스가 있다.
            Set<String> photoLicenses = new TreeSet<>(used);
            photoLicenses.retainAll(PHOTO_CC_TEMPLATES);
            if (photoLicenses.size() != 1) {
                return blocked(RIGHTS_UNCLEAR, "촬영 대상은 퍼블릭 도메인이지만 사진 자체의 라이선스를 하나로 확정할 수 없습니다"
                        + (photoLicenses.isEmpty() ? "." : "(" + String.join(", ", photoLicenses) + ")."));
            }
            String template = photoLicenses.iterator().next();
            String evidence = "Licensed-PD: 촬영 대상은 퍼블릭 도메인, 사진 라이선스는 Template:" + template;
            return template.equals("Cc-zero") ? cc0(null, evidence)
                    : creativeCommons(template.toLowerCase(Locale.ROOT), null, null, null, evidence);
        }
        if (!"false".equalsIgnoreCase(copyrighted)) {
            return blocked(RIGHTS_UNCLEAR, "저작권 상태(Copyrighted) 표시가 퍼블릭 도메인과 맞지 않습니다.");
        }
        Set<String> worldwide = new TreeSet<>(used);
        worldwide.retainAll(PD_WORLDWIDE);
        if (!worldwide.isEmpty()) {
            return new Decision(ELIGIBLE, null, "PUBLIC_DOMAIN", "Public domain", null, null, false, false, false,
                    "퍼블릭 도메인(" + String.join(", ", worldwide) + ") · 저작자·변경사항 표시 의무는 없지만 출처 표시를 권장합니다.",
                    "License=" + code + " · Copyrighted=False · 퍼블릭 도메인 근거 템플릿: " + String.join(", ", worldwide)
                            + (used.contains("PD-Art") ? " · PD-Art(평면 작품의 충실한 복제)" : ""));
        }
        if (used.stream().anyMatch(PD_US_ONLY::contains)) {
            return blocked(RIGHTS_UNCLEAR, "미국 기준 퍼블릭 도메인으로만 확인돼 국외 적용 범위가 불명확합니다.");
        }
        if (used.stream().anyMatch(PD_OLD_AUTO::contains)) {
            return blocked(RIGHTS_UNCLEAR, "저작자 사망 연도로 계산한 퍼블릭 도메인(PD-old-auto)이라 한국(사후 70년) 보호기간 만료 여부를 확인할 수 없습니다.");
        }
        if (used.stream().anyMatch(PD_THRESHOLD::contains)) {
            return blocked(RIGHTS_UNCLEAR, "창작성이 낮아 퍼블릭 도메인으로 본 파일이라 나라마다 판단이 다를 수 있습니다.");
        }
        return blocked(LICENSE_EVIDENCE_MISSING, "'Public domain' 표시만 있고 구체적인 퍼블릭 도메인 근거(템플릿)를 확인하지 못했습니다.");
    }

    /** Commons Restrictions 코드를 관리자가 이해할 수 있는 사유로 바꾼다. */
    static String restrictionReason(String restrictions) {
        List<String> labels = java.util.Arrays.stream(restrictions.split("[|,]"))
                .map(String::strip).filter(value -> !value.isEmpty())
                .map(value -> switch (value.toLowerCase(Locale.ROOT)) {
                    case "personality" -> "초상권·인격권 주의(personality)";
                    case "trademarked" -> "상표 포함(trademarked)";
                    case "ita-mibac" -> "이탈리아 문화재 이미지 이용 제한(ita-mibac)";
                    case "insignia" -> "휘장·기장 사용 제한(insignia)";
                    default -> value;
                }).toList();
        return "이용 제한 표시가 있어 자동 저장하지 않습니다: " + String.join(", ", labels);
    }

    private static Decision blocked(String category, String reason) {
        return new Decision(category, reason, "UNKNOWN", null, null, null, false, false, false,
                "라이선스 조건을 원본 파일 페이지에서 확인해 주세요.", null);
    }

    private static List<String> requestedTemplates() {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(PD_WORLDWIDE);
        names.addAll(PD_US_ONLY);
        names.addAll(PD_THRESHOLD);
        names.addAll(PD_OLD_AUTO);
        names.addAll(PHOTO_CC_TEMPLATES);
        names.add(LICENSED_PD);
        names.add("PD-Art");
        return names.stream().map(name -> "Template:" + name).toList();
    }
}
