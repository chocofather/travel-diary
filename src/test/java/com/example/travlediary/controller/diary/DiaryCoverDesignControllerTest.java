package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.DiaryCoverLibraryRegistrationRequest;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.repository.diary.DiaryStickerMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverDesignElementService;
import com.example.travlediary.service.diary.DiaryCoverDesignService;
import com.example.travlediary.service.diary.DiaryCoverLibraryRegistrationService;
import com.example.travlediary.service.diary.DiaryLabelFontCatalog;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DiaryCoverDesignController.class)
@Import({SecurityConfig.class, DiaryStickerCatalog.class, DiaryLabelFontCatalog.class})
class DiaryCoverDesignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryCoverDesignService diaryCoverDesignService;
    @MockitoBean
    private DiaryCoverDesignElementService diaryCoverDesignElementService;
    @MockitoBean
    private DiaryCoverLibraryRegistrationService diaryCoverLibraryRegistrationService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private DiaryPrivatePhotoStorage diaryPrivatePhotoStorage;
    @MockitoBean
    private CustomUserDetails userDetails;
    @MockitoBean
    private DiaryStickerMapper diaryStickerMapper;

    /** 붙일 수 있는 스티커 목록. 실제 manifest 를 그대로 읽는다. */
    @Autowired
    private DiaryStickerCatalog diaryStickerCatalog;
    /** 파일 정리는 업로드 폴더 안에서만 일어나야 하므로, 시험용 폴더를 심어 확인한다. */
    @Autowired
    private DiaryCoverDesignController controller;

    @BeforeEach
    void setUpStickerCatalog() {
        com.example.travlediary.support.DiaryStickerTestCatalogData.stub(diaryStickerMapper);
    }

    @Test
    void guestIsSentToLogin() throws Exception {
        mockMvc.perform(get("/diaries/cover-designs"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void guestCannotOpenOrSubmitTheLibraryShareForm() throws Exception {
        mockMvc.perform(get("/diaries/cover-designs/5/library-share"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/diaries/cover-designs/5/library-share").with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(diaryCoverLibraryRegistrationService, never()).register(any(), any());
    }

    /**
     * 예전 보관함 주소는 전용 화면 대신 나의 여행일기 위 표지 디자인 패널로 보낸다.
     * (보유 디자인 목록은 패널 아래쪽이 그린다 — DiaryCoverLibraryControllerTest)
     * 앞 요청이 남긴 안내 문구도 다음 화면까지 옮기고, 목록을 따로 읽지 않는다.
     */
    @Test
    void theOldShelfAddressOpensTheCoverDesignPanelOnTheDiaryList() throws Exception {
        mockMvc.perform(get("/diaries/cover-designs")
                        .flashAttr("coverDesignMessage", "표지 디자인을 삭제했습니다.")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverDesigns=open"))
                .andExpect(flash().attribute("coverDesignMessage", "표지 디자인을 삭제했습니다."));

        verify(diaryCoverDesignService, never()).getMyDesigns(any());
    }

    @Test
    void shareFormReusesTheFinishedCoverAndListsEachPhotoAsExcludedByDefault() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L))
                .thenReturn(design(5L, "제주 여행"));
        when(diaryCoverDesignElementService.getElements(5L, 7L))
                .thenReturn(List.of(
                        photo(101L, "/uploads/diary-cover-designs/a.jpg"),
                        sticker(102L, "/images/diary/stickers/travel/plane.svg")));

        String body = mockMvc.perform(get("/diaries/cover-designs/5/library-share")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-library-share"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-cover-canvas")
                // 사진은 저장 키가 아니라 통제된 주소로만 그려진다
                .contains(photoViewUrl(101L))
                .doesNotContain("/uploads/diary-cover-designs/a.jpg")
                .contains("photoModes[101]")
                .contains("value=\"EXCLUDED\" checked=\"checked\"")
                .contains("사진 제외")
                .contains("사진까지 함께 공유")
                .contains("이 사진을 직접 촬영했거나")
                .contains("/js/diary-cover-library-share.js");
    }

    @Test
    void omittedPhotoModesRemainExcludedInTheRegistrationService() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/cover-designs/5/library-share")
                        .param("title", "제주 여행 표지")
                        .param("description", "여름 바다 표지")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverDesigns=open"))
                .andExpect(flash().attribute("coverDesignMessage",
                        "표지 디자인을 라이브러리에 공유했습니다."));

        ArgumentCaptor<DiaryCoverLibraryRegistrationRequest> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryRegistrationRequest.class);
        verify(diaryCoverLibraryRegistrationService).register(eq(7L), captor.capture());
        assertThat(captor.getValue().sourceCoverDesignId()).isEqualTo(5L);
        assertThat(captor.getValue().title()).isEqualTo("제주 여행 표지");
        assertThat(captor.getValue().description()).isEqualTo("여름 바다 표지");
        assertThat(captor.getValue().photoSelections()).isEmpty();
    }

    @Test
    void includedPhotoAndRightsConfirmationArePassedToTheRegistrationService() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/cover-designs/5/library-share")
                        .param("title", "사진 포함 표지")
                        .param("photoModes[101]", "INCLUDED")
                        .param("photoModes[102]", "EXCLUDED")
                        .param("rightsConfirmed", "true")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverDesigns=open"));

        ArgumentCaptor<DiaryCoverLibraryRegistrationRequest> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryRegistrationRequest.class);
        verify(diaryCoverLibraryRegistrationService).register(eq(7L), captor.capture());
        assertThat(captor.getValue().photoSelections()).containsOnlyKeys(101L, 102L);
        assertThat(captor.getValue().photoSelections().get(101L).mode())
                .isEqualTo(DiaryCoverLibraryPhotoShareMode.INCLUDED);
        assertThat(captor.getValue().photoSelections().get(101L).rightsConfirmed()).isTrue();
        assertThat(captor.getValue().photoSelections().get(102L).mode())
                .isEqualTo(DiaryCoverLibraryPhotoShareMode.EXCLUDED);
    }

    @Test
    void missingRightsConfirmationIsReportedWithoutClaimingSuccess() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "사진 공유 권리를 확인해 주세요."))
                .when(diaryCoverLibraryRegistrationService).register(eq(7L), any());

        mockMvc.perform(post("/diaries/cover-designs/5/library-share")
                        .param("title", "사진 포함 표지")
                        .param("photoModes[101]", "INCLUDED")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-designs/5/library-share"))
                .andExpect(flash().attribute("coverDesignError", "사진 공유 권리를 확인해 주세요."));

        ArgumentCaptor<DiaryCoverLibraryRegistrationRequest> captor =
                ArgumentCaptor.forClass(DiaryCoverLibraryRegistrationRequest.class);
        verify(diaryCoverLibraryRegistrationService).register(eq(7L), captor.capture());
        assertThat(captor.getValue().photoSelections().get(101L).rightsConfirmed()).isFalse();
    }

    @Test
    void fileStorageFailureDoesNotExposeItsInternalPath() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        org.mockito.Mockito.doThrow(new IllegalStateException(
                        "/private/cover-library/secret/photo.jpg 저장 실패"))
                .when(diaryCoverLibraryRegistrationService).register(eq(7L), any());

        mockMvc.perform(post("/diaries/cover-designs/5/library-share")
                        .param("title", "사진 포함 표지")
                        .param("photoModes[101]", "INCLUDED")
                        .param("rightsConfirmed", "true")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-designs/5/library-share"))
                .andExpect(flash().attribute("coverDesignError",
                        "표지 디자인을 공유하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    /** 라이브러리에서 받은 디자인은 공유 화면을 열지 않고, 공유 등록이 거부되면 안내와 함께 돌려보낸다. */
    @Test
    void libraryDownloadedDesignCannotOpenOrSubmitTheShareForm() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryCoverDesign downloaded = design(5L, "받은 표지");
        downloaded.setSourceLibraryItemId(11L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(downloaded);

        mockMvc.perform(get("/diaries/cover-designs/5/library-share")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverDesigns=open"))
                .andExpect(flash().attribute("coverDesignError",
                        "라이브러리에서 받은 디자인은 다시 공유할 수 없습니다."));
        verify(diaryCoverDesignElementService, never()).getElements(any(), any());

        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT, "라이브러리에서 받은 디자인은 다시 공유할 수 없습니다."))
                .when(diaryCoverLibraryRegistrationService).register(eq(7L), any());
        mockMvc.perform(post("/diaries/cover-designs/5/library-share")
                        .param("title", "남의 표지를 내 것처럼")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverDesigns=open"))
                .andExpect(flash().attribute("coverDesignError",
                        "라이브러리에서 받은 디자인은 다시 공유할 수 없습니다."));
    }

    @Test
    void anotherUsersDesignCannotBeShared() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "표지 디자인을 찾을 수 없습니다."))
                .when(diaryCoverLibraryRegistrationService).register(eq(7L), any());

        mockMvc.perform(post("/diaries/cover-designs/99/library-share")
                        .param("title", "남의 표지")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverDesigns=open"))
                .andExpect(flash().attribute("coverDesignError",
                        "표지 디자인을 찾을 수 없거나 공유 권한이 없습니다."));
    }

    @Test
    void manipulatedPhotoElementIdIsRejectedByTheRegistrationService() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "사진 공유 대상을 다시 선택해 주세요."))
                .when(diaryCoverLibraryRegistrationService).register(eq(7L), any());

        mockMvc.perform(post("/diaries/cover-designs/5/library-share")
                        .param("title", "조작 요청")
                        .param("photoModes[999]", "INCLUDED")
                        .param("rightsConfirmed", "true")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-designs/5/library-share"))
                .andExpect(flash().attribute("coverDesignError",
                        "사진 공유 대상을 다시 선택해 주세요."));
    }

    @Test
    void aDesignWithoutPhotosCanBeSharedWithoutAnEmptyPhotoSection() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L))
                .thenReturn(design(5L, "글씨만 있는 표지"));
        when(diaryCoverDesignElementService.getElements(5L, 7L))
                .thenReturn(List.of(sticker(102L, "/images/diary/stickers/travel/plane.svg")));

        String body = mockMvc.perform(get("/diaries/cover-designs/5/library-share")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("data-photo-sharing-options")
                .doesNotContain("data-rights-confirmation");

        mockMvc.perform(post("/diaries/cover-designs/5/library-share")
                        .param("title", "글씨만 있는 표지")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverDesigns=open"));

        verify(diaryCoverLibraryRegistrationService).register(eq(7L), any());
    }

    /** 새 여행일기 화면은 내 디자인 목록만 조각으로 다시 받아 올 수 있다. */
    @Test
    void theChoiceFragmentReturnsOnlyMyLatestDesigns() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesigns(7L))
                .thenReturn(List.of(design(5L, "제주 여행")));
        when(diaryCoverDesignElementService.getElementsByDesign(List.of(5L), 7L))
                .thenReturn(Map.of(5L, List.of()));

        String body = mockMvc.perform(get("/diaries/cover-designs/choices")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-design-choices :: choices"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("data-cover-design-list")
                .contains("data-cover-design-create")
                .contains("target=\"_blank\"")
                .contains("제주 여행")
                .contains("data-cover-design-option");
        verify(diaryCoverDesignService).getMyDesigns(7L);
        verify(diaryCoverDesignElementService).getElementsByDesign(List.of(5L), 7L);
    }

    /** 저장 POST를 했을 때만 입력한 기본값으로 디자인을 처음 만든다. */
    @Test
    void savingANewDesignCreatesItForTheFirstTime() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.create(eq(7L), any())).thenReturn(design(5L, "새 표지 디자인"));

        mockMvc.perform(post("/diaries/cover-designs")
                        .param("name", "가을 표지")
                        .param("baseCoverStyle", "HARDCOVER_NAVY")
                        .param("backgroundColor", "#c9b79a")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-designs/5/edit"));

        ArgumentCaptor<DiaryCoverDesign> captor = ArgumentCaptor.forClass(DiaryCoverDesign.class);
        verify(diaryCoverDesignService).create(eq(7L), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("가을 표지");
        assertThat(captor.getValue().getBaseCoverStyle()).isEqualTo("HARDCOVER_NAVY");
        assertThat(captor.getValue().getBackgroundColor()).isEqualTo("#c9b79a");
    }

    /** 신규 화면을 열거나 닫는 것만으로는 디자인 행이나 요소를 만들지 않는다. */
    @Test
    void openingTheNewEditorDoesNotCreateAnything() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        String body = mockMvc.perform(get("/diaries/cover-designs/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-design-edit"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("새 표지 디자인")
                .contains("action=\"/diaries/cover-designs\"")
                .contains("디자인 저장")
                .doesNotContain("data-create-url=")
                .doesNotContain("이 디자인 삭제");
        verify(diaryCoverDesignService, never()).create(any(), any());
        verify(diaryCoverDesignElementService, never()).getElements(any(), any());
    }

    /** 편집 화면은 바탕 고치기 + 스티커 붙이기까지다. 사진/라벨은 아직 없다. */
    @Test
    void theEditorOffersTheBasicsAndStickers() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design(5L, "제주 여행"));
        when(diaryCoverDesignElementService.getElements(5L, 7L)).thenReturn(List.of());

        String body = mockMvc.perform(get("/diaries/cover-designs/5/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-design-edit"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("name=\"baseCoverStyle\"").contains("name=\"backgroundColor\"");
        assertThat(body).contains("action=\"/diaries/cover-designs/5/update\"");
        assertThat(body).contains("action=\"/diaries/cover-designs/5/delete\"");
        // 표지가 곧 꾸미는 자리다. 엔진은 이 표시를 보고 붙는다
        assertThat(body).contains("diary-cover-canvas is-editable");
        assertThat(body).contains("diary-cover-surface");
        // 스티커 붙이기는 페이지 다꾸와 같은 picker/스크립트를 쓴다
        assertThat(body).contains("id=\"diary-sticker-button\"");
        assertThat(body).contains("data-create-url=\"/diaries/cover-designs/5/elements/sticker\"");
        assertThat(body).contains("/js/diary-canvas-drag.js");
        assertThat(body).contains("/js/diary-sticker-picker.js");
        assertThat(body).contains("/js/diary-tape-repeat.js");
        // 표현 스타일(전체/기본/리얼)을 고르는 줄은 표지 picker 에도 없다
        assertThat(body).doesNotContain("data-sticker-collection=\"ALL\"");
        assertThat(body).doesNotContain("aria-label=\"스티커 스타일\"");
        // 표지 picker 도 같은 목록을 쓰므로 분류 구성이 페이지 다꾸와 어긋나지 않는다
        assertThat(body).containsPattern(
                "data-sticker-category=\"travel\"[\\s\\S]*data-sticker-category=\"landmark\""
                        + "[\\s\\S]*data-sticker-category=\"emotion\"");
        assertThat(body).containsPattern(
                "id=\"diary-sticker-grid-landmark\"(?:(?!diary-sticker-grid-)[\\s\\S])*"
                        + "data-sticker-id=\"eiffel-tower\"[^>]*data-sticker-collection=\"realistic\"");
        // 마스킹테이프의 갈래 필터는 그대로 남는다
        assertThat(body).contains("aria-label=\"마스킹테이프 종류\"");
        assertThat(body).contains("data-tape-type=\"CLEAR\"");
        // 이번 단계에는 사진/라벨/메모지 도구가 없다
        assertThat(body).doesNotContain("diary-photo-input");
        assertThat(body).doesNotContain("diary-decor-tab");
        assertThat(body).doesNotContain("diary-note-option");
    }

    /** 붙여 둔 스티커는 겹침 순서 그대로, 엔진이 읽는 주소를 달고 그려진다. */
    @Test
    void savedStickersComeBackWithTheAddressesTheEngineReads() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design(5L, "제주 여행"));
        when(diaryCoverDesignElementService.getElements(5L, 7L))
                .thenReturn(List.of(sticker(100L, "/images/diary/stickers/travel/plane.svg")));

        String body = mockMvc.perform(get("/diaries/cover-designs/5/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-canvas-item");
        assertThat(body).contains("data-element-id=\"100\"");
        for (String url : new String[]{"position", "size", "rotation", "layer"}) {
            assertThat(body).as("%s", url)
                    .contains("/diaries/cover-designs/5/elements/100/" + url + "\"");
        }
        assertThat(body).contains("/diaries/cover-designs/5/elements/100/sticker/delete");
        assertThat(body).contains("/images/diary/stickers/travel/plane.svg");
    }

    @Test
    void attachingAStickerOnlySendsItsId() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryCoverDesignElement created = sticker(100L, "/images/diary/stickers/travel/plane.svg");
        when(diaryCoverDesignElementService.createSticker(eq(5L), eq(7L), any()))
                .thenReturn(created);

        String stickerId = firstStickerId();
        mockMvc.perform(post("/diaries/cover-designs/5/elements/sticker")
                        .param("sticker", stickerId)
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());

        verify(diaryCoverDesignElementService).createSticker(5L, 7L, stickerId);
    }

    @Test
    void anUnknownStickerIsRefusedBeforeItReachesTheService() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/cover-designs/5/elements/sticker")
                        .param("sticker", "no-such-sticker")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest());

        verify(diaryCoverDesignElementService, never()).createSticker(any(), any(), any());
    }

    /** 이동/크기/회전/겹침/떼기 모두 로그인한 사용자 기준으로 서비스에 넘어간다. */
    @Test
    void everyElementActionCarriesTheLoggedInUser() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignElementService.changeLayer(5L, 100L, 7L, true))
                .thenReturn(List.of(sticker(100L, "/images/diary/stickers/travel/plane.svg")));
        when(diaryCoverDesignElementService.delete(5L, 100L, 7L))
                .thenReturn(sticker(100L, "/images/diary/stickers/travel/plane.svg"));

        perform("/diaries/cover-designs/5/elements/100/position",
                "positionX", "0.20000", "positionY", "0.30000");
        perform("/diaries/cover-designs/5/elements/100/size",
                "width", "0.25000", "height", "0.25000");
        perform("/diaries/cover-designs/5/elements/100/rotation", "rotation", "12.00");
        perform("/diaries/cover-designs/5/elements/100/layer", "direction", "FORWARD");
        perform("/diaries/cover-designs/5/elements/100/sticker/delete");

        verify(diaryCoverDesignElementService).move(5L, 100L, 7L,
                new java.math.BigDecimal("0.20000"), new java.math.BigDecimal("0.30000"));
        verify(diaryCoverDesignElementService).resize(5L, 100L, 7L,
                new java.math.BigDecimal("0.25000"), new java.math.BigDecimal("0.25000"));
        verify(diaryCoverDesignElementService).rotate(5L, 100L, 7L,
                new java.math.BigDecimal("12.00"));
        verify(diaryCoverDesignElementService).changeLayer(5L, 100L, 7L, true);
        verify(diaryCoverDesignElementService).delete(5L, 100L, 7L);
    }

    /**
     * 한 번에 여러 장을 고를 수 있고, 사진 한 장이 요소 한 행이다.
     * 그리고 어느 자리에서 올렸는지가 그 장들의 모습을 정한다.
     */
    @Test
    void severalPhotosBecomeSeveralElementsWithTheLookOfTheirEntryPoint() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPrivatePhotoStorage.save(any(), eq("diary-cover-designs")))
                .thenReturn("/uploads/diary-cover-designs/a.jpg",
                        "/uploads/diary-cover-designs/b.jpg");
        when(diaryCoverDesignElementService.createPhoto(eq(5L), eq(7L), any(), anyInt(), any(), anyDouble()))
                .thenReturn(photo(101L, "/uploads/diary-cover-designs/a.jpg"),
                        photo(102L, "/uploads/diary-cover-designs/b.jpg"));

        mockMvc.perform(multipart("/diaries/cover-designs/5/elements/photo")
                        .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                        .file(new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[]{2}))
                        .param("photoStyle", "POLAROID")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());

        // 표지 디자인 전용 폴더에 둔다. (페이지 사진과 섞지 않는다)
        verify(diaryPrivatePhotoStorage, org.mockito.Mockito.times(2))
                .save(any(), eq("diary-cover-designs"));
        // 두 번째 장은 첫 장과 겹치지 않게 한 칸 밀려 놓이고, 두 장 모두 고른 자리의 모습이다
        verify(diaryCoverDesignElementService)
                .createPhoto(eq(5L), eq(7L), eq("/uploads/diary-cover-designs/a.jpg"),
                        eq(0), eq("POLAROID"), anyDouble());
        verify(diaryCoverDesignElementService)
                .createPhoto(eq(5L), eq(7L), eq("/uploads/diary-cover-designs/b.jpg"),
                        eq(1), eq("POLAROID"), anyDouble());
    }

    /** 일반 사진 자리에서 올리면 프레임 없는 사진으로 붙는다. */
    @Test
    void theOtherEntryPointStoresTheOtherLook() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPrivatePhotoStorage.save(any(), eq("diary-cover-designs")))
                .thenReturn("/uploads/diary-cover-designs/a.jpg");
        when(diaryCoverDesignElementService.createPhoto(eq(5L), eq(7L), any(), anyInt(), any(), anyDouble()))
                .thenReturn(photo(101L, "/uploads/diary-cover-designs/a.jpg"));

        mockMvc.perform(multipart("/diaries/cover-designs/5/elements/photo")
                        .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                        .param("photoStyle", "FULL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());

        verify(diaryCoverDesignElementService)
                .createPhoto(eq(5L), eq(7L), eq("/uploads/diary-cover-designs/a.jpg"),
                        eq(0), eq("FULL"), anyDouble());
    }

    /** DB 저장이 실패하면 방금 올린 파일을 남기지 않는다. */
    @Test
    void aPhotoThatCannotBeSavedLeavesNoFileBehind() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPrivatePhotoStorage.save(any(), eq("diary-cover-designs")))
                .thenReturn("/uploads/diary-cover-designs/a.jpg");
        when(diaryCoverDesignElementService.createPhoto(eq(5L), eq(7L), any(), anyInt(), any(), anyDouble()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "표지 디자인을 찾을 수 없습니다."));

        mockMvc.perform(multipart("/diaries/cover-designs/5/elements/photo")
                        .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                        .param("photoStyle", "FULL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());

        // 방금 저장한 사진만 정리한다. (실제 파일 위치는 private 저장소가 판단한다)
        verify(diaryPrivatePhotoStorage).delete("/uploads/diary-cover-designs/a.jpg");
    }

    /** 사진을 지우면 올린 파일도 함께 정리된다. */
    @Test
    void deletingAPhotoAlsoRemovesTheUploadedFile() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignElementService.delete(5L, 101L, 7L))
                .thenReturn(photo(101L, "/uploads/diary-cover-designs/a.jpg"));

        mockMvc.perform(post("/diaries/cover-designs/5/elements/101/photo/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryPrivatePhotoStorage).delete("/uploads/diary-cover-designs/a.jpg");
    }

    /**
     * 스티커를 뗄 때는 어떤 파일도 지우지 않는다.
     * 공용 asset 이라 지우면 다른 디자인과 페이지 다꾸까지 함께 깨진다.
     */
    @Test
    void removingAStickerNeverTouchesAnyFile() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        // 공용 asset 경로를 들고 있는 요소를 뗀다
        when(diaryCoverDesignElementService.delete(5L, 100L, 7L))
                .thenReturn(sticker(100L, "/images/diary/stickers/travel/plane.svg"));

        mockMvc.perform(post("/diaries/cover-designs/5/elements/100/sticker/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        // 사진이 아닌 요소는 저장소를 부르지도 않는다.
        verify(diaryPrivatePhotoStorage, org.mockito.Mockito.never()).delete(any());
    }

    /**
     * 사진마다 모양을 따로 고른다.
     * 값이 비어 있는 예전 사진은 폴라로이드로 보이고, 새로 붙인 사진은 일반으로 시작한다.
     */
    @Test
    void eachPhotoCarriesItsOwnLook() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design(5L, "제주 여행"));
        DiaryCoverDesignElement legacy = photo(101L, "/uploads/diary-cover-designs/a.jpg");
        legacy.setPhotoStyle(null);                  // 칸이 생기기 전에 붙인 사진
        DiaryCoverDesignElement fresh = photo(102L, "/uploads/diary-cover-designs/b.jpg");
        fresh.setPhotoStyle("FULL");
        when(diaryCoverDesignElementService.getElements(5L, 7L)).thenReturn(List.of(legacy, fresh));

        String body = mockMvc.perform(get("/diaries/cover-designs/5/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 값이 없던 사진은 예전 모습(폴라로이드) 그대로다
        assertThat(body).contains("is-photo-polaroid");
        assertThat(body).contains("is-photo-full");
    }

    /**
     * 모습은 등록하는 자리로 정해진다.
     * 일반 사진과 폴라로이드가 각자 파일 고르개를 갖고, 붙인 뒤에는 다시 고르지 않는다.
     */
    @Test
    void theLookIsChosenWhereThePhotoIsAddedNotAfterwards() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design(5L, "제주 여행"));
        when(diaryCoverDesignElementService.getElements(5L, 7L))
                .thenReturn(List.of(photo(101L, "/uploads/diary-cover-designs/a.jpg")));

        String body = mockMvc.perform(get("/diaries/cover-designs/5/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 등록 자리가 둘이고, 각자 어떤 모습으로 붙일지 들고 있다
        assertThat(body).contains("data-photo-style=\"FULL\"")
                .contains("data-photo-style=\"POLAROID\"");
        assertThat(body).contains("일반").contains("폴라로이드");
        // 붙인 뒤 모습을 다시 고르는 자리는 없다
        assertThat(body).doesNotContain("diary-photo-style-action");
        assertThat(body).doesNotContain("/photo-style");
        // 사진 조작(겹침 순서/삭제)은 그대로다
        assertThat(body).contains("data-layer-direction=\"FORWARD\"");
        assertThat(body).contains("/elements/101/photo/delete");
    }

    @Test
    void anEmptyDownloadedPhotoShowsTheExistingPlaceholderInTheEditor() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design(5L, "받은 표지"));
        when(diaryCoverDesignElementService.getElements(5L, 7L))
                .thenReturn(List.of(photo(101L, null)));

        String body = mockMvc.perform(get("/diaries/cover-designs/5/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-cover-photo-placeholder")
                .contains("사진 넣기")
                .doesNotContain("<img src=\"\" alt=\"표지 사진\"");
    }

    /** 스티커 액션 줄에는 사진 관련 칸이 들어가지 않는다. */
    @Test
    void stickersKeepTheirOwnActions() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design(5L, "제주 여행"));
        when(diaryCoverDesignElementService.getElements(5L, 7L))
                .thenReturn(List.of(sticker(100L, "/images/diary/stickers/travel/plane.svg")));

        String body = mockMvc.perform(get("/diaries/cover-designs/5/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-canvas-sticker");
        assertThat(body).contains("/elements/100/sticker/delete");
        // 사진의 모습은 스티커와 상관이 없다
        assertThat(body).doesNotContain("is-photo-");
        assertThat(body).doesNotContain("diary-photo-style-action");
    }

    /** 저장 계층은 그대로 둔다. (화면에서 쓰지 않을 뿐 언제든 다시 열 수 있다) */
    @Test
    void theStyleChangeApiStillWorksEvenThoughTheScreenNoLongerUsesIt() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryCoverDesignElement changed = photo(101L, "/uploads/diary-cover-designs/a.jpg");
        changed.setPhotoStyle("POLAROID");
        when(diaryCoverDesignElementService.changePhotoStyle(5L, 101L, 7L, "POLAROID"))
                .thenReturn(changed);

        String body = mockMvc.perform(post("/diaries/cover-designs/5/elements/101/photo-style")
                        .param("photoStyle", "POLAROID")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 화면이 그대로 붙일 수 있게 바뀐 값과 class 를 함께 돌려준다
        assertThat(body).contains("POLAROID").contains("is-photo-polaroid");
        verify(diaryCoverDesignElementService).changePhotoStyle(5L, 101L, 7L, "POLAROID");
    }

    /** 조회 서비스를 거친 사진 요소. 화면이 쓸 통제된 주소까지 채워 준다. */
    private DiaryCoverDesignElement photo(Long id, String imageUrl) {
        DiaryCoverDesignElement element = sticker(id, imageUrl);
        element.setElementType("PHOTO");
        element.setPhotoStyle("FULL");
        element.setViewUrl(photoViewUrl(id));
        return element;
    }

    /** 내 표지 디자인 사진의 통제된 주소. (디자인 소유자만 열 수 있다) */
    private String photoViewUrl(Long elementId) {
        return "/diaries/cover-designs/5/elements/" + elementId + "/photo";
    }

    private void perform(String url, String... params) throws Exception {
        var request = post(url)
                .with(csrf())
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        userDetails, null, List.of())));
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        mockMvc.perform(request).andExpect(status().is2xxSuccessful());
    }

    /** manifest 의 첫 스티커 id. (목록은 서버가 들고 있으므로 테스트가 값을 적지 않는다) */
    private String firstStickerId() {
        return diaryStickerCatalog.getCategories().get(0).stickers().get(0).id();
    }

    /**
     * 라벨기는 스티커·사진과 같은 줄의 도구 하나다.
     * 고르는 칸은 페이지 다꾸와 같은 조각을 쓰고, 글꼴 목록도 같은 manifest 에서 온다.
     */
    @Test
    void theEditorOffersTheLabelMakerWithTheSameFontsAsThePages() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design(5L, "제주 여행"));
        when(diaryCoverDesignElementService.getElements(5L, 7L)).thenReturn(List.of());

        String body = mockMvc.perform(get("/diaries/cover-designs/5/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("id=\"diary-label-button\"").contains("라벨기");
        // 붙일 자리는 고르는 칸이 들고 있다 (페이지 다꾸와 같은 조각)
        assertThat(body)
                .contains("data-label-maker")
                .contains("data-create-url=\"/diaries/cover-designs/5/elements/label\"");
        // 글꼴은 세 가지 모두 실제 그 글꼴로 미리 보인다
        assertThat(body)
                .contains("data-label-font=\"nanum-square\"")
                .contains("data-label-font=\"bookk-myeongjo\"")
                .contains("data-label-font=\"park-dahyun\"")
                .contains("diary-font-park-dahyun");
        // 글꼴 정의는 페이지 다꾸와 같은 파일에서 온다 (옮겨 적지 않는다)
        assertThat(body).contains("/css/diary-fonts.css");
        assertThat(body).contains("/js/diary-label-picker.js");
        // 고르는 판은 스티커와 같은 자리 규칙(도구 줄 위)을 쓴다
        assertThat(body).contains("class=\"diary-sticker-popover diary-label-popover\"");
    }

    /** 붙여 둔 글씨는 배경 없이 글자만, 조작 주소와 함께 그려진다. */
    @Test
    void savedLabelsComeBackWithTheirFontAndTheAddressesTheEngineReads() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(design(5L, "제주 여행"));
        when(diaryCoverDesignElementService.getElements(5L, 7L))
                .thenReturn(List.of(label(100L, "JEJU 2026", "park-dahyun")));

        String body = mockMvc.perform(get("/diaries/cover-designs/5/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String item = between(body, "class=\"diary-canvas-item diary-label\"", "</figure>");
        assertThat(item).contains("JEJU 2026").contains("diary-font-park-dahyun");
        // 글자 크기를 상자에 맞추는 데 쓰는 값도 함께 실린다
        assertThat(item).contains("--diary-label-chars:9");
        for (String url : new String[]{"position", "size", "rotation", "layer"}) {
            assertThat(item).as("%s", url)
                    .contains("/diaries/cover-designs/5/elements/100/" + url + "\"");
        }
        assertThat(item).contains("/diaries/cover-designs/5/elements/100/label/delete");
        // 편집 요소라 손잡이와 액션 줄이 함께 그려진다
        assertThat(item)
                .contains("diary-resize-handle")
                .contains("diary-rotate-handle")
                .contains("diary-layer-actions");
        // 종이 배경은 쓰지 않는다
        assertThat(item).doesNotContain("diary-note-surface");
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    /**
     * 라벨기로 붙인 글씨. 화면이 곧바로 그릴 수 있도록 글·글꼴·자리와 저장 주소를 함께 준다.
     * 글꼴 class 도 서버가 줘서 화면이 code 를 class 로 바꾸는 규칙을 갖지 않는다.
     */
    @Test
    void attachingALabelComesBackWithItsTextFontAndAddresses() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignElementService
                .createLabel(5L, 7L, "JEJU 2026", "park-dahyun", "#C86B7C"))
                .thenReturn(label(100L, "JEJU 2026", "park-dahyun"));

        String body = mockMvc.perform(post("/diaries/cover-designs/5/elements/label")
                        .param("text", "JEJU 2026")
                        .param("textFont", "park-dahyun")
                        .param("textColor", "#C86B7C")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"textContent\":\"JEJU 2026\"");
        assertThat(body).contains("\"textFont\":\"park-dahyun\"");
        assertThat(body).contains("\"fontClass\":\"diary-font-park-dahyun\"");
        assertThat(body).contains("/diaries/cover-designs/5/elements/100/label/delete");
        assertThat(body).contains("/diaries/cover-designs/5/elements/100/position");
    }

    /** 문구·글꼴 검증은 서비스 한 곳에서 한다. 그 이유가 그대로 화면에 전해진다. */
    @Test
    void aRejectedLabelTellsWhy() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignElementService.createLabel(any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "지원하지 않는 글꼴입니다."));

        mockMvc.perform(post("/diaries/cover-designs/5/elements/label")
                        .param("text", "JEJU")
                        .param("textFont", "comic-sans")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers
                                .containsString("지원하지 않는 글꼴입니다.")));
    }

    /** 글씨는 파일을 갖지 않는다. DB 행만 지우고 파일 정리는 부르지 않는다. */
    @Test
    void removingALabelNeverTouchesAnyFile() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignElementService.deleteLabel(5L, 100L, 7L))
                .thenReturn(label(100L, "JEJU 2026", "park-dahyun"));

        mockMvc.perform(post("/diaries/cover-designs/5/elements/100/label/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryCoverDesignElementService).deleteLabel(5L, 100L, 7L);
    }

    private DiaryCoverDesignElement label(Long id, String text, String textFont) {
        DiaryCoverDesignElement element = new DiaryCoverDesignElement();
        element.setId(id);
        element.setDesignId(5L);
        element.setElementType("TEXT");
        element.setTextContent(text);
        element.setTextFont(textFont);
        element.setPositionX(new java.math.BigDecimal("0.38000"));
        element.setPositionY(new java.math.BigDecimal("0.38000"));
        element.setWidth(new java.math.BigDecimal("0.44000"));
        element.setHeight(new java.math.BigDecimal("0.09000"));
        element.setRotation(new java.math.BigDecimal("0.00"));
        element.setZIndex(0);
        return element;
    }

    private DiaryCoverDesignElement sticker(Long id, String imageUrl) {
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

    @Test
    void savingTheBasicsUsesTheLoggedInUser() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/cover-designs/5/update")
                        .param("name", "빈티지")
                        .param("baseCoverStyle", "HARDCOVER_NAVY")
                        .param("backgroundColor", "#c9b79a")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-designs/5/edit"));

        verify(diaryCoverDesignService)
                .updateBasics(5L, 7L, "빈티지", "HARDCOVER_NAVY", "#c9b79a");
    }

    @Test
    void deletingGoesThroughTheServiceWithTheLoggedInUser() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.delete(5L, 7L)).thenReturn(List.of());

        mockMvc.perform(post("/diaries/cover-designs/5/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?coverDesigns=open"));

        verify(diaryCoverDesignService).delete(5L, 7L);
    }

    /** 폼 전송에는 CSRF 토큰이 필요하다. */
    @Test
    void formPostsNeedACsrfToken() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        for (String url : new String[]{
                "/diaries/cover-designs",
                "/diaries/cover-designs/5/update",
                "/diaries/cover-designs/5/delete",
                "/diaries/cover-designs/5/library-share"}) {
            mockMvc.perform(post(url)
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, List.of()))))
                    .andExpect(status().isForbidden());
        }
        verify(diaryCoverDesignService, never()).create(any(), any());
        verify(diaryCoverDesignService, never()).delete(any(), any());
    }

    private DiaryCoverDesign design(Long id, String name) {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setId(id);
        design.setUserId(7L);
        design.setName(name);
        design.setBaseCoverStyle("LEATHER_BLACK");
        return design;
    }
}
