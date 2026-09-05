package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.FestivalEditData;
import com.example.travlediary.dto.FestivalEditForm;
import com.example.travlediary.dto.TravelInfoTranslationForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.FestivalAdminService;
import com.example.travlediary.service.travelinfo.FestivalRegistrationService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 축제·행사 수정 화면이 저장된 행사 기간을 날짜 입력에 그대로 실어 보내는지 본다.
 *
 * <p>{@code input type="date"} 는 ISO(yyyy-MM-dd) 값만 읽는다. 다른 모양으로 찍히면
 * 브라우저가 값을 버려서 화면에는 빈 칸으로 보인다.
 */
@WebMvcTest(AdminFestivalController.class)
@Import(SecurityConfig.class)
class AdminFestivalEditPeriodPreloadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private TravelInfoService travelInfoService;
    @MockitoBean private InfoCategoryService infoCategoryService;
    @MockitoBean private FestivalRegistrationService festivalRegistrationService;
    @MockitoBean private FestivalAdminService festivalAdminService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void editScreenRendersTheStoredPeriodAsIsoDateValues() throws Exception {
        givenCategories();
        when(festivalAdminService.getEditData(41L))
                .thenReturn(new FestivalEditData(editForm(), List.of()));

        Document document = render("/admin/festivals/41/edit");

        assertThat(document.select("#festival-start-date").attr("value"))
                .isEqualTo("2026-09-02");
        assertThat(document.select("#festival-end-date").attr("value"))
                .isEqualTo("2026-10-24");
    }

    @Test
    void createScreenLeavesTheDateInputsEmpty() throws Exception {
        givenCategories();

        Document document = render("/admin/festivals/create");

        assertThat(document.select("#festival-start-date").attr("value")).isEmpty();
        assertThat(document.select("#festival-end-date").attr("value")).isEmpty();
    }

    @Test
    void editScreenKeepsTheRestOfTheFormWhilePreloadingDates() throws Exception {
        givenCategories();
        when(festivalAdminService.getEditData(41L))
                .thenReturn(new FestivalEditData(editForm(), List.of()));

        Document document = render("/admin/festivals/41/edit");

        assertThat(document.select("#festival-title").attr("value")).isEqualTo("경복궁 별빛야행");
        assertThat(document.select("#festival-event-place").attr("value")).isEqualTo("경복궁");
        assertThat(document.select("#festival-content").attr("value"))
                .isEqualTo("<p>행사 소개</p>");
        // 번역 슬롯도 같은 화면에서 함께 실린다
        assertThat(document.select("[data-translation-panel]"))
                .extracting(element -> element.attr("data-translation-panel"))
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(document.select("[data-translation-title=en]").attr("value"))
                .isEqualTo("Starlight Night Tour");
    }

    @Test
    void editScreenCarriesTheKoreanKtoContentIdAsReadOnlyMetadata() throws Exception {
        givenCategories();
        when(festivalAdminService.getEditData(41L))
                .thenReturn(new FestivalEditData(editForm(), List.of(), "2648460"));

        Document document = render("/admin/festivals/41/edit");

        assertThat(document.select("[data-festival-foreign-autofill]")
                .attr("data-festival-kto-content-id")).isEqualTo("2648460");
        // 사용자가 고치는 입력값으로 만들지 않는다.
        assertThat(document.select("input[name=externalContentId]")).isEmpty();
        // 따로 누를 버튼은 없다. 상태 문구만 둔다.
        assertThat(document.select("[data-festival-foreign-status]")).hasSize(1);
    }

    @Test
    void manuallyRegisteredFestivalsCarryNoKtoContentId() throws Exception {
        givenCategories();
        when(festivalAdminService.getEditData(41L))
                .thenReturn(new FestivalEditData(editForm(), List.of()));

        Document document = render("/admin/festivals/41/edit");

        assertThat(document.select("[data-festival-foreign-autofill]")
                .attr("data-festival-kto-content-id")).isEmpty();
        // 수기 축제는 국문 자동입력 신호가 없어 외국어 조회가 시작되지 않는다.
        assertThat(document.select("[data-festival-foreign-status]")).hasSize(1);
    }

    @Test
    void createScreenLeavesTheKtoContentIdToTheKoreanAutofillHiddenField() throws Exception {
        givenCategories();

        Document document = render("/admin/festivals/create");

        assertThat(document.select("[data-festival-foreign-autofill]")
                .attr("data-festival-kto-content-id")).isEmpty();
        // 신규 등록은 국문 자동입력이 채우는 hidden 값을 쓴다.
        assertThat(document.select("#kto-festival-content-id")).hasSize(1);
    }

    private Document render(String path) throws Exception {
        String html = mockMvc.perform(get(path).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Jsoup.parse(html);
    }

    private void givenCategories() {
        InfoCategory category = new InfoCategory();
        category.setId(3L);
        category.setName("문화축제");
        category.setContentType(TravelInfoContentType.FESTIVAL);
        category.setIsVisible(true);
        when(infoCategoryService.getAll()).thenReturn(List.of(category));
    }

    private FestivalEditForm editForm() {
        FestivalEditForm form = new FestivalEditForm();
        form.setTitle("경복궁 별빛야행");
        form.setContent("<p>행사 소개</p>");
        form.setScope(TravelInfoScope.DOMESTIC);
        form.setCategoryId(3L);
        form.setStartDate(LocalDate.parse("2026-09-02"));
        form.setEndDate(LocalDate.parse("2026-10-24"));
        form.setEventPlace("경복궁");
        List<TravelInfoTranslationForm> translations =
                TravelInfoTranslationForm.newTranslationSlots();
        translations.stream()
                .filter(slot -> "en".equals(slot.getLanguageCode()))
                .forEach(slot -> slot.setTitle("Starlight Night Tour"));
        form.setTranslations(translations);
        return form;
    }
}
