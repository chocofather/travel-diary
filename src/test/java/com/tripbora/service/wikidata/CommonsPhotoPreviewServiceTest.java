package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonsPhotoPreviewServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WikidataApiClient wikidata = mock(WikidataApiClient.class);
    private final CommonsApiClient commons = mock(CommonsApiClient.class);
    private final WikidataAutofillCache cache = new WikidataAutofillCache(wikidata);
    private final CommonsPhotoPreviewService service = new CommonsPhotoPreviewService(wikidata, commons, cache);

    @Test
    void prioritizesP18DeduplicatesAndKeepsAttributionLinksAsSafeData() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        ObjectNode tour = page("File:Tour Eiffel.jpg", "cc-by-sa-4.0", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0",
                "<a href=\"//commons.wikimedia.org/wiki/User:Artist\">Artist</a><script>alert(1)</script>");
        ObjectNode other = page("File:Other view.jpg", "cc-by-3.0", "CC BY 3.0",
                "https://creativecommons.org/licenses/by/3.0", "Other Artist");
        when(commons.getImageInfo(List.of("File:Tour Eiffel.jpg"))).thenReturn(imageInfo(tour));
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull())).thenReturn(categoryInfo(
                List.of("File:Tour_Eiffel.jpg", "File:Other view.jpg"), tour, other));

        var result = service.preview("Q243");

        assertThat(result.photos()).hasSize(2);
        assertThat(result.photos().get(0).source()).isEqualTo("P18");
        assertThat(result.photos().get(0).licenseType()).isEqualTo("CC_BY_SA");
        assertThat(result.photos().get(0).reuseStatus()).isEqualTo("ELIGIBLE");
        assertThat(result.photos().get(0).author()).isEqualTo("Artist");
        assertThat(result.photos().get(0).creditLinks()).extracting("url")
                .contains("https://commons.wikimedia.org/wiki/User:Artist");
        assertThat(result.photos().get(0).author()).doesNotContain("<", "alert");
        assertThat(result.photos().get(1).source()).isEqualTo("CATEGORY");
        assertThat(result.photos().get(1).changesRequired()).isTrue();
        assertThat(result.photos().get(1).shareAlikeRequired()).isFalse();
        // 전체 claims(수백 KB)나 카테고리 목록·파일 정보를 따로 받지 않는다.
        verify(wikidata, never()).getAutofillEntities(anyList());
        verify(commons, never()).listCategoryFiles(anyString());
    }

    @Test
    void unknownLicenseAndBadImageUrlCannotBeTreatedAsReusable() throws Exception {
        stubPhotoEntity("Unknown.jpg", null);
        ObjectNode bad = page("File:Unknown.jpg", "custom", "Custom", null, "Unknown Artist");
        ((ObjectNode) bad.path("imageinfo").get(0)).put("thumburl", "https://evil.example/image.jpg");
        when(commons.getImageInfo(anyList())).thenReturn(imageInfo(bad));

        var result = service.preview("Q243");

        assertThat(result.photos()).hasSize(1);
        // 형식·URL 문제는 라이선스 판별보다 앞서 표시한다.
        assertThat(result.photos().get(0).reuseStatus()).isEqualTo("UNSUPPORTED_FORMAT");
        assertThat(result.photos().get(0).savable()).isFalse();
        assertThat(result.photos().get(0).selectable()).isFalse();
        assertThat(result.photos().get(0).thumbnailUrl()).isNull();
        verify(commons, never()).getCategoryImageInfo(anyString(), anyInt(), any());
    }

    @Test
    void categoryApiFailureStillReturnsRepresentativePhotoAndIsNotCached() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull()))
                .thenThrow(new CommonsApiException("Commons 요청이 많습니다. 잠시 후 다시 시도해 주세요."));
        when(commons.getImageInfo(anyList())).thenReturn(imageInfo(page(
                "File:Tour Eiffel.jpg", "cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0", "Artist")));

        var result = service.preview("Q243");
        service.preview("Q243");

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.photos()).hasSize(1);
        assertThat(result.photos().get(0).source()).isEqualTo("P18");
        assertThat(result.message()).contains("잠시 후");
        // 일부 실패한 응답은 캐시하지 않고 다음 조회에서 다시 시도한다.
        verify(commons, times(2)).getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull());
    }

    @Test
    void cc0AndPublicDomainHaveDifferentReuseDecisions() throws Exception {
        stubPhotoEntity("Zero.jpg", "Eiffel Tower");
        ObjectNode cc0 = page("File:Zero.jpg", "cc-zero", "CC0 1.0",
                "https://creativecommons.org/publicdomain/zero/1.0", "Artist");
        ObjectNode pd = page("File:Old.jpg", "pd", "Public domain", null, "Artist");
        ((ObjectNode) pd.path("imageinfo").get(0).path("extmetadata"))
                .putObject("Copyrighted").put("value", "False");
        when(commons.getImageInfo(anyList())).thenReturn(imageInfo(cc0));
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull())).thenReturn(categoryInfo(List.of("File:Old.jpg"), pd));

        var result = service.preview("Q243");

        // 'Public domain' 표시만 있고 근거 템플릿이 없으면 저장하지 않는다.
        assertThat(result.photos()).extracting("licenseType").containsExactly("CC0", "UNKNOWN");
        assertThat(result.photos()).extracting("reuseStatus").containsExactly("ELIGIBLE", "LICENSE_EVIDENCE_MISSING");
        assertThat(result.photos().get(1).reviewReason()).contains("구체적인 퍼블릭 도메인 근거");
        assertThat(result.photos().get(1).savable()).isFalse();
        assertThat(result.photos().get(0).attributionRequired()).isFalse();
    }

    @Test
    void realCommonsLicenseShapesAreJudgedByTypeVersionUrlAndTemplates() throws Exception {
        stubPhotoEntity("British Museum.jpg", "Eiffel Tower");
        // British Museum from NE 2 (cropped).JPG: 실제 LicenseUrl 은 http:// 이고 끝 슬래시가 있다.
        ObjectNode museum = page("File:British Museum.jpg", "cc-by-sa-3.0", "CC BY-SA 3.0",
                "http://creativecommons.org/licenses/by-sa/3.0/", "Ham");
        // Tour Eiffel Wikimedia Commons.jpg: extmetadata 는 pd 지만 사진 자체는 Licensed-PD 의 CC BY-SA 3.0.
        ObjectNode eiffel = page("File:Eiffel.jpg", "pd", "Public domain", null, "Benh LIEU SONG");
        templates(eiffel, "Licensed-PD", "PD-old-70", "Cc-by-sa-3.0");
        ObjectNode conflict = page("File:Conflict.jpg", "cc-by-sa-4.0", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by/4.0/", "Artist");
        ObjectNode colosseo = page("File:Colosseo.jpg", "cc-by-sa-4.0", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0", "Artist");
        ((ObjectNode) colosseo.path("imageinfo").get(0).path("extmetadata")).putObject("Restrictions")
                .put("value", "ita-mibac");
        ObjectNode anonymous = page("File:Anonymous.jpg", "cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0/", "Unknown author");
        when(commons.getImageInfo(anyList())).thenReturn(imageInfo(museum));
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull())).thenReturn(categoryInfo(List.of(
                "File:Eiffel.jpg", "File:Conflict.jpg", "File:Colosseo.jpg", "File:Anonymous.jpg"),
                eiffel, conflict, colosseo, anonymous));

        var photos = service.preview("Q243").photos();

        assertThat(photos).extracting("reuseStatus").containsExactly(
                "ELIGIBLE", "ELIGIBLE", "LICENSE_EVIDENCE_MISSING", "RESTRICTED", "LICENSE_EVIDENCE_MISSING");
        assertThat(photos.get(0).licenseName()).isEqualTo("CC BY-SA 3.0");
        assertThat(photos.get(0).licenseUrl()).isEqualTo("https://creativecommons.org/licenses/by-sa/3.0/");
        assertThat(photos.get(0).licenseEvidence()).contains("LicenseUrl=http://creativecommons.org/licenses/by-sa/3.0/");
        assertThat(photos.get(1).licenseType()).isEqualTo("CC_BY_SA");
        assertThat(photos.get(1).licenseName()).isEqualTo("CC BY-SA 3.0");
        assertThat(photos.get(1).author()).isEqualTo("Benh LIEU SONG");
        assertThat(photos.get(1).licenseEvidence()).contains("Licensed-PD", "Template:Cc-by-sa-3.0");
        assertThat(photos.get(2).reviewReason()).contains("라이선스 URL");
        assertThat(photos.get(3).reviewReason()).contains("ita-mibac");
        assertThat(photos.get(4).reviewReason()).contains("저작자 정보가 없어");
        assertThat(photos).extracting("savable").containsExactly(true, true, false, false, false);
    }

    @Test
    void repeatedPreviewForTheSameQidIsServedFromTheShortCache() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        ObjectNode tour = page("File:Tour Eiffel.jpg", "cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0", "Artist");
        when(commons.getImageInfo(anyList())).thenReturn(imageInfo(tour));
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull())).thenReturn(categoryInfo(List.of(), tour));

        var first = service.preview("Q243");
        var second = service.preview("Q243");

        assertThat(second).isSameAs(first);
        verify(wikidata, times(1)).getCommonsSitelinkEntity("Q243");
        verify(commons, times(1)).getImageInfo(anyList());
        verify(commons, times(1)).getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull());
        assertThatThrownBy(() -> service.preview("q243|Q1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alreadyCachedAutofillEntityIsReusedInsteadOfFetchingPhotoClaims() throws Exception {
        ObjectNode entity = mapper.createObjectNode();
        entity.putObject("claims").setAll((ObjectNode) claim("P18", "Tour Eiffel.jpg").path("claims"));
        when(wikidata.getAutofillEntities(List.of("Q243"))).thenReturn(Map.of("Q243", entity));
        cache.entity("Q243");
        when(commons.getImageInfo(anyList())).thenReturn(imageInfo(page("File:Tour Eiffel.jpg", "cc-by-4.0",
                "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0", "Artist")));

        assertThat(service.preview("Q243").photos()).hasSize(1);
        verify(wikidata, never()).getCommonsSitelinkEntity(anyString());
        verify(wikidata, never()).getClaims(anyString(), anyString());
    }

    @Test
    void representativeMetadataAndCategoryQueryRunAtTheSameTime() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        CountDownLatch bothStarted = new CountDownLatch(2);
        ObjectNode tour = page("File:Tour Eiffel.jpg", "cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0", "Artist");
        when(commons.getImageInfo(anyList())).thenAnswer(invocation -> {
            bothStarted.countDown();
            assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
            return imageInfo(tour);
        });
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull())).thenAnswer(invocation -> {
            bothStarted.countDown();
            assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
            return categoryInfo(List.of());
        });

        assertThat(service.preview("Q243").status()).isEqualTo("AVAILABLE");
    }

    @Test
    void nonImageFilesAreSkippedAndShowMoreContinuesFromTheReturnedCursor() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        ObjectNode tour = page("File:Tour Eiffel.jpg", "cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0", "Artist");
        ObjectNode audio = mime(page("File:Audio.ogg", "cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0", "Artist"), "application/ogg");
        ObjectNode pdf = mime(page("File:Guide.pdf", "cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0", "Artist"), "application/pdf");
        when(commons.getImageInfo(anyList())).thenReturn(imageInfo(tour));
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), isNull())).thenReturn(continued(
                categoryInfo(List.of("File:Audio.ogg", "File:Guide.pdf", "File:Tour_Eiffel.jpg", "File:A.jpg"),
                        audio, pdf, tour, photo("File:A.jpg")), "file|aa|1"));
        // 이미지가 모자라면 같은 요청 안에서 다음 묶음을 이어 받는다(최대 4번).
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), eq("file|aa|1"))).thenReturn(continued(
                categoryInfo(List.of("File:B.png"), mime(photo("File:B.png"), "image/png")), "file|bb|2"));
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), eq("file|bb|2"))).thenReturn(continued(
                categoryInfo(List.of("File:C.jpg"), photo("File:C.jpg")), "file|cc|3"));
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), eq("file|cc|3"))).thenReturn(continued(
                categoryInfo(List.of("File:E.jpg"), photo("File:E.jpg")), "file|dd|4"));

        var first = service.preview("Q243");

        assertThat(first.photos()).extracting("fileName")
                .containsExactly("Tour Eiffel.jpg", "A.jpg", "B.png", "C.jpg", "E.jpg");
        assertThat(first.nextCursor()).isEqualTo("7:file|dd|4");
        assertThat(first.selectionLimit()).isEqualTo(CommonsPhotoPreviewService.MAX_PHOTOS);
        verify(commons, times(4)).getCategoryImageInfo(anyString(), anyInt(), any());

        // 이어 보기 요청이 실패하면 같은 위치를 돌려줘 다시 시도할 수 있고, 실패 응답은 캐시하지 않는다.
        when(commons.getCategoryImageInfo(eq("Eiffel Tower"), anyInt(), eq("file|dd|4")))
                .thenThrow(new CommonsApiException("Commons 응답 시간이 초과되었습니다. 다시 시도해 주세요."))
                .thenReturn(categoryInfo(List.of("File:Tour Eiffel.jpg", "File:D.jpg"), tour, photo("File:D.jpg")));
        var failed = service.preview("Q243", first.nextCursor());
        assertThat(failed.status()).isEqualTo("PARTIAL");
        assertThat(failed.nextCursor()).isEqualTo("7:file|dd|4");

        var more = service.preview("Q243", failed.nextCursor());
        // P18은 카테고리에 다시 나와도 이어진 묶음에 또 넣지 않는다.
        assertThat(more.photos()).extracting("fileName").containsExactly("D.jpg");
        assertThat(more.nextCursor()).isNull();
        verify(commons, times(1)).getImageInfo(anyList());
        assertThatThrownBy(() -> service.preview("Q243", "600:file|x|1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview("Q243", "6:file|x&a=1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveTimeCandidatesAreFetchedFreshWithoutThePreviewCache() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        when(commons.listCategoryFiles("Eiffel Tower")).thenReturn(List.of("File:Other view.jpg"));

        service.candidates("Q243");
        var candidates = service.candidates("Q243");

        assertThat(candidates.files()).containsKeys("Tour Eiffel.jpg", "Other view.jpg");
        verify(wikidata, times(2)).getCommonsSitelinkEntity("Q243");
        verify(commons, times(2)).listCategoryFiles("Eiffel Tower");
        verify(commons, never()).getCategoryImageInfo(anyString(), anyInt(), any());
    }

    /** 사진 후보에는 P18·P373 claims와 commonswiki 연결만 받는다. */
    private void stubPhotoEntity(String p18, String category) {
        when(wikidata.getCommonsSitelinkEntity("Q243")).thenReturn(mapper.createObjectNode().put("id", "Q243"));
        when(wikidata.getClaims("Q243", "P18")).thenReturn(claim("P18", p18));
        when(wikidata.getClaims("Q243", "P373")).thenReturn(claim("P373", category));
    }

    private JsonNode claim(String property, String value) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode claims = root.putObject("claims");
        if (value != null) {
            claims.putArray(property).addObject().putObject("mainsnak").putObject("datavalue").put("value", value);
        }
        return root;
    }

    private JsonNode imageInfo(ObjectNode... pages) {
        ObjectNode root = mapper.createObjectNode();
        var array = root.putObject("query").putArray("pages");
        for (ObjectNode page : pages) array.add(page);
        return root;
    }

    /** 카테고리 목록(순서)과 파일 정보를 한 응답에 담은 합친 조회 결과. */
    private JsonNode categoryInfo(List<String> members, ObjectNode... pages) {
        ObjectNode root = (ObjectNode) imageInfo(pages);
        var list = ((ObjectNode) root.path("query")).putArray("categorymembers");
        for (String title : members) list.addObject().put("ns", 6).put("title", title);
        return root;
    }

    /** 다음 묶음 위치(continue.cmcontinue)를 붙인다. */
    private JsonNode continued(JsonNode response, String token) {
        ((ObjectNode) response).putObject("continue").put("cmcontinue", token).put("gcmcontinue", token);
        return response;
    }

    private ObjectNode photo(String title) {
        return page(title, "cc-by-4.0", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0", "Artist");
    }

    private ObjectNode mime(ObjectNode page, String mime) {
        ((ObjectNode) page.path("imageinfo").get(0)).put("mime", mime);
        return page;
    }

    /** Commons API prop=templates 응답처럼 파일 페이지에 쓰인 템플릿을 붙인다. */
    private void templates(ObjectNode page, String... names) {
        var array = page.putArray("templates");
        for (String name : names) array.addObject().put("ns", 10).put("title", "Template:" + name);
    }

    private ObjectNode page(String title, String license, String licenseShort, String licenseUrl, String artist) {
        ObjectNode page = mapper.createObjectNode();
        page.put("title", title);
        ObjectNode info = page.putArray("imageinfo").addObject();
        info.put("mime", "image/jpeg").put("width", 3000).put("height", 2000)
                .put("thumburl", "https://thumb.wikimedia.org/wikipedia/commons/thumb/test.jpg")
                .put("url", "https://upload.wikimedia.org/wikipedia/commons/test.jpg")
                .put("descriptionurl", "https://commons.wikimedia.org/wiki/" + title.replace(' ', '_'));
        ObjectNode metadata = info.putObject("extmetadata");
        metadata.putObject("Artist").put("value", artist);
        metadata.putObject("License").put("value", license);
        metadata.putObject("LicenseShortName").put("value", licenseShort);
        if (licenseUrl != null) metadata.putObject("LicenseUrl").put("value", licenseUrl);
        metadata.putObject("AttributionRequired").put("value", "true");
        metadata.putObject("Restrictions").put("value", "");
        return page;
    }
}
