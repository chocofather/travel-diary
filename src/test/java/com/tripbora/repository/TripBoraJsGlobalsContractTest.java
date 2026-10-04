package com.tripbora.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프론트엔드 전역 이름은 window.TripBora* 가 정식이다.
 *
 * <p>배포 정적 JS 는 최대 1시간 캐시되므로 새 HTML/JS 와 캐시된 옛 JS 가 섞일 수 있다.
 * 그래서 P10a 전까지는 제공하는 쪽이 같은 객체를 옛 이름(window.TravelDiary*)으로도 내보내고,
 * 쓰는 쪽은 새 이름을 먼저 찾고 옛 이름은 fallback 으로만 쓴다.
 *
 * <p>옛 이름 목록은 {@link #LEGACY_GLOBALS} 한 곳에 둔다. 호환 코드는 아래 두 표식이 붙은 줄에만 있다.
 * P10a 에서는 표식이 붙은 별칭 줄과 fallback 을 지우고, 이 테스트를 "TravelDiary 0건" 검사로 바꾼다.
 */
class TripBoraJsGlobalsContractTest {

    private static final Path JS_ROOT = Path.of("src/main/resources/static/js");
    private static final Path TEMPLATE_ROOT = Path.of("src/main/resources/templates");
    private static final String ALIAS_MARKER = "// legacy alias (P10a 제거)";
    private static final String FALLBACK_MARKER = "// legacy fallback (P10a 제거)";

    /** 전역 이름(TripBora/TravelDiary 뒤의 부분) → 그 전역을 내보내는 파일. */
    private static final Map<String, String> LEGACY_GLOBALS = Map.ofEntries(
            entry("AgeEligibility", "age-eligibility.js"),
            entry("BulkPageSelection", "admin-bulk-page-selection.js"),
            entry("CategorySelect", "admin-destination-category-select.js"),
            entry("CommentDeepLink", "comment-deep-link.js"),
            entry("CommonsPhotoPicker", "admin-commons-photo-picker.js"),
            entry("EmailDomain", "email-domain-suggestion.js"),
            entry("GuestDraftStore", "guest-diary-draft-store.js"),
            entry("GuestPhotoStore", "guest-diary-photo-store.js"),
            entry("HomeConverge", "home-converge-prototype.js"),
            entry("ImageBatchUpload", "admin-destination-image-batch-upload.js"),
            entry("ImageOrder", "admin-destination-image-order.js"),
            entry("NicknameAvailability", "nickname-availability.js"),
            entry("RegionSelector", "region-selector.js"),
            entry("SeasonPreview", "home-season-preview.js"),
            entry("WikidataApplyPlanner", "admin-wikidata-form-apply.js"),
            entry("WikidataBulkPlanner", "admin-wikidata-bulk-import.js"),
            entry("WikipediaDescriptionPlanner", "admin-wikipedia-description-apply.js"));

    private static final Pattern LEGACY_NAME = Pattern.compile("TravelDiary(\\w+)");
    private static final Pattern ALIAS_LINE = Pattern.compile(
            "^\\s*(root|global|window)\\.TravelDiary(\\w+) = \\1\\.TripBora\\2; "
                    + Pattern.quote(ALIAS_MARKER) + "$");
    private static final Pattern FALLBACK_PAIR = Pattern.compile(
            "(window|global)\\.TripBora(\\w+) \\|\\| \\1\\.TravelDiary\\2\\b");

    /** 제공하는 쪽은 새 이름을 정식으로 내보내고, 바로 그 객체를 옛 이름의 별칭으로 한 번만 건다. */
    @Test
    void providersExportTheTripBoraNameAndAliasTheSameObject() throws IOException {
        for (Map.Entry<String, String> global : LEGACY_GLOBALS.entrySet()) {
            String name = global.getKey();
            String js = read(JS_ROOT.resolve(global.getValue()));

            Matcher canonical = Pattern.compile("\\b(root|global|window)\\.TripBora" + name + " = ")
                    .matcher(js);
            assertThat(canonical.find()).as("%s 가 TripBora%s 를 내보낸다", global.getValue(), name).isTrue();
            String owner = canonical.group(1);
            String alias = owner + ".TravelDiary" + name + " = " + owner + ".TripBora" + name + "; "
                    + ALIAS_MARKER;

            assertThat(js).as(global.getValue()).contains(alias);
            assertThat(js.indexOf(alias)).as("별칭은 정식 export 뒤에 둔다").isGreaterThan(canonical.start());
            assertThat(count(js, ".TravelDiary" + name + " = ")).as("별칭은 한 번만").isEqualTo(1);
        }
    }

    /**
     * 옛 이름은 표식이 붙은 별칭 줄과 fallback 에만 나온다.
     * fallback 은 언제나 새 이름을 먼저 찾고, 목록에 없는 옛 이름은 새로 생기지 않는다.
     */
    @Test
    void legacyNamesAppearOnlyInMarkedCompatibilityCode() throws IOException {
        List<String> violations = new ArrayList<>();
        Set<String> aliasedNames = new TreeSet<>();
        Set<String> consumedNames = new TreeSet<>();

        for (Path file : jsFiles()) {
            String relative = JS_ROOT.relativize(file).toString().replace('\\', '/');
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.contains("TravelDiary")) {
                    continue;
                }
                String where = relative + ":" + (i + 1);
                Matcher legacy = LEGACY_NAME.matcher(line);
                while (legacy.find()) {
                    if (!LEGACY_GLOBALS.containsKey(legacy.group(1))) {
                        violations.add(where + " 목록에 없는 옛 이름 " + legacy.group());
                    }
                }

                Matcher alias = ALIAS_LINE.matcher(line);
                if (alias.matches()) {
                    String name = alias.group(2);
                    if (!relative.equals(LEGACY_GLOBALS.get(name))) {
                        violations.add(where + " 제공 파일이 아닌 곳의 별칭 " + name);
                    }
                    aliasedNames.add(name);
                    continue;
                }

                Matcher pair = FALLBACK_PAIR.matcher(line);
                int pairs = 0;
                while (pair.find()) {
                    consumedNames.add(pair.group(2));
                    pairs++;
                }
                if (!line.contains(FALLBACK_MARKER) || pairs == 0 || pairs != count(line, "TravelDiary")) {
                    violations.add(where + " 표식 없는 옛 이름 사용: " + line.strip());
                }
            }
        }

        assertThat(violations).isEmpty();
        assertThat(aliasedNames).containsExactlyInAnyOrderElementsOf(LEGACY_GLOBALS.keySet());
        assertThat(LEGACY_GLOBALS.keySet()).containsAll(consumedNames);
    }

    /** 템플릿 인라인 스크립트는 옛 이름을 쓰지 않는다. (호환 코드는 정적 JS 에만 둔다) */
    @Test
    void templatesDoNotUseLegacyGlobalNames() throws IOException {
        try (Stream<Path> templates = Files.walk(TEMPLATE_ROOT)) {
            for (Path template : templates.filter(Files::isRegularFile).toList()) {
                assertThat(read(template)).as(template.toString()).doesNotContain("TravelDiary");
            }
        }
    }

    private static List<Path> jsFiles() throws IOException {
        try (Stream<Path> files = Files.walk(JS_ROOT)) {
            return files.filter(path -> path.toString().endsWith(".js")).sorted().toList();
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
