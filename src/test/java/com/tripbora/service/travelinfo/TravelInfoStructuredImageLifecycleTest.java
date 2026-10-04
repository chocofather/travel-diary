package com.tripbora.service.travelinfo;

import com.tripbora.dto.TravelInfoForm;
import com.tripbora.model.InfoCategory;
import com.tripbora.model.TravelInfo;
import com.tripbora.model.TravelInfoContentFormat;
import com.tripbora.model.TravelInfoContentType;
import com.tripbora.model.TravelInfoScope;
import com.tripbora.repository.bookmark.BookmarkMapper;
import com.tripbora.repository.category.CategoryMapper;
import com.tripbora.repository.category.CountryCategoryMapper;
import com.tripbora.repository.category.InfoCategoryMapper;
import com.tripbora.repository.travelinfo.FestivalInfoMapper;
import com.tripbora.repository.travelinfo.TravelInfoMapper;
import com.tripbora.service.category.LocalizedReferenceNameResolver;
import com.tripbora.service.category.ReferenceNameLocalizationService;
import com.tripbora.service.file.FileUploadService;
import com.tripbora.service.post.PostContentSanitizer;
import com.tripbora.service.travelinfo.structured.StructuredContentTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 구조화 콘텐츠 본문 이미지 파일 정리. 위험한 작업이라 시점과 대상을 하나씩 고정한다.
 * <ul>
 *   <li>수정: 예전 본문에만 있던 이미지 중 다른 글도 쓰지 않는 것만, commit 뒤에 지운다.</li>
 *   <li>rollback: 아무것도 지우지 않는다.</li>
 *   <li>삭제: 글을 지운 뒤 다른 글이 쓰지 않는 이미지만 지우고, 대표 썸네일 정리는 그대로다.</li>
 *   <li>QUILL·등록: 본문 이미지 정리가 일어나지 않는다.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TravelInfoStructuredImageLifecycleTest {

    private static final String A = url("aaaaaaaa");
    private static final String B = url("bbbbbbbb");
    private static final String C = url("cccccccc");
    private static final String D = url("dddddddd");
    private static final String THUMBNAIL =
            "/uploads/travel-info/thumbnails/eeeeeeee-1111-4222-8333-444444444444.jpg";

    /** 수정 전: 큰 이미지 A, 이미지+글 B, 슬라이더 C·B (B 는 두 블록이 같이 쓴다). */
    private static final String BEFORE = document(
            fullImage("full", A), imageText("split", B), slider("slider", C, B));
    /** 수정 후: A 는 그대로, 새 이미지 D 추가, A 를 슬라이더에서도 쓴다. B·C 는 빠진다. */
    private static final String AFTER = document(fullImage("full", A), slider("slider", D, A));

    @Mock private TravelInfoMapper travelInfoMapper;
    @Mock private FestivalInfoMapper festivalInfoMapper;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private InfoCategoryMapper infoCategoryMapper;
    @Mock private FileUploadService fileUploadService;

    private TravelInfoService travelInfoService;

    @BeforeEach
    void setUp() {
        travelInfoService = new TravelInfoService(
                travelInfoMapper, festivalInfoMapper, bookmarkMapper, infoCategoryMapper,
                new PostContentSanitizer(), fileUploadService,
                new TravelInfoLocalizationService(travelInfoMapper),
                new ReferenceNameLocalizationService(
                        mock(CountryCategoryMapper.class), mock(CategoryMapper.class),
                        infoCategoryMapper, new LocalizedReferenceNameResolver()),
                StructuredContentTestSupport.structuredContentService());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---- update -------------------------------------------------------------------------

    @Test
    void updateDeletesOnlyRemovedImagesThatNoOtherArticleUsesAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        stubStructuredUpdate(BEFORE);
        when(travelInfoMapper.countStructuredContentReferences(B)).thenReturn(0);
        // C 는 다른 STRUCTURED 글도 쓰고 있다.
        when(travelInfoMapper.countStructuredContentReferences(C)).thenReturn(1);

        travelInfoService.update(10L, structuredForm(AFTER));

        // commit 전에는 아무것도 지우지 않는다. 남는 A 와 새 D 는 확인 대상도 아니다.
        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
        verify(travelInfoMapper, never()).countStructuredContentReferences(A);
        verify(travelInfoMapper, never()).countStructuredContentReferences(D);
        // 참조 확인은 자기 줄을 새 본문으로 바꾼 뒤에 한다.
        InOrder order = inOrder(travelInfoMapper);
        order.verify(travelInfoMapper).updateStructuredContent(eq(10L), anyString());
        order.verify(travelInfoMapper).countStructuredContentReferences(B);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileUploadService, times(1)).deleteTravelInfoContentImage(B);
        verify(fileUploadService, never()).deleteTravelInfoContentImage(C);
        verify(fileUploadService, never()).deleteTravelInfoContentImage(A);
        verify(fileUploadService, never()).deleteTravelInfoContentImage(D);
    }

    @Test
    void rolledBackUpdateKeepsEveryFile() {
        TransactionSynchronizationManager.initSynchronization();
        stubStructuredUpdate(BEFORE);
        when(travelInfoMapper.countStructuredContentReferences(B)).thenReturn(0);
        when(travelInfoMapper.countStructuredContentReferences(C)).thenReturn(0);

        travelInfoService.update(10L, structuredForm(AFTER));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
    }

    @Test
    void anImageStillUsedByAnotherBlockOfTheSameArticleIsNotRemoved() {
        TransactionSynchronizationManager.initSynchronization();
        // A 를 두 블록이 쓰다가 하나만 남긴다. A 는 여전히 본문에 있다.
        stubStructuredUpdate(document(fullImage("full", A), slider("slider", A)));

        travelInfoService.update(10L, structuredForm(document(slider("slider", A))));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(travelInfoMapper, never()).countStructuredContentReferences(any());
        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
    }

    @Test
    void quillUpdateNeverChecksOrDeletesContentImages() {
        TransactionSynchronizationManager.initSynchronization();
        TravelInfo existing = existing(TravelInfoContentFormat.QUILL, null);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(1);
        allowCategory();

        TravelInfoForm form = form(TravelInfoContentFormat.QUILL);
        form.setContent("<p>본문</p>");
        travelInfoService.update(10L, form);
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(travelInfoMapper, never()).countStructuredContentReferences(any());
        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
    }

    // ---- create -------------------------------------------------------------------------

    @Test
    void createKeepsUploadedImagesAndNeverCleansUp() {
        allowCategory();
        doAnswer(invocation -> {
            TravelInfo travelInfo = invocation.getArgument(0);
            travelInfo.setId(100L);
            return 1;
        }).when(travelInfoMapper).insertTravelInfo(any());

        travelInfoService.create(structuredForm(BEFORE), 7L);

        verify(travelInfoMapper, never()).countStructuredContentReferences(any());
        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
    }

    // ---- delete -------------------------------------------------------------------------

    @Test
    void deletingAStructuredArticleRemovesUnsharedImagesAndItsThumbnailAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(travelInfoMapper.findByIdForUpdate(10L))
                .thenReturn(existing(TravelInfoContentFormat.STRUCTURED, BEFORE));
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L)).thenReturn(List.of(THUMBNAIL));
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);
        when(travelInfoMapper.countStructuredContentReferences(A)).thenReturn(0);
        when(travelInfoMapper.countStructuredContentReferences(B)).thenReturn(0);
        when(travelInfoMapper.countStructuredContentReferences(C)).thenReturn(2);

        travelInfoService.delete(10L);

        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
        verify(fileUploadService, never()).deleteTravelInfoThumbnail(any());
        // 글을 지운 뒤에 센다. (지운 글 자신은 더 이상 참조로 잡히지 않는다)
        InOrder order = inOrder(travelInfoMapper);
        order.verify(travelInfoMapper).deleteTravelInfo(10L);
        order.verify(travelInfoMapper).countStructuredContentReferences(A);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileUploadService).deleteTravelInfoContentImage(A);
        verify(fileUploadService).deleteTravelInfoContentImage(B);
        verify(fileUploadService, never()).deleteTravelInfoContentImage(C);
        verify(fileUploadService).deleteTravelInfoThumbnail(THUMBNAIL);
    }

    @Test
    void rolledBackDeleteKeepsContentImagesAndThumbnail() {
        TransactionSynchronizationManager.initSynchronization();
        when(travelInfoMapper.findByIdForUpdate(10L))
                .thenReturn(existing(TravelInfoContentFormat.STRUCTURED, document(fullImage("full", A))));
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L)).thenReturn(List.of(THUMBNAIL));
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);
        when(travelInfoMapper.countStructuredContentReferences(A)).thenReturn(0);

        travelInfoService.delete(10L);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
        verify(fileUploadService, never()).deleteTravelInfoThumbnail(any());
    }

    @Test
    void deletingAQuillArticleKeepsTheExistingThumbnailOnlyFlow() {
        TransactionSynchronizationManager.initSynchronization();
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing(TravelInfoContentFormat.QUILL, null));
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L)).thenReturn(List.of(THUMBNAIL));
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);

        travelInfoService.delete(10L);
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(travelInfoMapper, never()).countStructuredContentReferences(any());
        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
        verify(fileUploadService).deleteTravelInfoThumbnail(THUMBNAIL);
    }

    @Test
    void unreadableStoredJsonDeletesNoContentImage() {
        TransactionSynchronizationManager.initSynchronization();
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing(TravelInfoContentFormat.STRUCTURED,
                "{\"version\":1,\"blocks\":[{\"id\":\"a\",\"type\":\"VIDEO\",\"url\":\"" + A + "\"}]}"));
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L)).thenReturn(List.of());
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);

        travelInfoService.delete(10L);
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(travelInfoMapper, never()).countStructuredContentReferences(any());
        verify(fileUploadService, never()).deleteTravelInfoContentImage(any());
    }

    @Test
    void outsideATransactionCleanupRunsRightAfterTheDatabaseWork() {
        when(travelInfoMapper.findByIdForUpdate(10L))
                .thenReturn(existing(TravelInfoContentFormat.STRUCTURED, document(fullImage("full", A))));
        when(travelInfoMapper.findMainImageUrlsByInfoId(10L)).thenReturn(List.of());
        when(travelInfoMapper.deleteTravelInfo(10L)).thenReturn(1);
        when(travelInfoMapper.countStructuredContentReferences(A)).thenReturn(0);

        travelInfoService.delete(10L);

        verify(fileUploadService).deleteTravelInfoContentImage(A);
    }

    // ---- helpers ------------------------------------------------------------------------

    private void stubStructuredUpdate(String storedJson) {
        TravelInfo existing = existing(TravelInfoContentFormat.STRUCTURED, storedJson);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(1);
        when(travelInfoMapper.updateStructuredContent(eq(10L), anyString())).thenReturn(1);
        allowCategory();
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

    private TravelInfoForm structuredForm(String json) {
        TravelInfoForm form = form(TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(json);
        return form;
    }

    private TravelInfoForm form(TravelInfoContentFormat contentFormat) {
        TravelInfoForm form = new TravelInfoForm();
        form.setTitle("서울 궁 투어");
        form.setContentFormat(contentFormat);
        form.setScope(TravelInfoScope.DOMESTIC);
        form.setContentType(TravelInfoContentType.GENERAL);
        form.setCategoryId(3L);
        return form;
    }

    private TravelInfo existing(TravelInfoContentFormat contentFormat, String structuredJson) {
        TravelInfo info = new TravelInfo();
        info.setId(10L);
        info.setTitle("기존 제목");
        info.setContent("<p>기존 본문</p>");
        info.setContentFormat(contentFormat);
        info.setStructuredContent(structuredJson);
        info.setScope(TravelInfoScope.DOMESTIC);
        info.setContentType(TravelInfoContentType.GENERAL);
        info.setCategoryId(3L);
        info.setViews(0);
        info.setUserId(7L);
        return info;
    }

    private void allowCategory() {
        InfoCategory category = new InfoCategory();
        category.setId(3L);
        category.setName("계절여행");
        category.setContentType(TravelInfoContentType.GENERAL);
        category.setIsVisible(true);
        when(infoCategoryMapper.findById(3L)).thenReturn(category);
    }

    private static String document(String... blocks) {
        return "{\"version\":1,\"blocks\":[" + String.join(",", blocks) + "]}";
    }

    private static String image(String url) {
        return "{\"url\":\"" + url + "\",\"width\":1200,\"height\":800}";
    }

    private static String fullImage(String id, String url) {
        return "{\"id\":\"" + id + "\",\"type\":\"FULL_IMAGE\",\"image\":" + image(url) + ",\"alt\":\"설명\"}";
    }

    private static String imageText(String id, String url) {
        return "{\"id\":\"" + id + "\",\"type\":\"IMAGE_TEXT\",\"imagePosition\":\"LEFT\",\"image\":"
                + image(url) + ",\"alt\":\"설명\",\"text\":\"본문\"}";
    }

    private static String slider(String id, String... urls) {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < urls.length; index++) {
            items.append(index == 0 ? "" : ",")
                    .append("{\"id\":\"i").append(index).append("\",\"image\":").append(image(urls[index]))
                    .append(",\"alt\":\"설명\"}");
        }
        return "{\"id\":\"" + id + "\",\"type\":\"IMAGE_SLIDER\",\"title\":\"슬라이더\",\"items\":[" + items + "]}";
    }

    private static String url(String prefix) {
        return "/uploads/travel-info/content/" + prefix + "-1111-4222-8333-444444444444.webp";
    }
}
