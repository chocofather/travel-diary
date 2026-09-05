package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 상세 두 화면(일반 여행정보 / 축제·행사)의 고정 문구가 messages 로 옮겨졌는지 본다.
 *
 * <p>제목·본문·카테고리·행사 상세정보처럼 DB 에서 언어별로 오는 값은 messages 대상이 아니다.
 */
class TravelInfoDetailMessagesContractTest {

    private static final String DETAIL = "/templates/travel-info/detail.html";
    private static final String FESTIVAL_DETAIL = "/templates/festivals/detail.html";

    /** 상세 화면이 새로 쓰는 키. 여섯 번들에 모두 있어야 한다. */
    private static final List<String> KEYS = List.of(
            "travelInfo.contentType.festival",
            "travelInfo.detail.createdAt", "travelInfo.detail.views",
            "travelInfo.detail.updatedAt", "travelInfo.detail.periods",
            "travelInfo.detail.content", "travelInfo.detail.backToList",
            "travelInfo.detail.pageTitle.general", "travelInfo.detail.pageTitle.festival",
            "travelInfo.festival.section.introduction",
            "travelInfo.festival.section.information",
            "travelInfo.festival.info.period", "travelInfo.festival.info.playTime",
            "travelInfo.festival.info.eventPlace", "travelInfo.festival.info.useTime",
            "travelInfo.festival.info.address",
            "travelInfo.festival.info.sponsor1", "travelInfo.festival.info.sponsor2",
            "travelInfo.festival.info.contact", "travelInfo.festival.info.homepage",
            "travelInfo.festival.info.officialHomepage",
            "travelInfo.festival.meta.registered",
            "travelInfo.festival.image.alt", "travelInfo.festival.image.zoom",
            "travelInfo.festival.image.defaultAlt", "travelInfo.festival.image.none",
            "travelInfo.festival.image.previous", "travelInfo.festival.image.next",
            "travelInfo.festival.image.modal", "travelInfo.festival.image.close",
            "travelInfo.festival.image.source",
            "travelInfo.festival.image.license.KOGL_TYPE_1",
            "travelInfo.festival.image.license.KOGL_TYPE_3");

    @ParameterizedTest
    @ValueSource(strings = {"", "_ko", "_en", "_ja", "_zh_CN", "_zh_TW"})
    void everyBundleCarriesEveryDetailKey(String suffix) throws IOException {
        Properties bundle = bundle("/messages" + suffix + ".properties");

        for (String key : KEYS) {
            assertThat(bundle.getProperty(key)).as("%s in messages%s", key, suffix)
                    .isNotNull().isNotBlank();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"_en", "_ja", "_zh_CN", "_zh_TW"})
    void translatedBundlesDoNotJustRepeatTheKoreanDetailLabels(String suffix) throws IOException {
        Properties korean = bundle("/messages_ko.properties");
        Properties translated = bundle("/messages" + suffix + ".properties");

        for (String key : List.of("travelInfo.detail.createdAt", "travelInfo.detail.backToList",
                "travelInfo.festival.section.introduction",
                "travelInfo.festival.section.information",
                "travelInfo.festival.info.eventPlace", "travelInfo.festival.info.address",
                "travelInfo.festival.info.sponsor1", "travelInfo.festival.info.sponsor2",
                "travelInfo.festival.image.close",
                "travelInfo.festival.image.license.KOGL_TYPE_1")) {
            assertThat(translated.getProperty(key)).as("%s in messages%s", key, suffix)
                    .isNotEqualTo(korean.getProperty(key));
        }
    }

    @Test
    void theGeneralDetailNoLongerCarriesItsOwnKoreanLabels() throws IOException {
        String template = resource(DETAIL);

        // 남은 한글은 th:text 가 덮어쓰는 정적 예시 값과 주석뿐이다.
        // 라벨을 그리는 자리(dt·aria-label)에 고정 한국어가 남아 있지 않은지 본다.
        assertThat(count(template, "<dt")).isEqualTo(count(template, "<dt th:text=\"#{"));
        assertThat(template)
                .doesNotContain("'국내' : '해외'")
                .doesNotContain("'일반' : '축제'")
                .doesNotContain("'저장됨' : '저장'")
                .doesNotContain("aria-label=\"여행정보")
                .doesNotContain("aria-label=\"본문")
                .contains("#{travelInfo.detail.createdAt}", "#{travelInfo.detail.views}",
                        "#{travelInfo.detail.updatedAt}", "#{travelInfo.detail.periods}",
                        "#{travelInfo.detail.content}", "#{travelInfo.detail.backToList}",
                        "#{travelInfo.contentType.festival}");
        // 제목·카테고리·본문은 DB 번역 결과 그대로다.
        assertThat(template)
                .contains("${travelInfo.title}", "${travelInfo.categoryName}",
                        "th:utext=\"${travelInfo.content}\"");
    }

    @Test
    void theFestivalDetailNoLongerCarriesItsOwnKoreanLabels() throws IOException {
        String template = resource(FESTIVAL_DETAIL);

        // 라벨 자리(dt)와 화면 낭독용 문구(aria-label)에 고정 한국어가 남아 있지 않다.
        assertThat(count(template, "<dt")).isEqualTo(count(template, "<dt th:text=\"#{"));
        assertThat(count(template, "aria-label="))
                .isEqualTo(count(template, "aria-label=#{") + count(template, "aria-label=${"));
        assertThat(template)
                .contains("#{travelInfo.festival.section.introduction}",
                        "#{travelInfo.festival.section.information}",
                        "#{travelInfo.festival.info.period}",
                        "#{travelInfo.festival.info.playTime}",
                        "#{travelInfo.festival.info.eventPlace}",
                        "#{travelInfo.festival.info.useTime}",
                        "#{travelInfo.festival.info.address}",
                        "#{travelInfo.festival.info.sponsor1}",
                        "#{travelInfo.festival.info.sponsor2}",
                        "#{travelInfo.festival.info.contact}",
                        "#{travelInfo.festival.info.homepage}",
                        "#{travelInfo.festival.info.officialHomepage}",
                        "#{travelInfo.festival.meta.registered}",
                        "#{travelInfo.detail.backToList}");
        // 행사 상세정보 값은 festival_info_translations 결과를 그대로 쓴다.
        assertThat(template)
                .contains("${festival.eventPlace}", "${festival.address}",
                        "${festival.playTime}", "${festival.useTime}",
                        "${festival.sponsor1}", "${festival.sponsor2}",
                        "${festival.contactTel}", "${festival.sponsor1Tel}",
                        "${festival.homepageUrl}");
    }

    @Test
    void galleryTextUsesMessageParametersInsteadOfStringConcatenation() throws IOException {
        for (String suffix : new String[]{"", "_ko", "_en", "_ja", "_zh_CN", "_zh_TW"}) {
            Properties bundle = bundle("/messages" + suffix + ".properties");
            assertThat(bundle.getProperty("travelInfo.festival.image.alt"))
                    .as("alt in messages%s", suffix).contains("{0}", "{1}");
            assertThat(bundle.getProperty("travelInfo.festival.image.zoom"))
                    .as("zoom in messages%s", suffix).contains("{0}", "{1}");
            assertThat(bundle.getProperty("travelInfo.detail.pageTitle.general"))
                    .as("pageTitle in messages%s", suffix).contains("{0}");
        }
        assertThat(resource(FESTIVAL_DETAIL))
                .contains("#{travelInfo.festival.image.alt(")
                .contains("#{travelInfo.festival.image.zoom(")
                .contains("${festival.travelInfo.title}, ${imageStat.count}");
    }

    @Test
    void theImageModalTakesItsWordingFromTheScreen() throws IOException {
        assertThat(resource(FESTIVAL_DETAIL))
                .contains("aria-label=#{travelInfo.festival.image.modal}")
                .contains("data-default-alt=#{travelInfo.festival.image.defaultAlt}")
                .contains("aria-label=#{travelInfo.festival.image.close}")
                .contains("aria-label=#{travelInfo.festival.image.previous}")
                .contains("aria-label=#{travelInfo.festival.image.next}");
        assertThat(resource("/static/js/festival-gallery.js"))
                .contains("modal?.dataset.defaultAlt")
                .doesNotContain("'축제 이미지'");
    }

    @Test
    void bothDetailScreensHandTheBookmarkScriptItsWording() throws IOException {
        for (String path : new String[]{DETAIL, FESTIVAL_DETAIL}) {
            assertThat(resource(path)).as(path)
                    .contains("data-label-save=#{travelInfo.bookmark.label.save}")
                    .contains("data-label-saved=#{travelInfo.bookmark.label.saved}")
                    .contains("data-aria-save=#{travelInfo.bookmark.save}")
                    .contains("data-aria-remove=#{travelInfo.bookmark.remove}")
                    .contains("data-failed-message=#{travelInfo.bookmark.failed}");
        }
        // 세 화면이 모두 문구를 실어 주므로 스크립트에 한국어 기본값이 남아 있지 않다.
        // (남은 한국어는 콘솔에만 찍히는 Error 문구다.)
        assertThat(resource("/static/js/travel-info-bookmark.js"))
                .doesNotContain("'저장됨'", "'저장'", "'북마크 처리에 실패했습니다.'")
                .doesNotContain("'여행정보 저장'", "'여행정보 저장 취소'");
    }

    @Test
    void koglNamesLiveInMessagesInsteadOfJavaCode() throws IOException {
        assertThat(file("src/main/java/com/example/travlediary/dto/FestivalGalleryImageDto.java"))
                .contains("getLicenseCode", "\"KOGL_TYPE_1\"", "\"KOGL_TYPE_3\"")
                .doesNotContain("공공누리", "getLicenseLabel");
        assertThat(file("src/main/java/com/example/travlediary/dto/FestivalDetailDto.java"))
                .contains("getLicenseCode")
                .doesNotContain("공공누리", "getLicenseLabel");
        // 화면이 코드로 messages 키를 만들어 이름을 고른다.
        assertThat(resource(FESTIVAL_DETAIL))
                .contains("#{'travelInfo.festival.image.license.' + ${image.licenseCode}}")
                .contains("${firstLicenseName}")
                .doesNotContain("licenseLabel=${");
    }

    @Test
    void theControllerBuildsTabTitlesFromMessages() throws IOException {
        String controller = file("src/main/java/com/example/travlediary/controller/"
                + "travelinfo/TravelInfoController.java");

        assertThat(controller)
                .contains("travelInfo.detail.pageTitle.general")
                .contains("travelInfo.detail.pageTitle.festival")
                .doesNotContain("\" | 여행정보\"", "\" | 축제·행사\"");
    }

    private Properties bundle(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private int count(String text, String token) {
        int total = 0;
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
            total++;
        }
        return total;
    }

    private String file(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
