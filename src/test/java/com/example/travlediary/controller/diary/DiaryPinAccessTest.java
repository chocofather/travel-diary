package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.DiaryPinLockedAdvice;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.Diary;
import com.example.travlediary.repository.diary.DiaryMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryCoverDesignElementService;
import com.example.travlediary.service.diary.DiaryCoverDesignService;
import com.example.travlediary.service.diary.DiaryCoverService;
import com.example.travlediary.service.diary.DiaryElementService;
import com.example.travlediary.service.diary.DiaryLabelFontCatalog;
import com.example.travlediary.service.diary.DiaryNoteCatalog;
import com.example.travlediary.service.diary.DiaryPageService;
import com.example.travlediary.service.diary.DiaryPinGuard;
import com.example.travlediary.service.diary.DiaryPinService;
import com.example.travlediary.service.diary.DiaryPinServiceImpl;
import com.example.travlediary.service.diary.DiaryPinSession;
import com.example.travlediary.service.diary.DiaryServiceImpl;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.holiday.HolidayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIN 을 건 뒤 실제로 화면이 막히는지.
 *
 * <p>여기서는 다이어리 조회 서비스와 PIN 서비스를 <b>진짜</b> 로 띄운다.
 * 다른 화면 테스트는 DiaryService 를 가짜로 두기 때문에 잠금 검사가 아예 실행되지 않는다.
 * 그래서 "PIN 을 걸었는데 그냥 들어가진다" 같은 문제는 그 테스트들이 잡을 수 없었다.
 * 이 클래스가 한 세션으로 걸기 → 접근 → 풀기 → 접근까지 실제 경로로 이어 본다.
 */
@WebMvcTest(controllers = {DiaryController.class, DiaryPinController.class})
@Import({SecurityConfig.class, DiaryStickerCatalog.class, DiaryNoteCatalog.class,
        DiaryLabelFontCatalog.class, DiaryPinSession.class, DiaryPinGuard.class,
        DiaryServiceImpl.class, DiaryPinServiceImpl.class, DiaryPinLockedAdvice.class})
class DiaryPinAccessTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DiaryPinSession diaryPinSession;
    @Autowired
    private DiaryPinService diaryPinService;

    /** 다이어리는 진짜 서비스가 읽는다. 저장소만 가짜다. */
    @MockitoBean
    private DiaryMapper diaryMapper;
    @MockitoBean
    private DiaryPageService diaryPageService;
    @MockitoBean
    private DiaryElementService diaryElementService;
    @MockitoBean
    private DiaryCoverService diaryCoverService;
    @MockitoBean
    private DiaryCoverDesignService diaryCoverDesignService;
    @MockitoBean
    private DiaryCoverDesignElementService diaryCoverDesignElementService;
    @MockitoBean
    private FileUploadService fileUploadService;
    @MockitoBean
    private HolidayService holidayService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomUserDetails userDetails;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        when(userDetails.getId()).thenReturn(7L);
        when(diaryMapper.findByIdAndUserId(10L, 7L)).thenAnswer(invocation -> diary());
        when(diaryMapper.updatePinHash(anyLong(), anyLong(), any())).thenAnswer(invocation -> {
            pinHash = invocation.getArgument(2);
            return 1;
        });
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of());
    }

    /** 지금 저장돼 있는 해시. (updatePinHash 가 부르는 대로 바뀐다) */
    private String pinHash;

    /**
     * 시나리오 C — PIN 을 건 직후 같은 세션으로 상세를 열면 막혀야 한다.
     * "걸었다"는 사실이 "열려 있다"는 뜻이 아니다.
     */
    @Test
    void afterSettingAPinTheSameSessionCannotOpenTheDiary() throws Exception {
        setPin("1234");

        // 건 순간 세션은 잠긴 상태다
        assertThat(diaryPinSession.isUnlocked(session, 10L)).isFalse();

        // 상세를 열면 내용이 아니라 책장의 PIN 입력으로 안내된다
        mockMvc.perform(get("/diaries/10").session(session)
                        .header("Accept", "text/html")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?locked=10"));
    }

    /** 시나리오 D — 수정 화면도 같다. 주소를 직접 쳐도 열리지 않는다. */
    @Test
    void afterSettingAPinTheEditScreenIsProtectedToo() throws Exception {
        setPin("1234");

        mockMvc.perform(get("/diaries/10/edit").session(session)
                        .header("Accept", "text/html")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries?locked=10"));

        // 풀고 나서 돌아갈 자리는 세션에 담긴다
        assertThat(session.getAttribute(DiaryPinLockedAdvice.PENDING_TARGET))
                .isEqualTo("/diaries/10/edit");
    }

    /** 시나리오 E — 실제로 PIN 을 넣어 맞힌 뒤에만 열린다. */
    @Test
    void onlyAfterUnlockingWithTheRightPinDoesTheDiaryOpen() throws Exception {
        setPin("1234");

        // 틀린 PIN 으로는 열리지 않는다
        mockMvc.perform(post("/diaries/10/pin/unlock").param("pin", "9999")
                        .session(session).with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest());
        assertThat(diaryPinSession.isUnlocked(session, 10L)).isFalse();

        // 맞는 PIN 을 넣으면 이 세션에서 열린다
        mockMvc.perform(post("/diaries/10/pin/unlock").param("pin", "1234")
                        .session(session).with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());
        assertThat(diaryPinSession.isUnlocked(session, 10L)).isTrue();

        mockMvc.perform(get("/diaries/10").session(session)
                        .header("Accept", "text/html")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());
    }

    /** POST 는 화면 이동이 아니다. 잠긴 동안에는 예전 그대로 막힌다. */
    @Test
    void aLockedDiaryStillRefusesDirectPostRequests() throws Exception {
        setPin("1234");

        mockMvc.perform(post("/diaries/10/delete").session(session).with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isForbidden());
    }

    /** 다른 세션은 따로다. 한쪽에서 풀어도 다른 쪽은 다시 물어본다. */
    @Test
    void unlockingInOneSessionDoesNotOpenAnother() throws Exception {
        setPin("1234");
        diaryPinService.verifyAndUnlock(10L, 7L, "1234", session);

        MockHttpSession other = new MockHttpSession();
        assertThat(diaryPinService.isUnlocked(10L, 7L, other)).isFalse();
    }

    private void setPin(String pin) throws Exception {
        mockMvc.perform(post("/diaries/10/pin").param("newPin", pin)
                        .session(session).with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());
    }

    private Diary diary() {
        Diary diary = new Diary();
        diary.setId(10L);
        diary.setUserId(7L);
        diary.setTitle("여름 제주 여행");
        diary.setStartDate(LocalDate.of(2026, 8, 1));
        diary.setEndDate(LocalDate.of(2026, 8, 5));
        diary.setCoverStyle("DEFAULT");
        diary.setNotebookType("CLASSIC");
        diary.setPinHash(pinHash);
        return diary;
    }
}
