package com.tripbora.repository;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 등록폼 multipart part 수(한도 600)를 늘리지 않고 Commons 선택을 한 칸으로 보내는지 고정한다. */
class AdminWikidataCommonsSelectionUiContractTest {

    @Test
    void createFormSendsCommonsSelectionAsOneHiddenFieldWithoutImageBinary() throws IOException {
        Document page = Jsoup.parse(resource("/templates/admin/destinations/create.html"));

        var fields = page.select("input[data-commons-selection]");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().attr("type")).isEqualTo("hidden");
        assertThat(fields.first().attr("th:field")).isEqualTo("*{commonsSelectedPhotosJson}");
        assertThat(page.select("[data-wikidata-commons-area] input[type=file]")).isEmpty();
    }

    @Test
    void commonsCardsUseUnnamedControlsAndSerializeOnlyQidFileNameAndMain() throws IOException {
        String script = resource("/static/js/admin-wikidata-preview.js");
        String commons = script.substring(script.indexOf("function writeCommonsSelection()"),
                script.indexOf("function renderWikipedia("));
        String picker = resource("/static/js/admin-commons-photo-picker.js");

        assertThat(commons)
                .contains("JSON.stringify({qid: commonsSelection.qid, photos: commonsSelection.photos})")
                .contains("mainMode: 'auto'")
                .doesNotContain(".name =", "setAttribute('name'", "sourceImageUrl:", "licenseUrl:", "author:");
        // 등록폼과 이미지 관리 화면이 함께 쓰는 카드: 이름 없는 입력만 만들고 파일명·대표 여부만 담는다.
        assertThat(picker)
                .contains("next.push({fileName, main: false})")
                .contains("JSON.stringify({qid, photos})")
                .contains("const selectable = Boolean(photo.savable) && !alreadyRegistered")
                .contains("checkbox.disabled = !selectable")
                .doesNotContain(".name =", "setAttribute('name'");
        // 후보를 바꾸면 이전 QID의 사진 선택을 비운다.
        assertThat(script).contains("resetCommonsSelection(qid);");
    }

    @Test
    void imageManagementOffersCommonsAddOnlyForWikidataDestinationsWithOneSelectionField() throws IOException {
        Document page = Jsoup.parse(resource("/templates/admin/destinations/image-upload.html"));
        var section = page.selectFirst("[data-commons-add]");

        assertThat(section).isNotNull();
        assertThat(section.attr("th:if")).isEqualTo("${wikidataQid != null}");
        var form = section.selectFirst("form[data-commons-add-form]");
        assertThat(form.attr("th:action")).contains("/images/commons");
        assertThat(form.attr("method")).isEqualTo("post");
        assertThat(form.select("input[name]")).extracting(input -> input.attr("name"))
                .containsExactly("commonsSelectedPhotosJson");
        assertThat(section.select("input[type=file]")).isEmpty();
        assertThat(page.select("script[src^='/js/admin-commons-photo-picker.js']")).hasSize(1);
        assertThat(page.select("script[src^='/js/admin-destination-commons-add.js']")).hasSize(1);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
