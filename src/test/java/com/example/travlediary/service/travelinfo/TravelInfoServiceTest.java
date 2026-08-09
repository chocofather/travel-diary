package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.AdminTravelInfoDetailDto;
import com.example.travlediary.dto.InfoPeriodForm;
import com.example.travlediary.dto.TravelInfoForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelInfoServiceTest {

    @Mock
    private TravelInfoMapper travelInfoMapper;
    @Mock
    private InfoCategoryMapper infoCategoryMapper;

    private TravelInfoService travelInfoService;

    @BeforeEach
    void setUp() {
        travelInfoService = new TravelInfoService(
                travelInfoMapper, infoCategoryMapper, new PostContentSanitizer());
    }

    @Test
    void getsFestivalAdminDetailWithCategorySanitizedContentAndMultiplePeriods() {
        TravelInfo existing = existingInfo(10L, TravelInfoContentType.FESTIVAL);
        existing.setTitle("벚꽃 축제");
        existing.setContent("<p onclick=\"alert(1)\">축제 본문</p>"
                + "<img src=\"/uploads/editor/festival.png\"><script>alert(1)</script>");
        existing.setViews(37);
        existing.setCreatedAt(Timestamp.valueOf("2026-04-01 10:00:00"));
        existing.setUpdatedAt(Timestamp.valueOf("2026-04-02 11:30:00"));
        List<InfoPeriod> periods = List.of(
                infoPeriod("2026-04-01", "2026-04-03"),
                infoPeriod("2026-05-10", "2026-05-12"));
        when(travelInfoMapper.findById(10L)).thenReturn(existing);
        when(travelInfoMapper.findPeriodsByInfoId(10L)).thenReturn(periods);
        allowCategory();

        AdminTravelInfoDetailDto detail = travelInfoService.getAdminDetail(10L);

        assertThat(detail.getId()).isEqualTo(10L);
        assertThat(detail.getTitle()).isEqualTo("벚꽃 축제");
        assertThat(detail.getCategoryId()).isEqualTo(3L);
        assertThat(detail.getCategoryName()).isEqualTo("계절여행");
        assertThat(detail.getViews()).isEqualTo(37);
        assertThat(detail.getCreatedAt()).isEqualTo(existing.getCreatedAt());
        assertThat(detail.getUpdatedAt()).isEqualTo(existing.getUpdatedAt());
        assertThat(detail.getPeriods()).containsExactlyElementsOf(periods);
        assertThat(detail.getContent())
                .contains("<p>축제 본문</p>", "src=\"/uploads/editor/festival.png\"")
                .doesNotContain("onclick", "script");
        verify(travelInfoMapper).findById(10L);
        verify(travelInfoMapper).findPeriodsByInfoId(10L);
        verifyNoMoreInteractions(travelInfoMapper);
    }

    @Test
    void getsGeneralAdminDetailWithEmptyPeriodsAndNoPeriodQuery() {
        when(travelInfoMapper.findById(10L))
                .thenReturn(existingInfo(10L, TravelInfoContentType.GENERAL));
        allowCategory();

        AdminTravelInfoDetailDto detail = travelInfoService.getAdminDetail(10L);

        assertThat(detail.getContentType()).isEqualTo(TravelInfoContentType.GENERAL);
        assertThat(detail.getPeriods()).isEmpty();
        verify(travelInfoMapper).findById(10L);
        verify(travelInfoMapper, never()).findPeriodsByInfoId(any());
        verifyNoMoreInteractions(travelInfoMapper);
    }

    @Test
    void missingAdminDetailReturnsNotFoundBeforeCategoryOrPeriodLookup() {
        when(travelInfoMapper.findById(99L)).thenReturn(null);

        assertNotFound(() -> travelInfoService.getAdminDetail(99L));

        verify(travelInfoMapper).findById(99L);
        verifyNoMoreInteractions(travelInfoMapper);
        verifyNoInteractions(infoCategoryMapper);
    }

    @Test
    void createsGeneralInfoWithAuthenticatedAdminAndNoPeriods() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL);
        form.setPeriods(List.of(period("2026-04-01", "2026-04-03")));
        allowCategory();
        stubTravelInfoInsert(100L);

        Long id = travelInfoService.create(form, 7L);

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<TravelInfo> captor = ArgumentCaptor.forClass(TravelInfo.class);
        verify(travelInfoMapper).insertTravelInfo(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("벚꽃 여행");
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getViews()).isZero();
        verify(travelInfoMapper, never()).insertPeriod(any());
    }

    @Test
    void createsFestivalWithMultiplePeriodsInDateOrder() {
        TravelInfoForm form = form(TravelInfoContentType.FESTIVAL);
        form.setPeriods(List.of(
                period("2026-05-10", "2026-05-12"),
                period("2026-04-01", "2026-04-03")));
        allowCategory();
        stubTravelInfoInsert(100L);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);

        travelInfoService.create(form, 7L);

        ArgumentCaptor<InfoPeriod> captor = ArgumentCaptor.forClass(InfoPeriod.class);
        verify(travelInfoMapper, times(2)).insertPeriod(captor.capture());
        assertThat(captor.getAllValues()).extracting(InfoPeriod::getStartDate)
                .containsExactly(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-05-10"));
        assertThat(captor.getAllValues()).allSatisfy(period -> assertThat(period.getInfoId()).isEqualTo(100L));
    }

    @Test
    void festivalRequiresAtLeastOneCompletePeriod() {
        TravelInfoForm empty = form(TravelInfoContentType.FESTIVAL);
        allowCategory();

        assertValidation("축제 여행정보는 기간을 한 개 이상 입력해 주세요.",
                () -> travelInfoService.create(empty, 7L));
        verify(travelInfoMapper, never()).insertTravelInfo(any());
    }

    @Test
    void festivalRejectsHalfFilledPeriod() {
        TravelInfoForm form = form(TravelInfoContentType.FESTIVAL);
        InfoPeriodForm period = new InfoPeriodForm();
        period.setStartDate(LocalDate.parse("2026-04-01"));
        form.setPeriods(List.of(period));
        allowCategory();

        assertValidation("축제 기간의 시작일과 종료일을 모두 입력해 주세요.",
                () -> travelInfoService.create(form, 7L));
    }

    @Test
    void festivalRejectsStartAfterEnd() {
        TravelInfoForm form = form(TravelInfoContentType.FESTIVAL);
        form.setPeriods(List.of(period("2026-04-10", "2026-04-01")));
        allowCategory();

        assertValidation("축제 기간의 시작일은 종료일보다 늦을 수 없습니다.",
                () -> travelInfoService.create(form, 7L));
    }

    @Test
    void festivalRejectsDuplicateAndOverlappingPeriods() {
        TravelInfoForm duplicate = form(TravelInfoContentType.FESTIVAL);
        duplicate.setPeriods(List.of(
                period("2026-04-01", "2026-04-03"),
                period("2026-04-01", "2026-04-03")));
        allowCategory();
        assertValidation("동일한 축제 기간을 중복해서 입력할 수 없습니다.",
                () -> travelInfoService.create(duplicate, 7L));

        TravelInfoForm overlap = form(TravelInfoContentType.FESTIVAL);
        overlap.setPeriods(List.of(
                period("2026-04-01", "2026-04-05"),
                period("2026-04-05", "2026-04-10")));
        assertValidation("서로 겹치는 축제 기간을 입력할 수 없습니다.",
                () -> travelInfoService.create(overlap, 7L));
    }

    @Test
    void festivalToGeneralDeletesPeriodsAndPreservesOwnerAndViews() {
        TravelInfo existing = existingInfo(10L, TravelInfoContentType.FESTIVAL);
        existing.setUserId(42L);
        existing.setViews(93);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(1);
        allowCategory();

        TravelInfoForm form = form(TravelInfoContentType.GENERAL);
        form.setPeriods(List.of(period("2026-04-01", "2026-04-02")));
        travelInfoService.update(10L, form);

        verify(travelInfoMapper).deletePeriodsByInfoId(10L);
        verify(travelInfoMapper, never()).insertPeriod(any());
        assertThat(existing.getContentType()).isEqualTo(TravelInfoContentType.GENERAL);
        assertThat(existing.getUserId()).isEqualTo(42L);
        assertThat(existing.getViews()).isEqualTo(93);
    }

    @Test
    void generalToFestivalRequiresPeriodBeforeUpdating() {
        when(travelInfoMapper.findByIdForUpdate(10L))
                .thenReturn(existingInfo(10L, TravelInfoContentType.GENERAL));
        allowCategory();

        assertValidation("축제 여행정보는 기간을 한 개 이상 입력해 주세요.",
                () -> travelInfoService.update(10L, form(TravelInfoContentType.FESTIVAL)));
        verify(travelInfoMapper, never()).updateTravelInfo(any());
        verify(travelInfoMapper, never()).deletePeriodsByInfoId(any());
    }

    @Test
    void stripsTitleAndSanitizesContentBeforeInsert() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL);
        form.setTitle("  안전 여행  ");
        form.setContent("<p onclick=\"alert(1)\">안전 정보</p><script>alert(1)</script>");
        allowCategory();
        stubTravelInfoInsert(100L);

        travelInfoService.create(form, 7L);

        ArgumentCaptor<TravelInfo> captor = ArgumentCaptor.forClass(TravelInfo.class);
        verify(travelInfoMapper).insertTravelInfo(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("안전 여행");
        assertThat(captor.getValue().getContent())
                .isEqualTo("<p>안전 정보</p>")
                .doesNotContain("script", "onclick");
    }

    @Test
    void rejectsBlankOrOversizedTitleAndEmptySanitizedContent() {
        TravelInfoForm blankTitle = form(TravelInfoContentType.GENERAL);
        blankTitle.setTitle("   ");
        assertValidation("제목을 입력해 주세요.", () -> travelInfoService.create(blankTitle, 7L));

        TravelInfoForm longTitle = form(TravelInfoContentType.GENERAL);
        longTitle.setTitle("가".repeat(256));
        assertValidation("제목은 255자 이하로 입력해 주세요.",
                () -> travelInfoService.create(longTitle, 7L));

        TravelInfoForm emptyContent = form(TravelInfoContentType.GENERAL);
        emptyContent.setContent("<script>alert(1)</script><p><br></p>");
        assertValidation("본문을 입력해 주세요.", () -> travelInfoService.create(emptyContent, 7L));
        verify(travelInfoMapper, never()).insertTravelInfo(any());
    }

    @Test
    void rejectsMissingCategoryBeforeInsert() {
        when(infoCategoryMapper.findById(3L)).thenReturn(null);

        assertValidation("존재하지 않는 정보 카테고리입니다.",
                () -> travelInfoService.create(form(TravelInfoContentType.GENERAL), 7L));
        verify(travelInfoMapper, never()).insertTravelInfo(any());
    }

    @Test
    void missingUpdateAndDeleteReturnNotFound() {
        when(travelInfoMapper.findByIdForUpdate(99L)).thenReturn(null);

        assertNotFound(() -> travelInfoService.update(99L, form(TravelInfoContentType.GENERAL)));
        assertNotFound(() -> travelInfoService.delete(99L));
        verify(travelInfoMapper, never()).updateTravelInfo(any());
        verify(travelInfoMapper, never()).deleteTravelInfo(any());
    }

    @Test
    void deletesExistingTravelInfoAndReliesOnDatabaseCascadeForPeriods() {
        when(travelInfoMapper.findByIdForUpdate(10L))
                .thenReturn(existingInfo(10L, TravelInfoContentType.FESTIVAL));
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);

        travelInfoService.delete(10L);

        verify(travelInfoMapper).deleteTravelInfo(10L);
        verify(travelInfoMapper, never()).deletePeriodsByInfoId(any());
    }

    @Test
    void requiresScopeAndContentType() {
        TravelInfoForm missingScope = form(TravelInfoContentType.GENERAL);
        missingScope.setScope(null);
        assertValidation("국내/해외 범위를 선택해 주세요.",
                () -> travelInfoService.create(missingScope, 7L));

        TravelInfoForm missingType = form(TravelInfoContentType.GENERAL);
        missingType.setContentType(null);
        assertValidation("여행정보 유형을 선택해 주세요.",
                () -> travelInfoService.create(missingType, 7L));
    }

    private TravelInfoForm form(TravelInfoContentType contentType) {
        TravelInfoForm form = new TravelInfoForm();
        form.setTitle("  벚꽃 여행  ");
        form.setContent("<p>봄 여행 정보</p>");
        form.setScope(TravelInfoScope.DOMESTIC);
        form.setContentType(contentType);
        form.setCategoryId(3L);
        return form;
    }

    private InfoPeriodForm period(String startDate, String endDate) {
        InfoPeriodForm form = new InfoPeriodForm();
        form.setStartDate(LocalDate.parse(startDate));
        form.setEndDate(LocalDate.parse(endDate));
        return form;
    }

    private InfoPeriod infoPeriod(String startDate, String endDate) {
        InfoPeriod period = new InfoPeriod();
        period.setInfoId(10L);
        period.setStartDate(LocalDate.parse(startDate));
        period.setEndDate(LocalDate.parse(endDate));
        return period;
    }

    private TravelInfo existingInfo(Long id, TravelInfoContentType contentType) {
        TravelInfo info = new TravelInfo();
        info.setId(id);
        info.setTitle("기존 제목");
        info.setContent("<p>기존 본문</p>");
        info.setScope(TravelInfoScope.DOMESTIC);
        info.setContentType(contentType);
        info.setCategoryId(3L);
        info.setViews(0);
        info.setUserId(7L);
        return info;
    }

    private void allowCategory() {
        InfoCategory category = new InfoCategory();
        category.setId(3L);
        category.setName("계절여행");
        category.setIsVisible(true);
        when(infoCategoryMapper.findById(3L)).thenReturn(category);
    }

    private void stubTravelInfoInsert(Long id) {
        when(travelInfoMapper.insertTravelInfo(any())).thenAnswer(invocation -> {
            TravelInfo travelInfo = invocation.getArgument(0);
            travelInfo.setId(id);
            return 1;
        });
    }

    private void assertValidation(String message, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(TravelInfoValidationException.class)
                .hasMessage(message);
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
