package com.tripbora.service.wikidata;

import com.tripbora.service.kto.KtoDownloadedPhoto;
import com.tripbora.service.kto.KtoPhotoDownloadException;
import com.tripbora.service.kto.KtoPhotoDownloadService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommonsPhotoImportServiceTest {
    /** 실제 Commons API는 thumb 호스트의 렌디션 URL에 utm 추적 쿼리를 붙여 준다. */
    private static final String RENDITION =
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a8/Tour.jpg/1920px-Tour.jpg";
    private static final String TRACKING = "?utm_source=commons.wikimedia.org&utm_campaign=imageinfo";
    private final ObjectMapper mapper = new ObjectMapper();
    private final WikidataApiClient wikidata = mock(WikidataApiClient.class);
    private final CommonsApiClient commons = mock(CommonsApiClient.class);
    private final KtoPhotoDownloadService downloads = mock(KtoPhotoDownloadService.class);
    private final CommonsPhotoImportService service = new CommonsPhotoImportService(
            new CommonsPhotoPreviewService(wikidata, commons, new WikidataAutofillCache(wikidata)),
            commons, downloads, mapper,
            Clock.fixed(Instant.parse("2026-09-25T01:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void selectionCarriesOnlyFileIdentifiersAndRejectsMixedQidDuplicatesAndTwoMains() {
        var selections = service.parseSelections("""
                {"qid":"Q243","photos":[{"fileName":"tour_Eiffel.jpg","main":true,
                  "url":"https://evil.example/x.jpg","author":"forged","license":"cc0"},
                  {"fileName":"File:Other view.jpg"}]}""", "q243");

        assertThat(selections).containsExactly(
                new CommonsPhotoImportService.Selection("Tour Eiffel.jpg", true),
                new CommonsPhotoImportService.Selection("Other view.jpg", false));
        assertThat(service.parseSelections("", "Q243")).isEmpty();
        assertThat(service.parseSelections("{\"qid\":\"Q1\",\"photos\":[]}", "Q243")).isEmpty();
        assertThatThrownBy(() -> service.parseSelections(
                "{\"qid\":\"Q90\",\"photos\":[{\"fileName\":\"A.jpg\"}]}", "Q243"))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("다시 선택");
        assertThatThrownBy(() -> service.parseSelections(
                "{\"qid\":\"Q243\",\"photos\":[{\"fileName\":\"A.jpg\"},{\"fileName\":\"File:a.jpg\"}]}", "Q243"))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("두 번");
        assertThatThrownBy(() -> service.parseSelections(
                "{\"qid\":\"Q243\",\"photos\":[{\"fileName\":\"A.jpg\",\"main\":true},{\"fileName\":\"B.jpg\",\"main\":true}]}",
                "Q243"))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("1장");
        assertThatThrownBy(() -> service.parseSelections(
                "{\"qid\":\"Q243\",\"photos\":[{\"fileName\":\"A.jpg\"}]}", ""))
                .isInstanceOf(CommonsPhotoSelectionException.class);
        assertThatThrownBy(() -> service.parseSelections("not json", "Q243"))
                .isInstanceOf(CommonsPhotoSelectionException.class);
        // 선택 한도 경계: 정확히 5장(대표 1장)은 받고, 6장은 이유와 함께 거부한다.
        assertThat(service.parseSelections(photosJson(5), "Q243")).hasSize(5)
                .filteredOn(CommonsPhotoImportService.Selection::main).hasSize(1);
        assertThatThrownBy(() -> service.parseSelections(photosJson(6), "Q243"))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("최대 5장");
    }

    private String photosJson(int count) {
        StringBuilder json = new StringBuilder("{\"qid\":\"Q243\",\"photos\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) json.append(',');
            json.append("{\"fileName\":\"P").append(index).append(".jpg\",\"main\":").append(index == 0).append('}');
        }
        return json.append("]}").toString();
    }

    @Test
    void savesServerRefetchedFullMetadataNotPreviewValues() throws Exception {
        String longAuthor = "Photographer " + "x".repeat(900);
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        when(commons.listCategoryFiles("Eiffel Tower")).thenReturn(List.of("File:Other view.jpg"));
        ObjectNode tour = page("File:Tour Eiffel.jpg", "cc-by-sa-4.0", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/", "<a href=\"//commons.wikimedia.org/wiki/User:A\">"
                        + longAuthor + "</a>", "image/jpeg");
        ((ObjectNode) tour.path("imageinfo").get(0).path("extmetadata")).putObject("Attribution")
                .put("value", "Photo: Studio Paris");
        ObjectNode other = page("File:Other view.jpg", "cc-zero", "CC0 1.0",
                "https://creativecommons.org/publicdomain/zero/1.0", "Other Artist", "image/png");
        when(commons.getImageInfoForSave(anyList())).thenReturn(imageInfo(tour, other));
        when(downloads.downloadCommonsImage(RENDITION)).thenReturn(
                new KtoDownloadedPhoto("/uploads/destinations/a.jpg", RENDITION, "image/jpeg", 10));
        when(downloads.downloadCommonsImage(rendition("File:Other view.jpg"))).thenReturn(
                new KtoDownloadedPhoto("/uploads/destinations/b.png", RENDITION, "image/png", 10));

        List<PreparedCommonsPhoto> prepared = service.prepare("Q243", List.of(
                new CommonsPhotoImportService.Selection("Tour Eiffel.jpg", false),
                new CommonsPhotoImportService.Selection("Other view.jpg", false)), false);

        verify(commons, never()).getImageInfo(anyList());
        assertThat(prepared).extracting(PreparedCommonsPhoto::localImageUrl)
                .containsExactly("/uploads/destinations/a.jpg", "/uploads/destinations/b.png");
        assertThat(prepared).extracting(PreparedCommonsPhoto::main).containsExactly(true, false);
        var source = prepared.get(0).source();
        assertThat(source.getAuthorText()).isEqualTo(longAuthor).hasSizeGreaterThan(500);
        assertThat(source.getWikidataQid()).isEqualTo("Q243");
        assertThat(source.getCommonsFileTitle()).isEqualTo("File:Tour Eiffel.jpg");
        assertThat(source.getSourceTitle()).isEqualTo("Tour Eiffel.jpg");
        assertThat(source.getExternalContentId()).isEqualTo("M101");
        assertThat(source.getWorkPageUrl()).isEqualTo("https://commons.wikimedia.org/wiki/File:Tour_Eiffel.jpg");
        assertThat(source.getOriginalImageUrl()).isEqualTo("https://upload.wikimedia.org/wikipedia/commons/a/a8/Tour.jpg");
        assertThat(source.getLicenseType()).isEqualTo("CREATIVE_COMMONS");
        assertThat(source.getLicenseName()).isEqualTo("CC BY-SA 4.0");
        assertThat(source.getLicenseVersion()).isEqualTo("4.0");
        assertThat(source.getLicenseUrl()).isEqualTo("https://creativecommons.org/licenses/by-sa/4.0/");
        assertThat(source.getCustomAttribution()).isEqualTo("Photo: Studio Paris");
        assertThat(source.getAttributionText()).isEqualTo("Photo: Studio Paris, CC BY-SA 4.0, via Wikimedia Commons");
        assertThat(source.getCreditLinksJson()).contains("https://commons.wikimedia.org/wiki/User:A");
        assertThat(source.getLicenseEvidenceUrl()).isEqualTo("https://commons.wikimedia.org/w/index.php?oldid=9001");
        assertThat(source.getLicenseEvidenceRevisionId()).isEqualTo(9001L);
        assertThat(source.getLicenseEvidenceDetail()).contains("License=cc-by-sa-4.0",
                "LicenseUrl=https://creativecommons.org/licenses/by-sa/4.0/", "판본 9001", "1920px");
        assertThat(source.getLicenseConditions()).contains("동일조건변경허락");
        assertThat(source.getAttributionRequired()).isTrue();
        assertThat(source.getShareAlikeRequired()).isTrue();
        assertThat(source.getContentModified()).isFalse();
        assertThat(source.getLicenseCheckedAt()).isEqualTo(LocalDateTime.of(2026, 9, 25, 10, 0));
        assertThat(prepared.get(1).source().getAttributionRequired()).isFalse();
    }

    @Test
    void unclearLicenseUnsupportedFormatAndForeignFileBlockAutoSaveBeforeDownload() throws Exception {
        stubPhotoEntity("Old.jpg", "Eiffel Tower");
        when(commons.listCategoryFiles("Eiffel Tower")).thenReturn(List.of("File:Anim.gif"));
        ObjectNode pd = page("File:Old.jpg", "pd", "Public domain", null, "Artist", "image/jpeg");
        ObjectNode gif = page("File:Anim.gif", "cc-by-4.0", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0", "Artist", "image/gif");
        when(commons.getImageInfoForSave(anyList())).thenReturn(imageInfo(pd, gif));

        assertThatThrownBy(() -> service.prepare("Q243", List.of(
                new CommonsPhotoImportService.Selection("Old.jpg", true),
                new CommonsPhotoImportService.Selection("Anim.gif", false)), false))
                .isInstanceOf(CommonsPhotoSelectionException.class)
                .hasMessageContaining("선택을 해제")
                .hasMessageContaining("Old.jpg: 저작권 상태(Copyrighted) 표시가 퍼블릭 도메인과 맞지 않습니다")
                .hasMessageContaining("Anim.gif: 자동 저장은 JPEG·PNG");
        assertThatThrownBy(() -> service.prepare("Q243", List.of(
                new CommonsPhotoImportService.Selection("Unrelated.jpg", true)), false))
                .isInstanceOf(CommonsPhotoSelectionException.class)
                .hasMessageContaining("현재 Wikidata 후보에 없는 사진");
        verifyNoInteractions(downloads);
    }

    @Test
    void publicDomainWithConcreteTemplateIsSavedWithItsEvidence() throws Exception {
        stubPhotoEntity("Mona.jpg", "Eiffel Tower");
        when(commons.listCategoryFiles("Eiffel Tower")).thenReturn(List.of("File:Gov.jpg"));
        ObjectNode mona = page("File:Mona.jpg", "pd", "Public domain", null, "Leonardo da Vinci", "image/jpeg");
        ((ObjectNode) mona.path("imageinfo").get(0).path("extmetadata")).putObject("Copyrighted").put("value", "False");
        templates(mona, "PD-old-100-expired", "PD-Art");
        ObjectNode gov = page("File:Gov.jpg", "pd", "Public domain", null, "NASA", "image/jpeg");
        ((ObjectNode) gov.path("imageinfo").get(0).path("extmetadata")).putObject("Copyrighted").put("value", "False");
        templates(gov, "PD-USGov-NASA");
        when(commons.getImageInfoForSave(anyList())).thenReturn(imageInfo(mona, gov));
        when(downloads.downloadCommonsImage(anyString())).thenAnswer(invocation -> new KtoDownloadedPhoto(
                "/uploads/destinations/m.jpg", invocation.getArgument(0), "image/jpeg", 10));

        var source = service.prepare("Q243", List.of(new CommonsPhotoImportService.Selection("Mona.jpg", true)), false)
                .get(0).source();

        assertThat(source.getLicenseType()).isEqualTo("PUBLIC_DOMAIN");
        assertThat(source.getLicenseName()).isEqualTo("Public domain");
        assertThat(source.getLicenseUrl()).isNull();
        assertThat(source.getAttributionText()).isEqualTo("Leonardo da Vinci, Public domain, via Wikimedia Commons");
        assertThat(source.getLicenseEvidenceDetail()).contains("Copyrighted=False", "PD-old-100-expired", "PD-Art");
        assertThat(source.getLicenseConditions()).contains("PD-old-100-expired");
        assertThat(source.getAttributionRequired()).isFalse();
        // 미국 한정 퍼블릭 도메인은 저장 단계에서도 다시 막는다.
        assertThatThrownBy(() -> service.prepare("Q243", List.of(
                new CommonsPhotoImportService.Selection("Gov.jpg", true)), false))
                .isInstanceOf(CommonsPhotoSelectionException.class)
                .hasMessageContaining("Gov.jpg: 미국 기준 퍼블릭 도메인");
    }

    @Test
    void downloadFailureCleansFilesAlreadyDownloadedInThisRegistration() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        when(commons.listCategoryFiles("Eiffel Tower")).thenReturn(List.of("File:Other view.jpg"));
        when(commons.getImageInfoForSave(anyList())).thenReturn(imageInfo(
                page("File:Tour Eiffel.jpg", "cc-by-4.0", "CC BY 4.0",
                        "https://creativecommons.org/licenses/by/4.0", "Artist", "image/jpeg"),
                page("File:Other view.jpg", "cc-by-4.0", "CC BY 4.0",
                        "https://creativecommons.org/licenses/by/4.0", "Artist", "image/jpeg")));
        // 실패한 다운로드보다 늦게 끝나는 다운로드도 기다렸다가 지운다.
        when(downloads.downloadCommonsImage(RENDITION)).thenAnswer(invocation -> {
            Thread.sleep(150);
            return new KtoDownloadedPhoto("/uploads/destinations/a.jpg", RENDITION, "image/jpeg", 10);
        });
        when(downloads.downloadCommonsImage(rendition("File:Other view.jpg")))
                .thenThrow(new KtoPhotoDownloadException());

        assertThatThrownBy(() -> service.prepare("Q243", List.of(
                new CommonsPhotoImportService.Selection("Tour Eiffel.jpg", true),
                new CommonsPhotoImportService.Selection("Other view.jpg", false)), false))
                .isInstanceOf(CommonsPhotoDownloadException.class)
                .hasMessageContaining("Other view.jpg");
        verify(downloads).deleteDownloadedPhoto("/uploads/destinations/a.jpg");
    }

    @Test
    void rateLimitedDownloadCleansTheOtherFileAndIsReportedAsATemporaryLimit() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        when(commons.listCategoryFiles("Eiffel Tower")).thenReturn(List.of("File:Other view.jpg"));
        when(commons.getImageInfoForSave(anyList())).thenReturn(imageInfo(
                page("File:Tour Eiffel.jpg", "cc-by-4.0", "CC BY 4.0",
                        "https://creativecommons.org/licenses/by/4.0", "Artist", "image/jpeg"),
                page("File:Other view.jpg", "cc-by-4.0", "CC BY 4.0",
                        "https://creativecommons.org/licenses/by/4.0", "Artist", "image/jpeg")));
        when(downloads.downloadCommonsImage(RENDITION))
                .thenReturn(new KtoDownloadedPhoto("/uploads/destinations/a.jpg", RENDITION, "image/jpeg", 10));
        when(downloads.downloadCommonsImage(rendition("File:Other view.jpg")))
                .thenThrow(new com.tripbora.service.kto.PhotoDownloadRateLimitedException("20"));

        assertThatThrownBy(() -> service.prepare("Q243", List.of(
                new CommonsPhotoImportService.Selection("Tour Eiffel.jpg", true),
                new CommonsPhotoImportService.Selection("Other view.jpg", false)), false))
                .isInstanceOfSatisfying(CommonsRateLimitException.class,
                        limited -> assertThat(limited.retryAfter()).isEqualTo(java.time.Duration.ofSeconds(20)));
        verify(downloads).deleteDownloadedPhoto("/uploads/destinations/a.jpg");
    }

    @Test
    void selectedPhotosDownloadInParallelButAtMostThreeAtOnce() throws Exception {
        List<String> category = List.of("File:B.jpg", "File:C.jpg", "File:D.jpg", "File:E.jpg");
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        when(commons.listCategoryFiles("Eiffel Tower")).thenReturn(category);
        List<ObjectNode> pages = new java.util.ArrayList<>();
        for (String title : List.of("File:Tour Eiffel.jpg", "File:B.jpg", "File:C.jpg", "File:D.jpg", "File:E.jpg")) {
            pages.add(page(title, "cc-by-4.0", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0", "Artist", "image/jpeg"));
        }
        when(commons.getImageInfoForSave(anyList())).thenReturn(imageInfo(pages.toArray(ObjectNode[]::new)));
        java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger peak = new java.util.concurrent.atomic.AtomicInteger();
        when(downloads.downloadCommonsImage(anyString())).thenAnswer(invocation -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(120);
                String url = invocation.getArgument(0);
                return new KtoDownloadedPhoto("/uploads/destinations/" + url.hashCode() + ".jpg", url, "image/jpeg", 10);
            } finally {
                running.decrementAndGet();
            }
        });

        var prepared = service.prepare("Q243", List.of(
                new CommonsPhotoImportService.Selection("Tour Eiffel.jpg", true),
                new CommonsPhotoImportService.Selection("B.jpg", false),
                new CommonsPhotoImportService.Selection("C.jpg", false),
                new CommonsPhotoImportService.Selection("D.jpg", false),
                new CommonsPhotoImportService.Selection("E.jpg", false)), false);

        assertThat(prepared).hasSize(5);
        assertThat(prepared.get(0).main()).isTrue();
        assertThat(prepared).extracting(photo -> photo.source().getSourceTitle())
                .containsExactly("Tour Eiffel.jpg", "B.jpg", "C.jpg", "D.jpg", "E.jpg");
        assertThat(peak.get()).isBetween(2, 3);
    }

    @Test
    void existingDestinationKeepsItsMainPhotoUnlessTheAdminChoosesANewOne() throws Exception {
        stubPhotoEntity("Tour Eiffel.jpg", "Eiffel Tower");
        when(commons.listCategoryFiles("Eiffel Tower")).thenReturn(List.of("File:Other view.jpg"));
        when(commons.getImageInfoForSave(anyList())).thenReturn(imageInfo(
                page("File:Tour Eiffel.jpg", "cc-by-4.0", "CC BY 4.0",
                        "https://creativecommons.org/licenses/by/4.0", "Artist", "image/jpeg"),
                page("File:Other view.jpg", "cc-by-4.0", "CC BY 4.0",
                        "https://creativecommons.org/licenses/by/4.0", "Artist", "image/jpeg")));
        when(downloads.downloadCommonsImage(anyString())).thenAnswer(invocation -> new KtoDownloadedPhoto(
                "/uploads/destinations/" + Math.abs(invocation.getArgument(0).hashCode()) + ".jpg",
                invocation.getArgument(0), "image/jpeg", 10));
        var both = List.of(new CommonsPhotoImportService.Selection("Tour Eiffel.jpg", false),
                new CommonsPhotoImportService.Selection("Other view.jpg", false));

        assertThat(service.prepareForExistingDestination("Q243", both, true))
                .extracting(PreparedCommonsPhoto::main).containsExactly(false, false);
        assertThat(service.prepareForExistingDestination("Q243", List.of(
                new CommonsPhotoImportService.Selection("Tour Eiffel.jpg", false),
                new CommonsPhotoImportService.Selection("Other view.jpg", true)), true))
                .extracting(PreparedCommonsPhoto::main).containsExactly(false, true);
        // 대표 사진이 없던 여행지는 첫 사진을 대표로 둔다.
        assertThat(service.prepareForExistingDestination("Q243", both, false))
                .extracting(PreparedCommonsPhoto::main).containsExactly(true, false);
        // 기존 여행지 추가도 저장 전 재검증(후보·원본 메타데이터 재조회)을 그대로 쓴다.
        org.mockito.Mockito.verify(wikidata, org.mockito.Mockito.times(3)).getCommonsSitelinkEntity("Q243");
        org.mockito.Mockito.verify(wikidata, org.mockito.Mockito.times(3)).getClaims("Q243", "P18");
        org.mockito.Mockito.verify(wikidata, org.mockito.Mockito.never()).getAutofillEntities(anyList());
        org.mockito.Mockito.verify(commons, org.mockito.Mockito.times(3)).getImageInfoForSave(anyList());
    }

    @Test
    void directUploadMainAndCommonsMainCannotBothBeChosen() {
        assertThatThrownBy(() -> service.prepare("Q243", List.of(
                new CommonsPhotoImportService.Selection("Tour Eiffel.jpg", true)), true))
                .isInstanceOf(CommonsPhotoSelectionException.class)
                .hasMessageContaining("대표");
        verifyNoInteractions(wikidata, commons, downloads);
    }

    /** 저장 전 재검증은 P18·P373 claims와 commonswiki 연결을 새로 받는다(전체 claims 아님). */
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

    /** 파일마다 다른 렌디션 URL. 병렬 다운로드에서도 어떤 파일의 결과인지 구분한다. 에펠탑 P18은 RENDITION 그대로다. */
    private static String rendition(String title) {
        return title.equals("File:Tour Eiffel.jpg") ? RENDITION
                : RENDITION.replace("Tour.jpg", title.substring(5).replace(' ', '_'));
    }

    /** Commons API prop=templates 응답처럼 파일 페이지에 쓰인 템플릿을 붙인다. */
    private void templates(ObjectNode page, String... names) {
        var array = page.putArray("templates");
        for (String name : names) array.addObject().put("ns", 10).put("title", "Template:" + name);
    }

    private ObjectNode page(String title, String license, String licenseShort, String licenseUrl,
                            String artist, String mime) {
        ObjectNode page = mapper.createObjectNode();
        page.put("title", title).put("pageid", 101).put("lastrevid", 9001);
        ObjectNode info = page.putArray("imageinfo").addObject();
        info.put("mime", mime).put("width", 3000).put("height", 2000)
                .put("thumburl", rendition(title) + TRACKING)
                .put("url", "https://upload.wikimedia.org/wikipedia/commons/a/a8/Tour.jpg" + TRACKING)
                .put("descriptionurl", "https://commons.wikimedia.org/wiki/" + title.replace(' ', '_'));
        ObjectNode metadata = info.putObject("extmetadata");
        metadata.putObject("Artist").put("value", artist);
        metadata.putObject("License").put("value", license);
        metadata.putObject("LicenseShortName").put("value", licenseShort);
        if (licenseUrl != null) metadata.putObject("LicenseUrl").put("value", licenseUrl);
        metadata.putObject("AttributionRequired").put("value", "true");
        return page;
    }
}
