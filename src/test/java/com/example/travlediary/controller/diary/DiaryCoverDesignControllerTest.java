package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverDesignElementService;
import com.example.travlediary.service.diary.DiaryCoverDesignService;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DiaryCoverDesignController.class)
@Import({SecurityConfig.class, DiaryStickerCatalog.class})
class DiaryCoverDesignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryCoverDesignService diaryCoverDesignService;
    @MockitoBean
    private DiaryCoverDesignElementService diaryCoverDesignElementService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private FileUploadService fileUploadService;
    @MockitoBean
    private CustomUserDetails userDetails;

    /** 붙일 수 있는 스티커 목록. 실제 manifest 를 그대로 읽는다. */
    @Autowired
    private DiaryStickerCatalog diaryStickerCatalog;
    /** 파일 정리는 업로드 폴더 안에서만 일어나야 하므로, 시험용 폴더를 심어 확인한다. */
    @Autowired
    private DiaryCoverDesignController controller;

    @Test
    void guestIsSentToLogin() throws Exception {
        mockMvc.perform(get("/diaries/cover-designs"))
                .andExpect(status().is3xxRedirection());
    }

    /** 보관함은 언제나 로그인한 사람의 것만 읽는다. (요청에 실린 소유자 값은 쓰지 않는다) */
    @Test
    void theShelfOnlyShowsMyOwnDesigns() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesigns(7L)).thenReturn(List.of(design(5L, "제주 여행")));

        String body = mockMvc.perform(get("/diaries/cover-designs")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/cover-designs"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("제주 여행");
        // 표지 미리보기는 목록 카드와 같은 재질 클래스를 쓴다
        assertThat(body).contains("diary-cover-canvas").contains("diary-cover-leather-black");
        // 다음 단계에서 이 안쪽이 자유배치 캔버스가 된다
        assertThat(body).contains("diary-cover-surface");
        verify(diaryCoverDesignService).getMyDesigns(7L);
    }

    /**
     * 카드에는 꾸민 표지가 그대로 줄어 보인다.
     * 요소는 카드마다 따로 묻지 않고 디자인 번호를 모아 한 번에 읽는다.
     */
    @Test
    void theShelfShowsTheFinishedCoversAndAsksForTheElementsOnlyOnce() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesigns(7L))
                .thenReturn(List.of(design(5L, "제주 여행"), design(6L, "빈티지")));
        when(diaryCoverDesignElementService.getElementsByDesign(List.of(5L, 6L), 7L))
                .thenReturn(Map.of(
                        5L, List.of(photo(101L, "/uploads/diary-cover-designs/a.jpg"),
                                sticker(100L, "/images/diary/stickers/travel/plane.svg")),
                        6L, List.of()));

        String body = mockMvc.perform(get("/diaries/cover-designs")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 사진과 스티커가 실제로 그려진다
        assertThat(body).contains("/uploads/diary-cover-designs/a.jpg");
        assertThat(body).contains("/images/diary/stickers/travel/plane.svg");
        assertThat(body).contains("is-photo-full");
        // 보기 전용이다. 조작 손잡이도 저장 주소도 없다
        assertThat(body).doesNotContain("diary-resize-handle")
                .doesNotContain("diary-rotate-handle")
                .doesNotContain("diary-layer-action")
                .doesNotContain("data-position-url")
                .doesNotContain("is-editable");
        // 마스킹테이프는 편집 화면과 같은 렌더러가 그린다
        assertThat(body).contains("/js/diary-tape-repeat.js");
        assertThat(body).doesNotContain("/js/diary-canvas-drag.js");

        // 한 번만 묻는다 (카드 수만큼 부르지 않는다)
        verify(diaryCoverDesignElementService).getElementsByDesign(List.of(5L, 6L), 7L);
        verify(diaryCoverDesignElementService, never()).getElements(any(), any());
    }

    @Test
    void theEmptyShelfInvitesMakingOne() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesigns(7L)).thenReturn(List.of());

        String body = mockMvc.perform(get("/diaries/cover-designs")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("아직 저장한 표지 디자인이 없습니다.");
        // 만들기는 폼 전송 하나다. 중간에 이름을 묻는 화면이 없다
        assertThat(body).contains("action=\"/diaries/cover-designs\"");
        assertThat(body).doesNotContain("/diaries/cover-designs/new");
    }

    /**
     * 만들기 전에 이름이나 바탕을 따로 묻지 않는다.
     * 요소를 붙이려면 디자인 번호가 먼저 있어야 해서 기본값으로 한 줄 만들고 바로 넘어간다.
     */
    @Test
    void makingADesignAsksNothingUpFront() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.create(eq(7L), any())).thenReturn(design(5L, "새 표지 디자인"));

        mockMvc.perform(post("/diaries/cover-designs")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/cover-designs/5/edit"));

        ArgumentCaptor<DiaryCoverDesign> captor = ArgumentCaptor.forClass(DiaryCoverDesign.class);
        verify(diaryCoverDesignService).create(eq(7L), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("새 표지 디자인");
        assertThat(captor.getValue().getBaseCoverStyle()).isEqualTo("DEFAULT");
        // 색은 고르지 않은 채로 시작한다 (재질의 원래 색)
        assertThat(captor.getValue().getBackgroundColor()).isNull();
    }

    /** 중간 입력 화면은 더 이상 없다. */
    @Test
    void thereIsNoSeparateCreationForm() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(get("/diaries/cover-designs/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());
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
        when(fileUploadService.saveFile(any(), eq("diary-cover-designs")))
                .thenReturn("/uploads/diary-cover-designs/a.jpg",
                        "/uploads/diary-cover-designs/b.jpg");
        when(diaryCoverDesignElementService.createPhoto(eq(5L), eq(7L), any(), anyInt(), any()))
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
        verify(fileUploadService, org.mockito.Mockito.times(2))
                .saveFile(any(), eq("diary-cover-designs"));
        // 두 번째 장은 첫 장과 겹치지 않게 한 칸 밀려 놓이고, 두 장 모두 고른 자리의 모습이다
        verify(diaryCoverDesignElementService)
                .createPhoto(5L, 7L, "/uploads/diary-cover-designs/a.jpg", 0, "POLAROID");
        verify(diaryCoverDesignElementService)
                .createPhoto(5L, 7L, "/uploads/diary-cover-designs/b.jpg", 1, "POLAROID");
    }

    /** 일반 사진 자리에서 올리면 프레임 없는 사진으로 붙는다. */
    @Test
    void theOtherEntryPointStoresTheOtherLook() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(fileUploadService.saveFile(any(), eq("diary-cover-designs")))
                .thenReturn("/uploads/diary-cover-designs/a.jpg");
        when(diaryCoverDesignElementService.createPhoto(eq(5L), eq(7L), any(), anyInt(), any()))
                .thenReturn(photo(101L, "/uploads/diary-cover-designs/a.jpg"));

        mockMvc.perform(multipart("/diaries/cover-designs/5/elements/photo")
                        .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                        .param("photoStyle", "FULL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());

        verify(diaryCoverDesignElementService)
                .createPhoto(5L, 7L, "/uploads/diary-cover-designs/a.jpg", 0, "FULL");
    }

    /** DB 저장이 실패하면 방금 올린 파일을 남기지 않는다. */
    @Test
    void aPhotoThatCannotBeSavedLeavesNoFileBehind(@TempDir Path uploadRoot) throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Path saved = Files.createDirectories(uploadRoot.resolve("diary-cover-designs"))
                .resolve("a.jpg");
        Files.writeString(saved, "x");
        when(fileUploadService.saveFile(any(), eq("diary-cover-designs")))
                .thenReturn("/uploads/diary-cover-designs/a.jpg");
        when(diaryCoverDesignElementService.createPhoto(eq(5L), eq(7L), any(), anyInt(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "표지 디자인을 찾을 수 없습니다."));
        ReflectionTestUtils.setField(controller, "uploadPath", uploadRoot.toString());

        mockMvc.perform(multipart("/diaries/cover-designs/5/elements/photo")
                        .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                        .param("photoStyle", "FULL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());

        assertThat(Files.exists(saved)).as("실패한 장의 파일").isFalse();
    }

    /** 사진을 지우면 올린 파일도 함께 정리된다. */
    @Test
    void deletingAPhotoAlsoRemovesTheUploadedFile(@TempDir Path uploadRoot) throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Path saved = Files.createDirectories(uploadRoot.resolve("diary-cover-designs"))
                .resolve("a.jpg");
        Files.writeString(saved, "x");
        when(diaryCoverDesignElementService.delete(5L, 101L, 7L))
                .thenReturn(photo(101L, "/uploads/diary-cover-designs/a.jpg"));
        ReflectionTestUtils.setField(controller, "uploadPath", uploadRoot.toString());

        mockMvc.perform(post("/diaries/cover-designs/5/elements/101/photo/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(saved)).as("지운 사진의 파일").isFalse();
    }

    /**
     * 스티커를 뗄 때는 어떤 파일도 지우지 않는다.
     * 공용 asset 이라 지우면 다른 디자인과 페이지 다꾸까지 함께 깨진다.
     */
    @Test
    void removingAStickerNeverTouchesAnyFile(@TempDir Path uploadRoot) throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Path assetLike = Files.createDirectories(uploadRoot.resolve("images/diary/stickers/travel"))
                .resolve("plane.svg");
        Files.writeString(assetLike, "x");
        // 공용 asset 경로를 들고 있는 요소를 뗀다
        when(diaryCoverDesignElementService.delete(5L, 100L, 7L))
                .thenReturn(sticker(100L, "/images/diary/stickers/travel/plane.svg"));
        ReflectionTestUtils.setField(controller, "uploadPath", uploadRoot.toString());

        mockMvc.perform(post("/diaries/cover-designs/5/elements/100/sticker/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(assetLike)).as("공용 스티커 파일").isTrue();
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

    private DiaryCoverDesignElement photo(Long id, String imageUrl) {
        DiaryCoverDesignElement element = sticker(id, imageUrl);
        element.setElementType("PHOTO");
        element.setPhotoStyle("FULL");
        return element;
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

    private DiaryCoverDesignElement sticker(Long id, String imageUrl) {
        DiaryCoverDesignElement element = new DiaryCoverDesignElement();
        element.setId(id);
        element.setDesignId(5L);
        element.setElementType("STICKER");
        element.setImageUrl(imageUrl);
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
                .andExpect(redirectedUrl("/diaries/cover-designs"));

        verify(diaryCoverDesignService).delete(5L, 7L);
    }

    /** 폼 전송에는 CSRF 토큰이 필요하다. (SecurityConfig 의 목록에 세 주소를 넣어 두었다) */
    @Test
    void formPostsNeedACsrfToken() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        for (String url : new String[]{
                "/diaries/cover-designs",
                "/diaries/cover-designs/5/update",
                "/diaries/cover-designs/5/delete"}) {
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
