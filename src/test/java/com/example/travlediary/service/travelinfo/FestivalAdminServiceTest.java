package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.FestivalEditData;
import com.example.travlediary.dto.FestivalEditForm;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.kto.KtoFestivalImageDownloadService;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FestivalAdminServiceTest {

    @Mock
    private TravelInfoMapper travelInfoMapper;
    @Mock
    private FestivalInfoMapper festivalInfoMapper;
    @Mock
    private InfoCategoryMapper infoCategoryMapper;
    @Mock
    private BookmarkMapper bookmarkMapper;
    @Mock
    private KtoFestivalImageDownloadService festivalImageDownloadService;
    @Mock
    private TravelInfoService travelInfoService;
    @Mock
    private FestivalInfoService festivalInfoService;

    private FestivalAdminService service;

    @BeforeEach
    void setUp() {
        service = new FestivalAdminService(
                travelInfoMapper, festivalInfoMapper, infoCategoryMapper,
                bookmarkMapper, new PostContentSanitizer(), festivalImageDownloadService,
                travelInfoService, festivalInfoService);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void editDataRestoresFestivalFieldsPeriodImagesAndCurrentThumbnail() {
        TravelInfo travelInfo = festival(10L);
        FestivalInfo festivalInfo = festivalInfo();
        InfoPeriod period = period("2026-09-02", "2026-10-24");
        InfoImage main = image(101L, true, false, 1, "/uploads/travel-info/festivals/main.jpg");
        InfoImage thumbnail = image(105L, false, true, 5, "/uploads/travel-info/festivals/poster.jpg");
        when(travelInfoMapper.findById(10L)).thenReturn(travelInfo);
        when(travelInfoMapper.findPeriodsByInfoId(10L)).thenReturn(List.of(period));
        when(festivalInfoMapper.findByInfoId(10L)).thenReturn(festivalInfo);
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(main, thumbnail));

        FestivalEditData result = service.getEditData(10L);

        assertThat(result.form()).satisfies(form -> {
            assertThat(form.getTitle()).isEqualTo("경복궁 별빛야행");
            assertThat(form.getContent()).isEqualTo("<p>행사 소개</p>");
            assertThat(form.getScope()).isEqualTo(TravelInfoScope.DOMESTIC);
            assertThat(form.getCategoryId()).isEqualTo(5L);
            assertThat(form.getStartDate()).isEqualTo(LocalDate.parse("2026-09-02"));
            assertThat(form.getEndDate()).isEqualTo(LocalDate.parse("2026-10-24"));
            assertThat(form.getEventPlace()).isEqualTo("경복궁");
            assertThat(form.getThumbnailImageId()).isEqualTo(105L);
        });
        assertThat(result.images()).containsExactly(main, thumbnail);
    }

    @Test
    void editDataRejectsGeneralTravelInfoBeforeFestivalTablesAreRead() {
        TravelInfo general = festival(10L);
        general.setContentType(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findById(10L)).thenReturn(general);

        assertThatThrownBy(() -> service.getEditData(10L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(festivalInfoMapper);
    }

    @Test
    void updateChangesEditableTablesKeepsFestivalTypeAndSourceAndSelectsOwnedThumbnail() {
        TravelInfo existing = festival(10L);
        FestivalInfo source = festivalInfo();
        InfoImage main = image(101L, true, false, 1, "/uploads/travel-info/festivals/main.jpg");
        InfoImage poster = image(105L, false, true, 5, "/uploads/travel-info/festivals/poster.jpg");
        FestivalEditForm form = validForm();
        form.setThumbnailImageId(101L);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(infoCategoryMapper.findById(6L)).thenReturn(festivalCategory(6L));
        when(festivalInfoMapper.findByInfoId(10L)).thenReturn(source);
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(main, poster));
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.update(any())).thenReturn(1);
        when(travelInfoMapper.setThumbnailByIdAndInfoId(101L, 10L)).thenReturn(1);

        service.update(10L, form);

        assertThat(existing.getTitle()).isEqualTo("수정된 축제");
        assertThat(existing.getContent()).isEqualTo("<p>수정 소개</p>");
        assertThat(existing.getScope()).isEqualTo(TravelInfoScope.INTERNATIONAL);
        assertThat(existing.getCategoryId()).isEqualTo(6L);
        assertThat(existing.getContentType()).isEqualTo(TravelInfoContentType.FESTIVAL);
        ArgumentCaptor<InfoPeriod> periodCaptor = ArgumentCaptor.forClass(InfoPeriod.class);
        verify(travelInfoMapper).insertPeriod(periodCaptor.capture());
        assertThat(periodCaptor.getValue().getStartDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(periodCaptor.getValue().getEndDate()).isEqualTo(LocalDate.parse("2026-11-30"));
        ArgumentCaptor<FestivalInfo> festivalCaptor = ArgumentCaptor.forClass(FestivalInfo.class);
        verify(festivalInfoMapper).update(festivalCaptor.capture());
        assertThat(festivalCaptor.getValue().getEventPlace()).isEqualTo("수정 장소");
        assertThat(festivalCaptor.getValue().getSourceType()).isEqualTo("KTO_TOURAPI");
        assertThat(festivalCaptor.getValue().getExternalContentId()).isEqualTo("2648460");
        verify(travelInfoMapper).clearThumbnailsByInfoId(10L);
        verify(travelInfoMapper).setThumbnailByIdAndInfoId(101L, 10L);
        org.mockito.InOrder thumbnailOrder = inOrder(travelInfoMapper);
        thumbnailOrder.verify(travelInfoMapper).clearThumbnailsByInfoId(10L);
        thumbnailOrder.verify(travelInfoMapper).setThumbnailByIdAndInfoId(101L, 10L);
        assertThat(main.getIsMain()).isTrue();
        assertThat(poster.getIsMain()).isFalse();
    }

    @Test
    void updateRejectsThumbnailOwnedByAnotherFestivalBeforeAnyMutation() {
        FestivalEditForm form = validForm();
        form.setThumbnailImageId(999L);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));
        when(infoCategoryMapper.findById(6L)).thenReturn(festivalCategory(6L));
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(
                image(101L, true, false, 1, "/uploads/travel-info/festivals/main.jpg")));

        assertThatThrownBy(() -> service.update(10L, form))
                .isInstanceOfSatisfying(FestivalValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("thumbnailImageId"));

        verify(travelInfoMapper, never()).updateTravelInfo(any());
        verify(travelInfoMapper, never()).clearThumbnailsByInfoId(any());
    }

    @Test
    void updateRejectsEndDateBeforeStartDate() {
        FestivalEditForm form = validForm();
        form.setEndDate(LocalDate.parse("2026-10-31"));
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));

        assertThatThrownBy(() -> service.update(10L, form))
                .isInstanceOfSatisfying(FestivalValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("endDate"));
    }

    @Test
    void updateRejectsGeneralCategory() {
        FestivalEditForm form = validForm();
        InfoCategory generalCategory = festivalCategory(6L);
        generalCategory.setContentType(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));
        when(infoCategoryMapper.findById(6L)).thenReturn(generalCategory);

        assertThatThrownBy(() -> service.update(10L, form))
                .isInstanceOfSatisfying(FestivalValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("categoryId"));

        verify(travelInfoMapper, never()).updateTravelInfo(any());
    }

    @Test
    void updateAllowsClearingThumbnailWithoutChangingMainFlags() {
        TravelInfo existing = festival(10L);
        InfoImage main = image(101L, true, false, 1, "/uploads/travel-info/festivals/main.jpg");
        InfoImage poster = image(105L, false, true, 5, "/uploads/travel-info/festivals/poster.jpg");
        FestivalEditForm form = validForm();
        form.setThumbnailImageId(null);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(infoCategoryMapper.findById(6L)).thenReturn(festivalCategory(6L));
        when(festivalInfoMapper.findByInfoId(10L)).thenReturn(festivalInfo());
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(main, poster));
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.update(any())).thenReturn(1);

        service.update(10L, form);

        verify(travelInfoMapper).clearThumbnailsByInfoId(10L);
        verify(travelInfoMapper, never()).setThumbnailByIdAndInfoId(any(), any());
        assertThat(main.getIsMain()).isTrue();
        assertThat(poster.getIsMain()).isFalse();
    }

    @Test
    void missingFestivalInfoIsInsertedDefensivelyWithAdminSource() {
        FestivalEditForm form = validForm();
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));
        when(infoCategoryMapper.findById(6L)).thenReturn(festivalCategory(6L));
        when(festivalInfoMapper.findByInfoId(10L)).thenReturn(null);
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of());
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.insert(any())).thenReturn(1);

        service.update(10L, form);

        ArgumentCaptor<FestivalInfo> captor = ArgumentCaptor.forClass(FestivalInfo.class);
        verify(festivalInfoMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getExternalContentId()).isNull();
    }

    @Test
    void festivalInfoFailureRollsBackTheWholeUpdateTransaction() {
        FestivalEditForm form = validForm();
        stubValidUpdate(form);
        doThrow(new IllegalStateException("festival failure")).when(festivalInfoMapper).update(any());
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();

        assertThatThrownBy(() -> transactionalService(transactionManager).update(10L, form))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("festival failure");

        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    @Test
    void thumbnailFailureRollsBackOtherFestivalUpdates() {
        FestivalEditForm form = validForm();
        form.setThumbnailImageId(101L);
        stubValidUpdate(form);
        when(travelInfoMapper.setThumbnailByIdAndInfoId(101L, 10L)).thenReturn(0);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();

        assertThatThrownBy(() -> transactionalService(transactionManager).update(10L, form))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("목록 썸네일을 변경하지 못했습니다.");

        assertThat(transactionManager.rolledBack).isTrue();
    }

    @Test
    void deleteUsesTravelInfoCascadeAndDeletesManagedImagesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        InfoImage first = image(101L, true, false, 1,
                "/uploads/travel-info/festivals/11111111-1111-4111-8111-111111111111.jpg");
        InfoImage second = image(102L, false, false, 2,
                "/uploads/travel-info/festivals/22222222-2222-4222-8222-222222222222.png");
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(first, second));
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);

        service.delete(10L);

        verify(bookmarkMapper).deleteByTarget("TRAVEL_INFO", 10L);
        verify(travelInfoMapper).deleteTravelInfo(10L);
        verify(festivalInfoMapper, never()).deleteByInfoId(any());
        verify(travelInfoMapper, never()).deletePeriodsByInfoId(any());
        verify(festivalImageDownloadService, never()).deleteDownloadedFestivalImage(any(String.class));

        completeSynchronization(TransactionSynchronization.STATUS_COMMITTED);

        verify(festivalImageDownloadService).deleteDownloadedFestivalImage(first.getImageUrl());
        verify(festivalImageDownloadService).deleteDownloadedFestivalImage(second.getImageUrl());
    }

    @Test
    void deleteWithNoImagesStillDeletesFestivalRoot() {
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of());
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);

        service.delete(10L);

        verify(travelInfoMapper).deleteTravelInfo(10L);
        verifyNoInteractions(festivalImageDownloadService);
    }

    @Test
    void deleteRejectsGeneralBeforeBookmarkDatabaseOrFileMutation() {
        TravelInfo general = festival(10L);
        general.setContentType(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(general);

        assertThatThrownBy(() -> service.delete(10L)).isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(bookmarkMapper, festivalImageDownloadService);
        verify(travelInfoMapper, never()).deleteTravelInfo(any());
    }

    @Test
    void deleteRollbackNeverDeletesPhysicalFestivalFiles() {
        InfoImage image = image(101L, true, false, 1,
                "/uploads/travel-info/festivals/11111111-1111-4111-8111-111111111111.jpg");
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(image));
        doThrow(new IllegalStateException("delete failure")).when(travelInfoMapper).deleteTravelInfo(10L);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();

        assertThatThrownBy(() -> transactionalService(transactionManager).delete(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delete failure");

        assertThat(transactionManager.rolledBack).isTrue();
        verifyNoInteractions(festivalImageDownloadService);
    }

    @Test
    void physicalFileCleanupFailureDoesNotFailCommittedDelete() {
        TransactionSynchronizationManager.initSynchronization();
        InfoImage image = image(101L, true, false, 1,
                "/uploads/travel-info/festivals/11111111-1111-4111-8111-111111111111.jpg");
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(image));
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);
        doThrow(new IllegalStateException("file failure"))
                .when(festivalImageDownloadService).deleteDownloadedFestivalImage(image.getImageUrl());

        service.delete(10L);

        org.assertj.core.api.Assertions.assertThatCode(
                () -> completeSynchronization(TransactionSynchronization.STATUS_COMMITTED))
                .doesNotThrowAnyException();
    }

    private TravelInfo festival(Long id) {
        TravelInfo info = new TravelInfo();
        info.setId(id);
        info.setTitle("경복궁 별빛야행");
        info.setContent("<p>행사 소개</p>");
        info.setScope(TravelInfoScope.DOMESTIC);
        info.setContentType(TravelInfoContentType.FESTIVAL);
        info.setCategoryId(5L);
        info.setViews(18);
        info.setUserId(7L);
        return info;
    }

    private FestivalEditForm validForm() {
        FestivalEditForm form = new FestivalEditForm();
        form.setTitle("  수정된 축제  ");
        form.setContent("<p>수정 소개</p><script>alert(1)</script>");
        form.setScope(TravelInfoScope.INTERNATIONAL);
        form.setCategoryId(6L);
        form.setStartDate(LocalDate.parse("2026-11-01"));
        form.setEndDate(LocalDate.parse("2026-11-30"));
        form.setEventPlace(" 수정 장소 ");
        form.setAddress(" 수정 주소 ");
        form.setPlayTime(" 19:30 ");
        form.setUseTime(" 무료 ");
        form.setSponsor1(" 주최 ");
        form.setSponsor1Tel(" 02-1111-1111 ");
        form.setSponsor2(" 주관 ");
        form.setSponsor2Tel(" 02-2222-2222 ");
        form.setContactTel(" 1234 ");
        form.setHomepageUrl(" https://example.com/updated ");
        return form;
    }

    private InfoCategory festivalCategory(Long id) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setContentType(TravelInfoContentType.FESTIVAL);
        category.setIsVisible(true);
        return category;
    }

    private void stubValidUpdate(FestivalEditForm form) {
        InfoImage image = image(101L, true, false, 1, "/uploads/travel-info/festivals/main.jpg");
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(festival(10L));
        when(infoCategoryMapper.findById(6L)).thenReturn(festivalCategory(6L));
        when(festivalInfoMapper.findByInfoId(10L)).thenReturn(festivalInfo());
        when(travelInfoMapper.findImagesByInfoId(10L)).thenReturn(List.of(image));
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(festivalInfoMapper.update(any())).thenReturn(1);
        if (form.getThumbnailImageId() != null) {
            when(travelInfoMapper.setThumbnailByIdAndInfoId(form.getThumbnailImageId(), 10L)).thenReturn(1);
        }
    }

    private FestivalAdminService transactionalService(RecordingTransactionManager transactionManager) {
        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(interceptor);
        return (FestivalAdminService) proxyFactory.getProxy();
    }

    private void completeSynchronization(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private FestivalInfo festivalInfo() {
        FestivalInfo info = new FestivalInfo();
        info.setInfoId(10L);
        info.setEventPlace("경복궁");
        info.setAddress("서울 종로구");
        info.setPlayTime("19:00");
        info.setUseTime("유료");
        info.setSponsor1("국가유산청");
        info.setSponsor1Tel("02-1111-2222");
        info.setSponsor2("국가유산진흥원");
        info.setSponsor2Tel("02-3333-4444");
        info.setContactTel("1522-2295");
        info.setHomepageUrl("https://example.com/festival");
        info.setSourceType("KTO_TOURAPI");
        info.setExternalContentId("2648460");
        return info;
    }

    private InfoPeriod period(String startDate, String endDate) {
        InfoPeriod period = new InfoPeriod();
        period.setInfoId(10L);
        period.setStartDate(LocalDate.parse(startDate));
        period.setEndDate(LocalDate.parse(endDate));
        return period;
    }

    private InfoImage image(Long id, boolean main, boolean thumbnail, int orderIndex, String url) {
        InfoImage image = new InfoImage();
        image.setId(id);
        image.setInfoId(10L);
        image.setImageUrl(url);
        image.setIsMain(main);
        image.setIsThumbnail(thumbnail);
        image.setOrderIndex(orderIndex);
        image.setSourceTitle(main ? "대표사진" : "공식 포스터");
        image.setLicenseType("KOGL_TYPE_1");
        return image;
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean committed;
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
