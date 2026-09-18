package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryPhotoSelection;
import com.example.travlediary.dto.DiaryCoverLibraryRegistrationRequest;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverDesignMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.DiaryCoverLibraryPhotoStorage;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryCoverLibraryRegistrationServiceTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-09-17T03:00:00Z");

    @Mock private DiaryCoverLibraryItemMapper itemMapper;
    @Mock private DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    @Mock private DiaryCoverLibraryElementMapper elementMapper;
    @Mock private DiaryCoverDesignMapper designMapper;
    @Mock private DiaryCoverDesignElementMapper designElementMapper;
    @Mock private UserMapper userMapper;
    @Mock private DiaryCoverLibraryPhotoStorage photoStorage;
    @Mock private DiaryPrivatePhotoStorage diaryPhotoStorage;

    private DiaryCoverLibraryRegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DiaryCoverLibraryRegistrationServiceImpl(
                itemMapper, photoAssetMapper, elementMapper,
                designMapper, designElementMapper, userMapper, photoStorage, diaryPhotoStorage,
                Clock.fixed(CONFIRMED_AT, ZoneOffset.UTC));
    }

    /** 공유 원본은 개인 사진 저장소가 저장 키를 확인해 내준 경로만 쓴다. */
    private void givenSharedSource(DiaryCoverDesignElement photo, String storageKey) {
        Path source = Path.of("/tmp/private-diary", storageKey);
        when(diaryPhotoStorage.resolveManagedSource(photo.getImageUrl())).thenReturn(source);
        when(photoStorage.copyFromDiaryPrivateStorage(source))
                .thenReturn(new DiaryCoverLibraryPhotoStorage.StoredPhoto(
                        storageKey, "image/jpeg", 321L));
    }

    @Test
    void anotherUsersDesignCannotBeRegistered() {
        when(designMapper.findByIdAndUserId(41L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> service.register(7L, request(41L, Map.of())))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(itemMapper, photoAssetMapper, elementMapper, photoStorage);
        verify(designElementMapper, never()).findAllByDesignId(any());
    }

    @Test
    void excludedPhotoKeepsGeometryAndNormalizesLegacyStyleWithoutCreatingAnAsset() {
        DiaryCoverDesign design = ownedDesign();
        DiaryCoverDesignElement photo = photo(101L, null, "/uploads/diary-cover-designs/original.jpg");
        prepareRegistration(design, List.of(photo));

        DiaryCoverLibraryItem registered = service.register(7L, request(41L, Map.of()));

        assertThat(registered.getId()).isEqualTo(501L);
        ArgumentCaptor<DiaryCoverLibraryItem> item = ArgumentCaptor.forClass(DiaryCoverLibraryItem.class);
        verify(itemMapper).insert(item.capture());
        assertThat(item.getValue()).satisfies(saved -> {
            assertThat(saved.getCreatorUserId()).isEqualTo(7L);
            assertThat(saved.getCreatorDisplayName()).isEqualTo("여행자");
            assertThat(saved.getSourceCoverDesignId()).isEqualTo(41L);
            assertThat(saved.getTitle()).isEqualTo("여름 표지");
            assertThat(saved.getDescription()).isEqualTo("바다 여행");
            assertThat(saved.getBaseCoverStyle()).isEqualTo("LEATHER_BLACK");
            assertThat(saved.getBackgroundColor()).isEqualTo("#123456");
            assertThat(saved.getSnapshotVersion()).isEqualTo(1);
        });

        ArgumentCaptor<DiaryCoverLibraryElement> element =
                ArgumentCaptor.forClass(DiaryCoverLibraryElement.class);
        verify(elementMapper).insert(element.capture());
        assertThat(element.getValue()).satisfies(saved -> {
            assertThat(saved.getPhotoShareMode()).isEqualTo(DiaryCoverLibraryPhotoShareMode.EXCLUDED);
            assertThat(saved.getPhotoAssetId()).isNull();
            assertThat(saved.getImageUrl()).isNull();
            assertThat(saved.getPhotoStyle()).isEqualTo("POLAROID");
            assertThat(saved.getPositionX()).isEqualByComparingTo("0.12500");
            assertThat(saved.getPositionY()).isEqualByComparingTo("0.25000");
            assertThat(saved.getWidth()).isEqualByComparingTo("0.45000");
            assertThat(saved.getHeight()).isEqualByComparingTo("0.55000");
            assertThat(saved.getRotation()).isEqualByComparingTo("12.50");
            assertThat(saved.getZIndex()).isEqualTo(3);
        });
        verifyNoInteractions(photoAssetMapper, photoStorage);
    }

    @Test
    void includedPhotoCreatesAPrivateAssetAndLinksTheSnapshotElement() {
        DiaryCoverDesignElement photo = photo(101L, "FULL", "/uploads/diary-cover-designs/original.jpg");
        prepareRegistration(ownedDesign(), List.of(photo));
        givenSharedSource(photo, "photos/private-copy.jpg");
        when(photoAssetMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCoverLibraryPhotoAsset.class).setId(701L);
            return 1;
        });
        Map<Long, DiaryCoverLibraryPhotoSelection> selections = Map.of(
                101L, new DiaryCoverLibraryPhotoSelection(
                        DiaryCoverLibraryPhotoShareMode.INCLUDED, true));

        service.register(7L, request(41L, selections));

        ArgumentCaptor<DiaryCoverLibraryPhotoAsset> asset =
                ArgumentCaptor.forClass(DiaryCoverLibraryPhotoAsset.class);
        verify(photoAssetMapper).insert(asset.capture());
        assertThat(asset.getValue()).satisfies(saved -> {
            assertThat(saved.getLibraryItemId()).isEqualTo(501L);
            assertThat(saved.getOriginalUploaderUserId()).isEqualTo(7L);
            assertThat(saved.getOriginalUploaderDisplayName()).isEqualTo("여행자");
            assertThat(saved.getStorageKey()).isEqualTo("photos/private-copy.jpg");
            assertThat(saved.getContentType()).isEqualTo("image/jpeg");
            assertThat(saved.getFileSize()).isEqualTo(321L);
            assertThat(saved.getRightsConfirmedAt().toInstant()).isEqualTo(CONFIRMED_AT);
            assertThat(saved.getRightsTermsVersion())
                    .isEqualTo(CoverLibraryPhotoRightsPolicy.CURRENT_TERMS_VERSION);
        });

        ArgumentCaptor<DiaryCoverLibraryElement> element =
                ArgumentCaptor.forClass(DiaryCoverLibraryElement.class);
        verify(elementMapper).insert(element.capture());
        assertThat(element.getValue().getPhotoShareMode())
                .isEqualTo(DiaryCoverLibraryPhotoShareMode.INCLUDED);
        assertThat(element.getValue().getPhotoAssetId()).isEqualTo(701L);
        assertThat(element.getValue().getImageUrl()).isNull();
        assertThat(element.getValue().getPhotoStyle()).isEqualTo("FULL");
    }

    @Test
    void includedPhotoWithoutRightsConfirmationIsRejectedBeforeAnythingIsWritten() {
        DiaryCoverDesignElement photo = photo(101L, "FULL", "/uploads/photo.jpg");
        when(designMapper.findByIdAndUserId(41L, 7L)).thenReturn(ownedDesign());
        when(designElementMapper.findAllByDesignId(41L)).thenReturn(List.of(photo));
        Map<Long, DiaryCoverLibraryPhotoSelection> selections = Map.of(
                101L, new DiaryCoverLibraryPhotoSelection(
                        DiaryCoverLibraryPhotoShareMode.INCLUDED, false));

        assertThatThrownBy(() -> service.register(7L, request(41L, selections)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("권리");

        verifyNoInteractions(itemMapper, photoAssetMapper, elementMapper, photoStorage, userMapper);
    }

    @Test
    void inheritedSharedPhotoCannotBeIncludedAgainAsAPersonalUpload() {
        DiaryCoverDesignElement photo = photo(101L, "FULL", null);
        photo.setLibraryPhotoAssetId(701L);
        when(designMapper.findByIdAndUserId(41L, 7L)).thenReturn(ownedDesign());
        when(designElementMapper.findAllByDesignId(41L)).thenReturn(List.of(photo));
        Map<Long, DiaryCoverLibraryPhotoSelection> selections = Map.of(
                101L, new DiaryCoverLibraryPhotoSelection(
                        DiaryCoverLibraryPhotoShareMode.INCLUDED, true));

        assertThatThrownBy(() -> service.register(7L, request(41L, selections)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("받은 공유 사진은 사진 제외로만");

        verifyNoInteractions(itemMapper, photoAssetMapper, elementMapper, photoStorage, userMapper);
    }

    @Test
    void stickerNoteAndTextPayloadsAndGeometryArePreserved() {
        DiaryCoverDesignElement sticker = element(201L, "STICKER");
        sticker.setImageUrl("/uploads/diary-stickers/normal/pin.png");
        DiaryCoverDesignElement note = element(202L, "NOTE");
        note.setTextContent("출발");
        note.setStyleType("DATE_LABEL");
        note.setColorType("SAGE");
        DiaryCoverDesignElement text = element(203L, "TEXT");
        text.setTextContent("JEJU");
        text.setTextFont("nanum-square");
        text.setTextColor("#abcdef");
        prepareRegistration(ownedDesign(), List.of(sticker, note, text));

        service.register(7L, request(41L, Map.of()));

        ArgumentCaptor<DiaryCoverLibraryElement> elements =
                ArgumentCaptor.forClass(DiaryCoverLibraryElement.class);
        verify(elementMapper, org.mockito.Mockito.times(3)).insert(elements.capture());
        assertThat(elements.getAllValues()).extracting(DiaryCoverLibraryElement::getElementType)
                .containsExactly("STICKER", "NOTE", "TEXT");
        assertThat(elements.getAllValues().get(0).getImageUrl())
                .isEqualTo("/uploads/diary-stickers/normal/pin.png");
        assertThat(elements.getAllValues().get(1)).satisfies(saved -> {
            assertThat(saved.getTextContent()).isEqualTo("출발");
            assertThat(saved.getStyleType()).isEqualTo("DATE_LABEL");
            assertThat(saved.getColorType()).isEqualTo("SAGE");
        });
        assertThat(elements.getAllValues().get(2)).satisfies(saved -> {
            assertThat(saved.getTextContent()).isEqualTo("JEJU");
            assertThat(saved.getTextFont()).isEqualTo("nanum-square");
            assertThat(saved.getTextColor()).isEqualTo("#abcdef");
            assertThat(saved.getPositionX()).isEqualByComparingTo("0.12500");
            assertThat(saved.getRotation()).isEqualByComparingTo("12.50");
        });
    }

    @Test
    void aDatabaseFailureRollsBackAndDeletesEveryNewPrivatePhoto() {
        DiaryCoverDesignElement photo = photo(101L, "FULL", "/uploads/photo.jpg");
        prepareRegistration(ownedDesign(), List.of(photo));
        givenSharedSource(photo, "photos/rollback.jpg");
        when(photoAssetMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCoverLibraryPhotoAsset.class).setId(701L);
            return 1;
        });
        when(elementMapper.insert(any())).thenThrow(new IllegalStateException("DB failure"));
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        DiaryCoverLibraryRegistrationService transactional = transactionalProxy(transactionManager);
        Map<Long, DiaryCoverLibraryPhotoSelection> selections = Map.of(
                101L, new DiaryCoverLibraryPhotoSelection(
                        DiaryCoverLibraryPhotoShareMode.INCLUDED, true));

        assertThatThrownBy(() -> transactional.register(7L, request(41L, selections)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB failure");

        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
        verify(photoStorage).delete("photos/rollback.jpg");
    }

    private DiaryCoverLibraryRegistrationService transactionalProxy(
            RecordingTransactionManager transactionManager) {
        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(interceptor);
        return (DiaryCoverLibraryRegistrationService) proxyFactory.getProxy();
    }

    /**
     * 라이브러리에서 받은 디자인(출처가 있는 디자인)은 편집했더라도 다시 공유할 수 없다.
     * 요소·회원·파일을 읽거나 쓰기 전에 거부한다.
     */
    @Test
    void designDownloadedFromTheLibraryCannotBeSharedAgain() {
        DiaryCoverDesign downloaded = ownedDesign();
        downloaded.setSourceLibraryItemId(11L);
        when(designMapper.findByIdAndUserId(41L, 7L)).thenReturn(downloaded);

        assertThatThrownBy(() -> service.register(7L, request(41L, Map.of())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("라이브러리에서 받은 디자인은 다시 공유할 수 없습니다.");
                });

        verifyNoInteractions(designElementMapper, userMapper, itemMapper, elementMapper,
                photoAssetMapper, photoStorage);
    }

    /** 직접 만든 디자인(출처 없음)은 기존처럼 공유된다. */
    @Test
    void designMadeByTheMemberCanStillBeShared() {
        prepareRegistration(ownedDesign(), List.of(element(301L, "STICKER")));

        DiaryCoverLibraryItem item = service.register(7L, request(41L, Map.of()));

        assertThat(item.getId()).isEqualTo(501L);
        verify(itemMapper).insert(any());
        verify(elementMapper).insert(any());
    }

    private void prepareRegistration(DiaryCoverDesign design,
                                     List<DiaryCoverDesignElement> elements) {
        when(designMapper.findByIdAndUserId(41L, 7L)).thenReturn(design);
        when(designElementMapper.findAllByDesignId(41L)).thenReturn(elements);
        User user = new User();
        user.setId(7L);
        user.setNickname("여행자");
        when(userMapper.findById(7L)).thenReturn(user);
        when(itemMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCoverLibraryItem.class).setId(501L);
            return 1;
        });
        when(elementMapper.insert(any())).thenReturn(1);
    }

    private DiaryCoverDesign ownedDesign() {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setId(41L);
        design.setUserId(7L);
        design.setName("원본");
        design.setBaseCoverStyle("LEATHER_BLACK");
        design.setBackgroundColor("#123456");
        return design;
    }

    private DiaryCoverDesignElement photo(Long id, String style, String imageUrl) {
        DiaryCoverDesignElement element = element(id, "PHOTO");
        element.setPhotoStyle(style);
        element.setImageUrl(imageUrl);
        return element;
    }

    private DiaryCoverDesignElement element(Long id, String type) {
        DiaryCoverDesignElement element = new DiaryCoverDesignElement();
        element.setId(id);
        element.setDesignId(41L);
        element.setElementType(type);
        element.setPositionX(new BigDecimal("0.12500"));
        element.setPositionY(new BigDecimal("0.25000"));
        element.setWidth(new BigDecimal("0.45000"));
        element.setHeight(new BigDecimal("0.55000"));
        element.setRotation(new BigDecimal("12.50"));
        element.setZIndex(3);
        return element;
    }

    private DiaryCoverLibraryRegistrationRequest request(
            Long designId, Map<Long, DiaryCoverLibraryPhotoSelection> selections) {
        return new DiaryCoverLibraryRegistrationRequest(
                designId, "  여름 표지  ", "  바다 여행  ", selections);
    }

    private static final class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {
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
