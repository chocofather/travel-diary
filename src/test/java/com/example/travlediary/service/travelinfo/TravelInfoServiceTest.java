package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.AdminTravelInfoDetailDto;
import com.example.travlediary.dto.InfoPeriodForm;
import com.example.travlediary.dto.TravelInfoForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    @Mock
    private FileUploadService fileUploadService;

    private TravelInfoService travelInfoService;

    @BeforeEach
    void setUp() {
        travelInfoService = new TravelInfoService(
                travelInfoMapper, infoCategoryMapper, new PostContentSanitizer(), fileUploadService);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getsFestivalAdminDetailWithCategorySanitizedContentAndMultiplePeriods() {
        TravelInfo existing = existingInfo(10L, TravelInfoContentType.FESTIVAL);
        existing.setTitle("벚꽃 축제");
        existing.setContent("<p onclick=\"alert(1)\"><span class=\"ql-font-pretendard\">축제 본문</span></p>"
                + "<p class=\"ql-indent-3\"><span class=\"ql-font-nanum-human\">들여쓴 안내</span></p>"
                + "<ul><li data-list=\"unchecked\"><span class=\"ql-font-school-safe-bareonbatang\">준비물</span></li>"
                + "<li data-list=\"checked\"><span class=\"ql-font-cafe24-dongdong\">예약</span></li></ul>"
                + "<p><span class=\"ql-font-gangwon-saeeum\">출발</span></p>"
                + "<img src=\"/uploads/editor/festival.png\" width=\"640\"><script>alert(1)</script>");
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
                .contains(
                        "<p><span class=\"ql-font-pretendard\">축제 본문</span></p>",
                        "class=\"ql-indent-3\"",
                        "class=\"ql-font-nanum-human\"",
                        "data-list=\"unchecked\"",
                        "class=\"ql-font-school-safe-bareonbatang\"",
                        "data-list=\"checked\"",
                        "class=\"ql-font-cafe24-dongdong\"",
                        "class=\"ql-font-gangwon-saeeum\"",
                        "src=\"/uploads/editor/festival.png\" width=\"640\""
                )
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
        verify(travelInfoMapper, never()).insertInfoImage(any());
        verify(fileUploadService, never()).saveTravelInfoThumbnail(any());
    }

    @Test
    void createsInfoWithOneMainThumbnail() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL);
        form.setThumbnailFile(thumbnailFile());
        allowCategory();
        stubTravelInfoInsert(100L);
        when(fileUploadService.saveTravelInfoThumbnail(form.getThumbnailFile()))
                .thenReturn("/uploads/travel-info/thumbnails/new.jpg");
        when(travelInfoMapper.insertInfoImage(any())).thenReturn(1);

        assertThat(travelInfoService.create(form, 7L)).isEqualTo(100L);

        ArgumentCaptor<InfoImage> captor = ArgumentCaptor.forClass(InfoImage.class);
        verify(travelInfoMapper).insertInfoImage(captor.capture());
        assertThat(captor.getValue().getInfoId()).isEqualTo(100L);
        assertThat(captor.getValue().getImageUrl())
                .isEqualTo("/uploads/travel-info/thumbnails/new.jpg");
        assertThat(captor.getValue().getIsMain()).isTrue();
        assertThat(captor.getValue().getOrderIndex()).isEqualTo(1);
    }

    @Test
    void createRollbackDeletesNewThumbnailFile() {
        beginTransactionSynchronization();
        TravelInfoForm form = form(TravelInfoContentType.GENERAL);
        form.setThumbnailFile(thumbnailFile());
        allowCategory();
        when(fileUploadService.saveTravelInfoThumbnail(form.getThumbnailFile()))
                .thenReturn("/uploads/travel-info/thumbnails/new.jpg");
        when(travelInfoMapper.insertTravelInfo(any())).thenReturn(0);

        assertThatThrownBy(() -> travelInfoService.create(form, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("여행정보 저장에 실패했습니다.");
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileUploadService).deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/new.jpg");
        verify(travelInfoMapper, never()).insertInfoImage(any());
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
        verify(travelInfoMapper, never()).findMainImageUrlsByInfoId(any());
        verify(travelInfoMapper, never()).deleteMainImagesByInfoId(any());
    }

    @Test
    void replacesThumbnailAndDeletesOldFilesOnlyAfterCommit() {
        beginTransactionSynchronization();
        TravelInfo existing = existingInfo(10L, TravelInfoContentType.GENERAL);
        TravelInfoForm form = form(TravelInfoContentType.GENERAL);
        form.setThumbnailFile(thumbnailFile());
        form.setRemoveThumbnail(true);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L)).thenReturn(List.of(
                "/uploads/travel-info/thumbnails/old-a.jpg",
                "/uploads/travel-info/thumbnails/old-b.jpg"));
        when(fileUploadService.saveTravelInfoThumbnail(form.getThumbnailFile()))
                .thenReturn("/uploads/travel-info/thumbnails/new.jpg");
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(1);
        when(travelInfoMapper.insertInfoImage(any())).thenReturn(1);
        allowCategory();

        travelInfoService.update(10L, form);

        verify(travelInfoMapper).deleteMainImagesByInfoId(10L);
        verify(travelInfoMapper).insertInfoImage(any());
        verify(fileUploadService, never()).deleteTravelInfoThumbnail(any());

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileUploadService).deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/old-a.jpg");
        verify(fileUploadService).deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/old-b.jpg");
        verify(fileUploadService, never()).deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/new.jpg");
    }

    @Test
    void removesThumbnailAndDeletesFileOnlyAfterCommit() {
        beginTransactionSynchronization();
        TravelInfo existing = existingInfo(10L, TravelInfoContentType.GENERAL);
        TravelInfoForm form = form(TravelInfoContentType.GENERAL);
        form.setRemoveThumbnail(true);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L))
                .thenReturn(List.of("/uploads/travel-info/thumbnails/old.jpg"));
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(1);
        allowCategory();

        travelInfoService.update(10L, form);

        verify(travelInfoMapper).deleteMainImagesByInfoId(10L);
        verify(travelInfoMapper, never()).insertInfoImage(any());
        verify(fileUploadService, never()).deleteTravelInfoThumbnail(any());

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileUploadService).deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/old.jpg");
    }

    @Test
    void rollbackDeletesNewThumbnailAndKeepsOldFile() {
        beginTransactionSynchronization();
        TravelInfo existing = existingInfo(10L, TravelInfoContentType.GENERAL);
        TravelInfoForm form = form(TravelInfoContentType.GENERAL);
        form.setThumbnailFile(thumbnailFile());
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L))
                .thenReturn(List.of("/uploads/travel-info/thumbnails/old.jpg"));
        when(fileUploadService.saveTravelInfoThumbnail(form.getThumbnailFile()))
                .thenReturn("/uploads/travel-info/thumbnails/new.jpg");
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(0);
        allowCategory();

        assertNotFound(() -> travelInfoService.update(10L, form));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileUploadService).deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/new.jpg");
        verify(fileUploadService, never()).deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/old.jpg");
        verify(travelInfoMapper, never()).deleteMainImagesByInfoId(any());
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
        form.setContent("<p onclick=\"alert(1)\"><span class=\"ql-font-noto-sans-kr\">안전 정보</span></p>"
                + "<img src=\"/uploads/editor/safe.png\" width=\"600\"><script>alert(1)</script>");
        allowCategory();
        stubTravelInfoInsert(100L);

        travelInfoService.create(form, 7L);

        ArgumentCaptor<TravelInfo> captor = ArgumentCaptor.forClass(TravelInfo.class);
        verify(travelInfoMapper).insertTravelInfo(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("안전 여행");
        assertThat(captor.getValue().getContent())
                .isEqualTo("<p><span class=\"ql-font-noto-sans-kr\">안전 정보</span></p>"
                        + "<img src=\"/uploads/editor/safe.png\" width=\"600\">")
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
    void deletesExistingTravelInfoAndReliesOnDatabaseCascadeBeforeDeletingFileAfterCommit() {
        beginTransactionSynchronization();
        when(travelInfoMapper.findByIdForUpdate(10L))
                .thenReturn(existingInfo(10L, TravelInfoContentType.FESTIVAL));
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L))
                .thenReturn(List.of("/uploads/travel-info/thumbnails/old.jpg"));
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);

        travelInfoService.delete(10L);

        verify(travelInfoMapper).deleteTravelInfo(10L);
        verify(travelInfoMapper, never()).deletePeriodsByInfoId(any());
        verify(travelInfoMapper, never()).deleteMainImagesByInfoId(any());
        verify(fileUploadService, never()).deleteTravelInfoThumbnail(any());

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileUploadService).deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/old.jpg");
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

    private MockMultipartFile thumbnailFile() {
        return new MockMultipartFile(
                "thumbnailFile", "thumbnail.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
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
