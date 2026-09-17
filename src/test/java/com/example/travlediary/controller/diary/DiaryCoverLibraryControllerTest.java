package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.DiaryCoverLibraryAssetFile;
import com.example.travlediary.dto.DiaryCoverLibraryDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryMineDto;
import com.example.travlediary.dto.DiaryCoverLibraryPageDto;
import com.example.travlediary.dto.DiaryCoverLibraryReportForm;
import com.example.travlediary.dto.DiaryCoverLibrarySort;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.repository.diary.DiaryStickerMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
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

        String body = mockMvc.perform(get("/diaries/cover-library")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-library/list"))
                .andReturn().getResponse().getContentAsString();

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

    @Test
    void publishedDetailShowsTheLargePreviewAndMetadata() throws Exception {
        DiaryCoverLibraryItem item = item(11L, "여름 표지", "여행자");
        item.setDescription("바다의 색을 담은 표지입니다.");
        DiaryCoverLibraryElement note = element(103L, "NOTE");
        note.setTextContent("JEJU");
        note.setStyleType("DATE_LABEL");
        note.setColorType("SAGE");
        when(libraryQueryService.getPublishedDetail(11L))
                .thenReturn(new DiaryCoverLibraryDetailDto(item, List.of(note)));

        String body = mockMvc.perform(get("/diaries/cover-library/11")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-library/detail"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("여름 표지")
                .contains("바다의 색을 담은 표지입니다.")
                .contains("여행자")
                .contains("다운로드 9")
                .contains("diary-note-date-label")
                .contains("JEJU")
                .contains("내 디자인으로 받기")
                .contains("내 표지 디자인에 복사하여 자유롭게 수정할 수 있습니다.");
    }

    @Test
    void publishedDetailOffersOnlyIncludedPhotosAsPhotoReportTargets() throws Exception {
        DiaryCoverLibraryItem item = item(11L, "여름 표지", "여행자");
        DiaryCoverLibraryElement included = photo(101L,
                DiaryCoverLibraryPhotoShareMode.INCLUDED, 701L,
                "/diaries/cover-library/assets/701");
        DiaryCoverLibraryElement excluded = photo(102L,
                DiaryCoverLibraryPhotoShareMode.EXCLUDED, null, null);
        when(libraryQueryService.getPublishedDetail(11L))
                .thenReturn(new DiaryCoverLibraryDetailDto(item, List.of(included, excluded)));

        String body = mockMvc.perform(get("/diaries/cover-library/11")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("신고")
                .contains("표지 전체")
                .contains("공유 사진 1")
                .contains("저작권 침해")
                .contains("초상권·개인정보 침해")
                .contains("maxlength=\"1000\"")
                .doesNotContain("공유 사진 2");
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
                .andExpect(redirectedUrl("/diaries/cover-library/11"))
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
                .andExpect(redirectedUrl("/diaries/cover-library/11"))
                .andExpect(flash().attribute("coverLibraryError", "이미 신고한 항목입니다."));
    }

    @Test
    void malformedPhotoAssetIdReturnsASafeFlashMessage() throws Exception {
        mockMvc.perform(post("/diaries/cover-library/11/reports")
                        .with(authentication(loggedIn())).with(csrf())
                        .param("photoAssetId", "not-a-number")
                        .param("reasonCode", "COPYRIGHT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-library/11"))
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
    void downloadCreatesForTheAuthenticatedMemberAndRedirectsToTheEditor() throws Exception {
        DiaryCoverDesign created = new DiaryCoverDesign();
        created.setId(901L);
        when(userDetails.getId()).thenReturn(7L);
        when(libraryDownloadService.download(7L, 11L)).thenReturn(created);

        mockMvc.perform(post("/diaries/cover-library/11/download")
                        .with(authentication(loggedIn()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-designs/901/edit"))
                .andExpect(flash().attribute("coverDesignMessage", "내 디자인에 추가했습니다."));

        verify(libraryDownloadService).download(7L, 11L);
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

    @Test
    void unpublishedDetailIsNotAccessible() throws Exception {
        when(libraryQueryService.getPublishedDetail(99L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "공유 표지를 찾을 수 없습니다."));

        mockMvc.perform(get("/diaries/cover-library/99")
                        .with(authentication(loggedIn())))
                .andExpect(status().isNotFound());
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
