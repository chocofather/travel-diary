package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.DiaryCoverLibraryAssetFile;
import com.example.travlediary.dto.DiaryCoverLibraryDownloadResult;
import com.example.travlediary.dto.DiaryCoverLibraryMineDto;
import com.example.travlediary.dto.DiaryCoverLibraryPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportForm;
import com.example.travlediary.dto.DiaryCoverLibrarySort;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.repository.diary.DiaryStickerMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverDesignElementService;
import com.example.travlediary.service.diary.DiaryCoverDesignService;
import com.example.travlediary.service.diary.DiaryCoverLibraryQueryService;
import com.example.travlediary.service.diary.DiaryCoverLibraryDownloadService;
import com.example.travlediary.service.diary.DiaryCoverLibraryManagementService;
import com.example.travlediary.service.diary.DiaryCoverLibraryReportException;
import com.example.travlediary.service.diary.DiaryCoverLibraryReportService;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DiaryCoverLibraryController.class)
@Import({SecurityConfig.class, DiaryStickerCatalog.class})
class DiaryCoverLibraryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DiaryCoverLibraryQueryService libraryQueryService;
    @MockitoBean private DiaryCoverLibraryDownloadService libraryDownloadService;
    @MockitoBean private DiaryCoverLibraryManagementService libraryManagementService;
    @MockitoBean private DiaryCoverLibraryReportService libraryReportService;
    @MockitoBean private DiaryCoverDesignService coverDesignService;
    @MockitoBean private DiaryCoverDesignElementService coverDesignElementService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;
    @MockitoBean private CustomUserDetails userDetails;
    @MockitoBean private DiaryStickerMapper diaryStickerMapper;

    @BeforeEach
    void setUpStickerCatalog() {
        com.example.travlediary.support.DiaryStickerTestCatalogData.stub(diaryStickerMapper);
    }

    @Test
    void guestCannotBrowseDetailsOrAssets() throws Exception {
        mockMvc.perform(get("/diaries/cover-library"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/diaries/cover-library/11"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/diaries/cover-library/assets/701"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void guestCannotDownloadALibraryDesign() throws Exception {
        mockMvc.perform(post("/diaries/cover-library/11/download").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void guestCannotReportALibraryDesign() throws Exception {
        mockMvc.perform(post("/diaries/cover-library/11/reports").with(csrf()))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(libraryReportService);
    }

    @Test
    void guestCannotOpenOrChangeMine() throws Exception {
        mockMvc.perform(get("/diaries/cover-library/mine"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/diaries/cover-library/11/withdraw").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/diaries/cover-library/11/republish").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/diaries/cover-library/11/delete").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void authenticatedDownloadRequiresCsrf() throws Exception {
        mockMvc.perform(post("/diaries/cover-library/11/download")
                        .with(authentication(loggedIn())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(libraryDownloadService);
    }

    @Test
    void latestLibraryPageRendersSnapshotAttributionAndBothPhotoModes() throws Exception {
        DiaryCoverLibraryItem item = item(11L, "여름 표지", "탈퇴한 사용자");
        DiaryCoverLibraryElement excluded = photo(
                101L, DiaryCoverLibraryPhotoShareMode.EXCLUDED, null, null);
        DiaryCoverLibraryElement included = photo(
                102L, DiaryCoverLibraryPhotoShareMode.INCLUDED, 701L,
                "/diaries/cover-library/assets/701");
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(page(item, List.of(excluded, included), DiaryCoverLibrarySort.LATEST));
        // 아래 "내 보유 디자인"은 로그인 회원의 표지 디자인만 읽는다.
        DiaryCoverDesign owned = new DiaryCoverDesign();
        owned.setId(901L);
        owned.setName("받아 둔 표지");
        owned.setBaseCoverStyle("LEATHER_BLACK");
        when(userDetails.getId()).thenReturn(7L);
        when(coverDesignService.getMyDesigns(7L)).thenReturn(List.of(owned));

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-library/list"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("내 보유 디자인")
                .contains("받아 둔 표지")
                .contains("href=\"/diaries/cover-designs/901/edit\"");
        // 목록에서 바로 받기. 기존 다운로드 주소를 그대로 쓴다.
        assertThat(body).contains("data-cover-library-quick-download")
                .contains("action=\"/diaries/cover-library/11/download\"");
        verify(coverDesignService).getMyDesigns(7L);

        assertThat(body).contains("여름 표지")
                .contains("탈퇴한 사용자")
                .contains("다운로드 9")
                .contains("2026.09.17")
                .contains("사진 넣기")
                .contains("/diaries/cover-library/assets/701")
                .contains("diary-cover-leather-black")
                .contains("최신순")
                .contains("인기순")
                .contains("/diaries/cover-library/mine")
                .contains("내 공유 디자인");
        verify(libraryQueryService)
                .getPublishedPage(DiaryCoverLibrarySort.LATEST, 1);
    }

    @Test
    void popularSortAndPageArePassedThroughTheAllowListedEnum() throws Exception {
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.POPULAR, 3))
                .thenReturn(new DiaryCoverLibraryPageDto(
                        List.of(), Map.of(), DiaryCoverLibrarySort.POPULAR,
                        3, 3, 25, 12));

        mockMvc.perform(get("/diaries/cover-library")
                        .param("sort", "popular")
                        .param("page", "3")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk());

        verify(libraryQueryService)
                .getPublishedPage(DiaryCoverLibrarySort.POPULAR, 3);
    }

    /**
     * 상세 화면 없이 목록 카드가 설명·받기·신고를 모두 갖는다.
     * 카드에서 상세로 들어가는 링크는 없다.
     */
    @Test
    void libraryCardsCarryDescriptionDownloadAndReportWithoutADetailLink() throws Exception {
        DiaryCoverLibraryItem item = item(11L, "여름 표지", "여행자");
        item.setDescription("바다의 색을 담은 표지입니다.");
        DiaryCoverLibraryElement note = element(103L, "NOTE");
        note.setTextContent("JEJU");
        note.setStyleType("DATE_LABEL");
        note.setColorType("SAGE");
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(page(item, List.of(note), DiaryCoverLibrarySort.LATEST));

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-library/list"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("여름 표지", "바다의 색을 담은 표지입니다.", "by 여행자", "다운로드 9")
                .contains("diary-note-date-label")
                .contains("JEJU")
                .contains("action=\"/diaries/cover-library/11/download\"")
                .contains("data-report-action=\"/diaries/cover-library/11/reports\"")
                .contains("data-cover-report-modal")
                .doesNotContain("href=\"/diaries/cover-library/11\"")
                .doesNotContain("목록으로");
    }

    /** 카드의 신고 대상에는 라이브러리에 함께 공유된(INCLUDED) 사진만 들어간다. */
    @Test
    void libraryCardsOfferOnlyIncludedPhotosAsPhotoReportTargets() throws Exception {
        DiaryCoverLibraryItem item = item(11L, "여름 표지", "여행자");
        DiaryCoverLibraryElement included = photo(101L,
                DiaryCoverLibraryPhotoShareMode.INCLUDED, 701L,
                "/diaries/cover-library/assets/701");
        DiaryCoverLibraryElement excluded = photo(102L,
                DiaryCoverLibraryPhotoShareMode.EXCLUDED, null, null);
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(page(item, List.of(included, excluded), DiaryCoverLibrarySort.LATEST));

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String photos = between(body, "data-cover-report-photos", "</template>");
        assertThat(photos).contains("value=\"701\"")
                .contains("공유 사진 1")
                .doesNotContain("공유 사진 2");
        assertThat(body).contains("신고")
                .contains("표지 전체")
                .contains("저작권 침해")
                .contains("초상권·개인정보 침해")
                .contains("maxlength=\"1000\"");
    }

    @Test
    void authenticatedMemberCanSubmitAnIncludedPhotoReport() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/cover-library/11/reports")
                        .with(authentication(loggedIn())).with(csrf())
                        .param("photoAssetId", "701")
                        .param("reasonCode", "COPYRIGHT")
                        .param("description", "권리 침해 의심"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library"))
                .andExpect(flash().attribute("coverLibraryMessage", "신고가 접수되었습니다."));

        ArgumentCaptor<DiaryCoverLibraryReportForm> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryReportForm.class);
        verify(libraryReportService).submitReport(eq(7L), eq(11L), captor.capture());
        assertThat(captor.getValue().getPhotoAssetId()).isEqualTo(701L);
        assertThat(captor.getValue().getReasonCode()).isEqualTo("COPYRIGHT");
    }

    @Test
    void duplicateReportReturnsASafeFlashMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        org.mockito.Mockito.doThrow(new DiaryCoverLibraryReportException(
                        DiaryCoverLibraryReportException.Reason.DUPLICATE,
                        "internal duplicate key"))
                .when(libraryReportService).submitReport(eq(7L), eq(11L), any());

        mockMvc.perform(post("/diaries/cover-library/11/reports")
                        .with(authentication(loggedIn())).with(csrf())
                        .param("reasonCode", "SPAM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library"))
                .andExpect(flash().attribute("coverLibraryError", "이미 신고한 항목입니다."));
    }

    @Test
    void malformedPhotoAssetIdReturnsASafeFlashMessage() throws Exception {
        mockMvc.perform(post("/diaries/cover-library/11/reports")
                        .with(authentication(loggedIn())).with(csrf())
                        .param("photoAssetId", "not-a-number")
                        .param("reasonCode", "COPYRIGHT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library"))
                .andExpect(flash().attribute("coverLibraryError", "잘못된 신고 요청입니다."));

        verifyNoInteractions(libraryReportService);
    }

    @Test
    void unavailableReportTargetReturnsToTheLibrary() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        org.mockito.Mockito.doThrow(new DiaryCoverLibraryReportException(
                        DiaryCoverLibraryReportException.Reason.UNAVAILABLE,
                        "internal item state"))
                .when(libraryReportService).submitReport(eq(7L), eq(11L), any());

        mockMvc.perform(post("/diaries/cover-library/11/reports")
                        .with(authentication(loggedIn())).with(csrf())
                        .param("reasonCode", "SPAM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library"))
                .andExpect(flash().attribute("coverLibraryError",
                        "공개 중지되었거나 찾을 수 없는 표지 디자인입니다."));
    }

    @Test
    void downloadCreatesForTheAuthenticatedMemberAndShowsItInOwnedDesigns() throws Exception {
        DiaryCoverDesign created = new DiaryCoverDesign();
        created.setId(901L);
        when(userDetails.getId()).thenReturn(7L);
        when(libraryDownloadService.download(7L, 11L))
                .thenReturn(new DiaryCoverLibraryDownloadResult(created, 10L));

        mockMvc.perform(post("/diaries/cover-library/11/download")
                        .with(authentication(loggedIn()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library#cover-library-owned-901"))
                .andExpect(flash().attribute("coverDesignMessage",
                        "내 디자인에 추가했습니다. 표지를 눌러 바로 편집할 수 있어요."))
                .andExpect(flash().attribute("coverDesignAddedId", 901L))
                // 카드 숫자는 서버가 반영한 최종 다운로드 수로만 고친다
                .andExpect(flash().attribute("coverLibraryDownloadedItemId", 11L))
                .andExpect(flash().attribute("coverLibraryDownloadCount", 10L));

        verify(libraryDownloadService).download(7L, 11L);
    }

    @Test
    void downloadingACurrentlyOwnedDesignReturnsASafeMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(libraryDownloadService.download(7L, 11L))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "internal"));

        mockMvc.perform(post("/diaries/cover-library/11/download")
                        .with(authentication(loggedIn()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library"))
                .andExpect(flash().attribute("coverLibraryError", "이미 내 보유 디자인에 있는 표지입니다."));
    }

    /**
     * 지금 보유 중인 표지(보유 디자인의 출처가 그 표지)는 받기(+) 대신 보유중으로 보인다.
     * 보유 디자인 중 라이브러리에서 받은 것은 출처가 표시되고 다시 공유하는 링크가 없다.
     */
    @Test
    void ownedLibraryItemsShowOwnedAndLibraryDesignsCannotBeSharedAgain() throws Exception {
        DiaryCoverLibraryItem owned = item(11L, "받은 표지", "여행자");
        DiaryCoverLibraryItem notOwned = item(12L, "안 받은 표지", "여행자");
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(new DiaryCoverLibraryPageDto(List.of(owned, notOwned),
                        Map.of(11L, List.of(), 12L, List.of()), DiaryCoverLibrarySort.LATEST,
                        1, 1, 2, 12));
        DiaryCoverDesign downloaded = ownedDesign(901L, "받은 표지");
        downloaded.setSourceLibraryItemId(11L);
        DiaryCoverDesign made = ownedDesign(902L, "직접 만든 표지");
        when(userDetails.getId()).thenReturn(7L);
        when(coverDesignService.getMyDesigns(7L)).thenReturn(List.of(downloaded, made));

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String ownedCard = between(body, "data-library-item=\"11\"", "data-library-item=\"12\"");
        assertThat(ownedCard).contains("diary-library-owned-badge").contains("보유중")
                .doesNotContain("/diaries/cover-library/11/download")
                .contains("data-report-action=\"/diaries/cover-library/11/reports\"");
        String otherCard = between(body, "data-library-item=\"12\"", "data-cover-library-owned-badge");
        assertThat(otherCard).doesNotContain("diary-library-owned-badge")
                .contains("action=\"/diaries/cover-library/12/download\"")
                .contains("aria-label=\"안 받은 표지 표지를 내 디자인에 추가\"");

        assertThat(body).contains("라이브러리에서 받은 디자인")
                .doesNotContain("/diaries/cover-designs/901/library-share")
                .contains("/diaries/cover-designs/902/library-share");
    }

    /**
     * 카드 상태 우선순위: 내가 공유한 표지 → "내 공유"(받기·보유중·신고 없음),
     * 그다음 지금 보유 중 → "보유중", 나머지 → 받기(+).
     * 내가 공유한 표지는 그 표지에서 받은 디자인이 남아 있어도 "내 공유"로 보인다.
     */
    @Test
    void cardStatePrefersSharedByMeThenOwnedThenDownload() throws Exception {
        DiaryCoverLibraryItem sharedByMe = item(11L, "내가 올린 표지", "나");
        sharedByMe.setCreatorUserId(7L);
        DiaryCoverLibraryItem owned = item(12L, "받은 표지", "여행자");
        owned.setCreatorUserId(8L);
        DiaryCoverLibraryItem other = item(13L, "새 표지", "여행자");
        other.setCreatorUserId(8L);
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(new DiaryCoverLibraryPageDto(List.of(sharedByMe, owned, other),
                        Map.of(11L, List.of(), 12L, List.of(), 13L, List.of()),
                        DiaryCoverLibrarySort.LATEST, 1, 1, 3, 12));
        DiaryCoverDesign fromMine = ownedDesign(901L, "예전에 받은 내 표지");
        fromMine.setSourceLibraryItemId(11L);
        DiaryCoverDesign fromOther = ownedDesign(902L, "받은 표지");
        fromOther.setSourceLibraryItemId(12L);
        when(userDetails.getId()).thenReturn(7L);
        when(coverDesignService.getMyDesigns(7L)).thenReturn(List.of(fromMine, fromOther));

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sharedCard = between(body, "data-library-item=\"11\"", "data-library-item=\"12\"");
        assertThat(sharedCard).contains("diary-library-shared-badge").contains("내 공유")
                .doesNotContain("diary-library-owned-badge")
                .doesNotContain("/diaries/cover-library/11/download")
                .doesNotContain("data-cover-report-open");
        String ownedCard = between(body, "data-library-item=\"12\"", "data-library-item=\"13\"");
        assertThat(ownedCard).contains("diary-library-owned-badge")
                .doesNotContain("diary-library-shared-badge")
                .doesNotContain("/diaries/cover-library/12/download")
                .contains("data-report-action=\"/diaries/cover-library/12/reports\"");
        String otherCard = between(body, "data-library-item=\"13\"", "data-cover-library-owned-badge");
        assertThat(otherCard).contains("action=\"/diaries/cover-library/13/download\"")
                .contains("data-report-action=\"/diaries/cover-library/13/reports\"")
                .doesNotContain("diary-library-shared-badge")
                .doesNotContain("diary-library-owned-badge");
        // 접기/펼치기는 라이브러리 본문만 대상으로 하고, 접힌 머리에는 공개 디자인 개수가 남는다
        assertThat(body).contains("aria-controls=\"cover-library-section-body\"")
                .contains("id=\"cover-library-section-body\"")
                .contains("3개의 디자인");
    }

    @Test
    void downloadingMyOwnSharedDesignReturnsASafeMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(libraryDownloadService.download(7L, 11L))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "internal"));

        mockMvc.perform(post("/diaries/cover-library/11/download")
                        .with(authentication(loggedIn()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library"))
                .andExpect(flash().attribute("coverLibraryError",
                        "직접 공유한 디자인은 다시 받을 수 없습니다."));
    }

    /** 보유중 판별은 과거 이력이 아니라 지금 가진 디자인의 출처만 본다. */
    @Test
    void ownedLibraryItemIdsComeOnlyFromCurrentDesignsWithASource() {
        DiaryCoverDesign downloaded = ownedDesign(901L, "받은 표지");
        downloaded.setSourceLibraryItemId(11L);
        DiaryCoverDesign made = ownedDesign(902L, "직접 만든 표지");

        assertThat(DiaryCoverLibraryController.ownedLibraryItemIds(List.of(downloaded, made)))
                .containsExactly(11L);
        assertThat(DiaryCoverLibraryController.ownedLibraryItemIds(List.of(made))).isEmpty();
    }

    @Test
    void unpublishedDownloadFailureReturnsToTheLibraryWithoutLeakingInternals()
            throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(libraryDownloadService.download(7L, 11L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "internal mapper detail"));

        mockMvc.perform(post("/diaries/cover-library/11/download")
                        .with(authentication(loggedIn()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library"))
                .andExpect(flash().attribute("coverLibraryError",
                        "공개 중지되었거나 찾을 수 없는 표지 디자인입니다."));
    }

    /** 예전 상세 주소는 상세를 읽지 않고 라이브러리 목록으로 보낸다. (공개 여부와 관계없이 같다) */
    @Test
    void oldDetailAddressFallsBackToTheLibraryList() throws Exception {
        mockMvc.perform(get("/diaries/cover-library/99")
                        .with(authentication(loggedIn())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library"));

        verifyNoInteractions(libraryQueryService);
    }

    @Test
    void mineShowsOnlyTheCurrentMembersItemsAndStatusSpecificActions() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryCoverLibraryItem published = item(11L, "공개 표지", "공유 당시 이름");
        published.setStatus(DiaryCoverLibraryItemStatus.PUBLISHED);
        DiaryCoverLibraryItem withdrawn = item(12L, "중지 표지", "공유 당시 이름");
        withdrawn.setStatus(DiaryCoverLibraryItemStatus.WITHDRAWN);
        DiaryCoverLibraryItem blocked = item(13L, "제한 표지", "공유 당시 이름");
        blocked.setStatus(DiaryCoverLibraryItemStatus.BLOCKED);
        when(libraryQueryService.getMine(7L)).thenReturn(new DiaryCoverLibraryMineDto(
                List.of(published, withdrawn, blocked),
                Map.of(11L, List.of(), 12L, List.of(), 13L, List.of())));

        String body = mockMvc.perform(get("/diaries/cover-library/mine")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-library/mine"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("공개 표지", "중지 표지", "제한 표지")
                .contains("공유 당시 이름")
                .contains("공개중", "공개중지", "이용 제한")
                .contains("/diaries/cover-library/11/withdraw")
                .contains("/diaries/cover-library/12/republish")
                .contains("/diaries/cover-library/11/delete")
                .contains("/diaries/cover-library/12/delete")
                .doesNotContain("/diaries/cover-library/13/republish")
                .doesNotContain("/diaries/cover-library/13/delete")
                .contains("삭제 후 다시 공개할 수 없습니다. 이미 다른 회원이 받은 디자인에는 영향을 주지 않습니다.");
        verify(libraryQueryService).getMine(7L);
    }

    @Test
    void ownerActionsUseTheAuthenticatedMemberAndReturnToMine() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/cover-library/11/withdraw")
                        .with(authentication(loggedIn())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library/mine"))
                .andExpect(flash().attribute("coverLibraryMessage",
                        "라이브러리 공개를 중지했습니다."));
        mockMvc.perform(post("/diaries/cover-library/12/republish")
                        .with(authentication(loggedIn())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library/mine"))
                .andExpect(flash().attribute("coverLibraryMessage",
                        "라이브러리에 다시 공개했습니다."));
        mockMvc.perform(post("/diaries/cover-library/13/delete")
                        .with(authentication(loggedIn())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library/mine"))
                .andExpect(flash().attribute("coverLibraryMessage",
                        "공유 디자인을 삭제했습니다."));

        verify(libraryManagementService).withdraw(7L, 11L);
        verify(libraryManagementService).republish(7L, 12L);
        verify(libraryManagementService).delete(7L, 13L);
    }

    @Test
    void ownerActionsRequireCsrf() throws Exception {
        mockMvc.perform(post("/diaries/cover-library/11/withdraw")
                        .with(authentication(loggedIn())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(libraryManagementService);
    }

    @Test
    void rejectedOwnerActionUsesASafeManagementMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "internal ownership detail"))
                .when(libraryManagementService).withdraw(7L, 11L);

        mockMvc.perform(post("/diaries/cover-library/11/withdraw")
                        .with(authentication(loggedIn())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library/mine"))
                .andExpect(flash().attribute("coverLibraryError",
                        "관리할 공유 디자인을 찾을 수 없거나 상태를 변경할 수 없습니다."));
    }

    @Test
    void activeAssetIsServedPrivatelyWithShortRevalidation(@TempDir Path directory)
            throws Exception {
        byte[] image = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        Path path = directory.resolve("shared.jpg");
        Files.write(path, image);
        when(libraryQueryService.getActiveAsset(701L))
                .thenReturn(new DiaryCoverLibraryAssetFile(path, "image/jpeg", image.length));

        mockMvc.perform(get("/diaries/cover-library/assets/701")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(image))
                .andExpect(header().string("Cache-Control",
                        "max-age=60, must-revalidate, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void blockedAssetReturnsNotFoundWithoutAFileResponse() throws Exception {
        when(libraryQueryService.getActiveAsset(701L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "공유 사진을 찾을 수 없습니다."));

        mockMvc.perform(get("/diaries/cover-library/assets/701")
                        .with(authentication(loggedIn())))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    /**
     * 주소창·북마크로 라이브러리 화면을 직접 열면(브라우저 페이지 이동)
     * 나의 여행일기로 보내고 같은 화면 위치로 표지 디자인 패널을 연다. 이때 목록은 읽지 않는다.
     */
    @Test
    void directPageNavigationOpensThePanelOnTheDiaryListAtTheSameView() throws Exception {
        mockMvc.perform(get("/diaries/cover-library").param("sort", "POPULAR").param("page", "2")
                        .header("Sec-Fetch-Mode", "navigate")
                        .with(authentication(loggedIn())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/diaries?coverLibrary=/diaries/cover-library?sort%3DPOPULAR%26page%3D2"));
        mockMvc.perform(get("/diaries/cover-library/11")
                        .header("Sec-Fetch-Mode", "navigate")
                        .flashAttr("coverLibraryMessage", "신고가 접수되었습니다.")
                        .with(authentication(loggedIn())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverLibrary=/diaries/cover-library"))
                .andExpect(flash().attribute("coverLibraryMessage", "신고가 접수되었습니다."));
        mockMvc.perform(get("/diaries/cover-library/mine")
                        .header("Sec-Fetch-Mode", "navigate")
                        .with(authentication(loggedIn())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverLibrary=/diaries/cover-library/mine"));

        verifyNoInteractions(libraryQueryService, coverDesignService);
    }

    /** 패널이 불러오는 요청(fetch)은 지금처럼 라이브러리 화면을 그대로 돌려준다. */
    @Test
    void panelFetchStillRendersTheLibraryView() throws Exception {
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(emptyLibraryPage());

        mockMvc.perform(get("/diaries/cover-library")
                        .header("Sec-Fetch-Mode", "cors")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-library/list"));
    }

    /**
     * 패널 아래 내 보유 디자인은 로그인한 사람의 것만 읽는다. (요청에 실린 소유자 값은 쓰지 않는다)
     * 편집·공유·⋯ 삭제 동작은 기존 경로를 그대로 쓰고, 새 디자인 만들기는 패널 머리에 있다.
     */
    @Test
    void ownedDesignsShowOnlyMyOwnDesignsWithTheirActions() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(emptyLibraryPage());
        when(coverDesignService.getMyDesigns(7L)).thenReturn(List.of(ownedDesign(5L, "제주 여행")));

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("내 보유 디자인", "제주 여행");
        // 표지 미리보기는 목록 카드와 같은 재질 클래스를 쓴다
        assertThat(body).contains("diary-cover-canvas").contains("diary-cover-leather-black")
                .contains("diary-cover-surface");
        assertThat(body).contains("diary-cover-design-gallery")
                .contains("href=\"/diaries/cover-designs/5/edit\"")
                .contains("href=\"/diaries/cover-designs/5/library-share\"")
                .contains("action=\"/diaries/cover-designs/5/delete\"")
                .contains("diary-book-menu")
                .contains("/js/diary-book-menu.js")
                .contains("href=\"/diaries/cover-designs/new\"");
        verify(coverDesignService).getMyDesigns(7L);
    }

    /**
     * 보유 디자인에는 꾸민 표지가 그대로 줄어 보인다. (사진·스티커·라벨)
     * 요소는 카드마다 따로 묻지 않고 디자인 번호를 모아 한 번에 읽는다.
     */
    @Test
    void ownedDesignsShowTheFinishedCoversAndAskForTheElementsOnlyOnce() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(emptyLibraryPage());
        when(coverDesignService.getMyDesigns(7L))
                .thenReturn(List.of(ownedDesign(5L, "제주 여행"), ownedDesign(6L, "빈티지")));
        when(coverDesignElementService.getElementsByDesign(List.of(5L, 6L), 7L))
                .thenReturn(Map.of(
                        5L, List.of(ownedPhoto(101L, "/uploads/diary-cover-designs/a.jpg"),
                                ownedSticker(100L, "/images/diary/stickers/travel/plane.svg"),
                                ownedLabel(102L, "JEJU 2026", "park-dahyun")),
                        6L, List.of(ownedLabel(103L, "여행의 순간", null))));

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 내 보유 디자인의 사진도 저장 키가 아니라 통제된 주소로만 그려진다
        assertThat(body).contains("/diaries/cover-designs/5/elements/101/photo")
                .doesNotContain("/uploads/diary-cover-designs/a.jpg")
                .contains("/images/diary/stickers/travel/plane.svg")
                .contains("is-photo-full");
        // 라벨은 편집 화면과 같은 글꼴 class·상대좌표를 쓴다. 글꼴이 없으면 class 없이 기본 글꼴이다.
        String label = between(body, "JEJU 2026", "</figure>");
        assertThat(between(body, "class=\"diary-canvas-item diary-label\"", "JEJU 2026"))
                .contains("left:38.00000%").contains("--diary-label-chars:9");
        assertThat(body).contains("diary-font-park-dahyun");
        assertThat(between(body, "여행의 순간", "</figure>")).doesNotContain("diary-font-");
        assertThat(label).doesNotContain("data-element-id");
        // 보기 전용이다. 조작 손잡이도 저장 주소도 없다
        assertThat(body).doesNotContain("diary-resize-handle")
                .doesNotContain("diary-rotate-handle")
                .doesNotContain("diary-layer-action")
                .doesNotContain("data-position-url")
                .doesNotContain("is-editable");
        assertThat(body).contains("/js/diary-tape-repeat.js").contains("/css/diary-fonts.css")
                .doesNotContain("/js/diary-canvas-drag.js");

        verify(coverDesignElementService).getElementsByDesign(List.of(5L, 6L), 7L);
    }

    @Test
    void emptyOwnedDesignsPointToTheLibraryAndTheNewDesignAction() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(libraryQueryService.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1))
                .thenReturn(emptyLibraryPage());
        when(coverDesignService.getMyDesigns(7L)).thenReturn(List.of());

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("아직 보유한 표지 디자인이 없습니다.")
                .contains("href=\"/diaries/cover-designs/new\"")
                .doesNotContain("action=\"/diaries/cover-designs\"");
    }

    private DiaryCoverLibraryPageDto emptyLibraryPage() {
        return new DiaryCoverLibraryPageDto(
                List.of(), Map.of(), DiaryCoverLibrarySort.LATEST, 1, 1, 0, 12);
    }

    private DiaryCoverDesign ownedDesign(Long id, String name) {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setId(id);
        design.setUserId(7L);
        design.setName(name);
        design.setBaseCoverStyle("LEATHER_BLACK");
        return design;
    }

    private DiaryCoverDesignElement ownedSticker(Long id, String imageUrl) {
        DiaryCoverDesignElement element = new DiaryCoverDesignElement();
        element.setId(id);
        element.setDesignId(5L);
        element.setElementType("STICKER");
        element.setImageUrl(imageUrl);
        // 공용 asset 이라 저장 경로가 곧 공개 주소다.
        element.setViewUrl(imageUrl);
        element.setPositionX(new java.math.BigDecimal("0.38000"));
        element.setPositionY(new java.math.BigDecimal("0.38000"));
        element.setWidth(new java.math.BigDecimal("0.22000"));
        element.setHeight(new java.math.BigDecimal("0.22000"));
        element.setRotation(new java.math.BigDecimal("0.00"));
        element.setZIndex(0);
        return element;
    }

    /** 조회 서비스를 거친 사진 요소. 화면이 쓸 통제된 주소까지 채워 준다. */
    private DiaryCoverDesignElement ownedPhoto(Long id, String imageUrl) {
        DiaryCoverDesignElement element = ownedSticker(id, imageUrl);
        element.setElementType("PHOTO");
        element.setPhotoStyle("FULL");
        element.setViewUrl("/diaries/cover-designs/5/elements/" + id + "/photo");
        return element;
    }

    private DiaryCoverDesignElement ownedLabel(Long id, String text, String textFont) {
        DiaryCoverDesignElement element = ownedSticker(id, null);
        element.setElementType("TEXT");
        element.setTextContent(text);
        element.setTextFont(textFont);
        element.setWidth(new java.math.BigDecimal("0.44000"));
        element.setHeight(new java.math.BigDecimal("0.09000"));
        return element;
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private UsernamePasswordAuthenticationToken loggedIn() {
        return new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    }

    private DiaryCoverLibraryPageDto page(
            DiaryCoverLibraryItem item,
            List<DiaryCoverLibraryElement> elements,
            DiaryCoverLibrarySort sort) {
        return new DiaryCoverLibraryPageDto(
                List.of(item), Map.of(item.getId(), elements), sort,
                1, 1, 1, 12);
    }

    private DiaryCoverLibraryItem item(Long id, String title, String creator) {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setId(id);
        item.setTitle(title);
        item.setCreatorDisplayName(creator);
        item.setBaseCoverStyle("LEATHER_BLACK");
        item.setDownloadCount(9L);
        item.setSnapshotVersion(1);
        item.setPublishedAt(Timestamp.from(Instant.parse("2026-09-17T03:00:00Z")));
        return item;
    }

    private DiaryCoverLibraryElement photo(
            Long id, DiaryCoverLibraryPhotoShareMode mode,
            Long assetId, String imageUrl) {
        DiaryCoverLibraryElement element = element(id, "PHOTO");
        element.setPhotoStyle("POLAROID");
        element.setPhotoShareMode(mode);
        element.setPhotoAssetId(assetId);
        element.setImageUrl(imageUrl);
        return element;
    }

    private DiaryCoverLibraryElement element(Long id, String type) {
        DiaryCoverLibraryElement element = new DiaryCoverLibraryElement();
        element.setId(id);
        element.setLibraryItemId(11L);
        element.setElementType(type);
        element.setPositionX(new BigDecimal("0.10000"));
        element.setPositionY(new BigDecimal("0.20000"));
        element.setWidth(new BigDecimal("0.30000"));
        element.setHeight(new BigDecimal("0.40000"));
        element.setRotation(new BigDecimal("5.00"));
        element.setZIndex(1);
        return element;
    }
}
