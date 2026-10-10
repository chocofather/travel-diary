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

    /**
     * QID가 없는 여행지의 수동 검색: 상업적 이용이 가능한 라이선스(PD·CC0·CC BY·CC BY-SA)의
     * JPEG·PNG 사진만, 검색 순위(index) 순서대로 남긴다. NC·미국 한정 PD·SVG·작은 사진·중복 원본은 뺀다.
     */
    @Test
    void searchKeepsOnlyCommerciallyReusablePhotosInSearchOrder() {
        ObjectNode ccBy = searchPhoto("File:By.jpg", 3);
        ObjectNode ccBySa = searchPage("File:BySa.jpg", 1, "cc-by-sa-4.0", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0");
        ObjectNode cc0 = searchPage("File:Zero.png", 2, "cc0", "CC0",
                "https://creativecommons.org/publicdomain/zero/1.0/");
        mime(cc0, "image/png");
        ObjectNode publicDomain = searchPage("File:Old.jpg", 4, "pd", "Public domain", null);
        ((ObjectNode) publicDomain.path("imageinfo").get(0).path("extmetadata")).putObject("Copyrighted").put("value", "False");
        templates(publicDomain, "PD-old-70");
        ObjectNode nonCommercial = searchPage("File:Nc.jpg", 5, "cc-by-nc-4.0", "CC BY-NC 4.0",
                "https://creativecommons.org/licenses/by-nc/4.0");
        ObjectNode usOnly = searchPage("File:Us.jpg", 6, "pd", "Public domain", null);
        ((ObjectNode) usOnly.path("imageinfo").get(0).path("extmetadata")).putObject("Copyrighted").put("value", "False");
        templates(usOnly, "PD-US");
        ObjectNode unknown = searchPage("File:Unknown.jpg", 7, "", "", null);
        ObjectNode svg = mime(searchPhoto("File:Map.svg", 8), "image/svg+xml");
        when(commons.searchImageInfo("Petronas Twin Towers", 8, 0)).thenReturn(imageInfo(
                ccBy, ccBySa, cc0, publicDomain, nonCommercial, usOnly, unknown, svg));

        var result = service.search("  Petronas   Twin Towers ", null);

        assertThat(result.qid()).isNull();
        assertThat(result.status()).isEqualTo("AVAILABLE");
        assertThat(result.photos()).extracting("fileName").containsExactly("BySa.jpg", "Zero.png", "By.jpg", "Old.jpg");
        assertThat(result.photos()).extracting("licenseType").containsExactly("CC_BY_SA", "CC0", "CC_BY", "PUBLIC_DOMAIN");
        assertThat(result.photos()).allMatch(photo -> photo.savable() && "SEARCH".equals(photo.source()));
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void searchDropsSmallImagesDuplicateOriginalsAndBrokenItemsWithoutFailingTheRest() {
        ObjectNode small = searchPhoto("File:Icon.jpg", 1);
        ((ObjectNode) small.path("imageinfo").get(0)).put("width", 300).put("height", 200);
        ObjectNode first = searchPhoto("File:Tower.jpg", 2);
        ObjectNode sameOriginal = searchPhoto("File:Tower copy.jpg", 3);
        ((ObjectNode) sameOriginal.path("imageinfo").get(0)).put("url",
                first.path("imageinfo").get(0).path("url").asText());
        ObjectNode missing = mapper.createObjectNode().put("title", "File:Gone.jpg").put("index", 4).put("missing", true);
        ObjectNode noInfo = mapper.createObjectNode().put("title", "File:NoInfo.jpg").put("index", 5);
        ObjectNode last = searchPhoto("File:Night.jpg", 6);
        when(commons.searchImageInfo("Tower", 8, 0)).thenReturn(imageInfo(small, first, sameOriginal, missing, noInfo, last));

        var result = service.search("Tower", null);

        assertThat(result.photos()).extracting("fileName").containsExactly("Tower.jpg", "Night.jpg");
        assertThat(result.status()).isEqualTo("AVAILABLE");
    }

    /** '사진 더 보기'는 Commons 공식 이어받기 값(gsroffset)으로 다음 위치부터 받는다. 앞 결과는 다시 받지 않는다. */
    @Test
    void searchContinuesFromTheOfficialOffsetAndValidatesTheCursor() {
        JsonNode firstBatch = withOffset(imageInfo(searchPhoto("File:A.jpg", 1)), 8);
        JsonNode secondBatch = withOffset(imageInfo(searchPhoto("File:B.jpg", 9)), 16);
        when(commons.searchImageInfo("Tower", 8, 0)).thenReturn(firstBatch);
        when(commons.searchImageInfo("Tower", 8, 8)).thenReturn(secondBatch);
        when(commons.searchImageInfo("Tower", 8, 16)).thenReturn(imageInfo(searchPhoto("File:C.jpg", 17)));

        var first = service.search("Tower", null);
        var more = service.search("Tower", "8");

        // 한 묶음은 PAGE_SIZE 를 채우거나 이어받을 위치가 없을 때까지 이어 받는다.
        assertThat(first.photos()).extracting("fileName").containsExactly("A.jpg", "B.jpg", "C.jpg");
        assertThat(first.nextCursor()).isNull();
        assertThat(more.photos()).extracting("fileName").containsExactly("B.jpg", "C.jpg");
        assertThatThrownBy(() -> service.search("Tower", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("Tower", "10000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("Tower", "8&x=1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("  ", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("검색어");
    }

    @Test
    void searchExplainsNoResultsNoReusablePhotosAndApiFailures() {
        when(commons.searchImageInfo("Nothing", 8, 0)).thenReturn(mapper.createObjectNode().put("batchcomplete", true));
        when(commons.searchImageInfo("Restricted", 8, 0)).thenReturn(imageInfo(searchPage("File:Nc.jpg", 1,
                "cc-by-nc-4.0", "CC BY-NC 4.0", "https://creativecommons.org/licenses/by-nc/4.0")));
        when(commons.searchImageInfo("Down", 8, 0)).thenThrow(new CommonsApiException("Commons에 연결하지 못했습니다."));
        when(commons.searchImageInfo("Later", 8, 0)).thenReturn(withOffset(imageInfo(searchPhoto("File:A.jpg", 1)), 8));
        when(commons.searchImageInfo("Later", 8, 8)).thenThrow(new CommonsApiException("Commons 응답 시간이 초과되었습니다."));

        var none = service.search("Nothing", null);
        var restricted = service.search("Restricted", null);
        var down = service.search("Down", null);
        var partial = service.search("Later", null);

        assertThat(none.status()).isEqualTo("NO_PHOTOS");
        assertThat(none.message()).contains("검색 결과가 없습니다");
        assertThat(restricted.status()).isEqualTo("NO_PHOTOS");
        assertThat(restricted.message()).contains("사용할 수 있는 사진이 없습니다");
        assertThat(down.status()).isEqualTo("ERROR");
        assertThat(down.message()).contains("연결하지 못했습니다");
        assertThat(down.nextCursor()).isNull();
        // 받은 사진은 보여주고, 실패한 위치에서 다시 '사진 더 보기'를 할 수 있다.
        assertThat(partial.status()).isEqualTo("PARTIAL");
        assertThat(partial.photos()).extracting("fileName").containsExactly("A.jpg");
        assertThat(partial.nextCursor()).isEqualTo("8");
    }

    /** 검색 결과 한 건. 실제 Commons 처럼 파일마다 원본 URL이 다르다. */
    private ObjectNode searchPhoto(String title, int index) {
        return searchPage(title, index, "cc-by-4.0", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0");
    }

    private ObjectNode searchPage(String title, int index, String license, String licenseShort, String licenseUrl) {
        ObjectNode page = page(title, license, licenseShort, licenseUrl, "Artist");
        page.put("ns", 6).put("index", index).put("pageid", 1000 + index);
        String file = title.substring(5).replace(' ', '_');
        ((ObjectNode) page.path("imageinfo").get(0))
                .put("url", "https://upload.wikimedia.org/wikipedia/commons/a/ab/" + file)
                .put("thumburl", "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/" + file + "/240px-" + file);
        return page;
    }

    private JsonNode withOffset(JsonNode response, int offset) {
        ((ObjectNode) response).putObject("continue").put("gsroffset", offset).put("continue", "gsroffset||");
        return response;
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
