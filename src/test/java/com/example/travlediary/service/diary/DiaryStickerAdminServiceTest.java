package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryStickerForm;
import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerCatalogItem;
import com.example.travlediary.model.DiaryStickerCategoryEntity;
import com.example.travlediary.model.DiaryStickerType;
import com.example.travlediary.repository.diary.DiaryStickerMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryStickerAdminServiceTest {

    @Mock
    private DiaryStickerMapper mapper;
    @Mock
    private FileUploadService fileUploadService;

    @Test
    void createStoresUploadedImageAndCatalogAttributes() {
        when(mapper.findCategoryById(3L)).thenReturn(category(3L));
        when(fileUploadService.saveDiaryStickerImage(any(), any()))
                .thenReturn("/uploads/diary-stickers/masking-tape/new.webp");
        DiaryStickerAdminService service = new DiaryStickerAdminService(mapper, fileUploadService);
        DiaryStickerForm form = form();

        service.create(form);

        ArgumentCaptor<DiaryStickerCatalogItem> saved =
                ArgumentCaptor.forClass(DiaryStickerCatalogItem.class);
        verify(mapper).insertSticker(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("별빛 테이프");
        assertThat(saved.getValue().getCategoryId()).isEqualTo(3L);
        assertThat(saved.getValue().getStickerType()).isEqualTo(DiaryStickerType.MASKING_TAPE);
        assertThat(saved.getValue().getAccessTier()).isEqualTo(DiaryStickerAccessTier.PREMIUM);
        assertThat(saved.getValue().getImageUrl())
                .isEqualTo("/uploads/diary-stickers/masking-tape/new.webp");
        assertThat(saved.getValue().getCatalogKey()).startsWith("admin-");
    }

    @Test
    void hidingStickerOnlyChangesVisibilityAndNeverDeletesItsImage() {
        when(mapper.findStickerById(9L)).thenReturn(sticker(9L, DiaryStickerType.NORMAL));
        DiaryStickerAdminService service = new DiaryStickerAdminService(mapper, fileUploadService);

        service.hide(9L);

        verify(mapper).hideSticker(9L);
        verify(fileUploadService, never()).deleteDiaryStickerImage(any());
    }

    @Test
    void changingTypeRequiresAReplacementImageSoSavedUrlsKeepTheirOriginalMeaning() {
        when(mapper.findStickerById(9L)).thenReturn(sticker(9L, DiaryStickerType.NORMAL));
        when(mapper.findCategoryById(3L)).thenReturn(category(3L));
        DiaryStickerAdminService service = new DiaryStickerAdminService(mapper, fileUploadService);
        DiaryStickerForm form = form();
        form.setImage(null);

        assertThatThrownBy(() -> service.update(9L, form))
                .isInstanceOf(DiaryStickerValidationException.class)
                .hasMessageContaining("유형을 변경하려면 새 이미지를 선택");
        verify(mapper, never()).updateSticker(any());
    }

    @Test
    void replacingImageKeepsThePreviousUrlAndTapeMetadataAsHiddenHistory() {
        DiaryStickerCatalogItem current = sticker(9L, DiaryStickerType.MASKING_TAPE);
        current.setImageUrl("/images/diary/stickers/masking-tape/old.svg");
        current.setCollectionCode("pattern");
        current.setTapeStyle("TRANSLUCENT");
        current.setRepeatLeftUrl("/images/diary/stickers/masking-tape/repeat/old-left.svg");
        current.setRepeatCenterUrl("/images/diary/stickers/masking-tape/repeat/old-center.svg");
        current.setRepeatRightUrl("/images/diary/stickers/masking-tape/repeat/old-right.svg");
        when(mapper.findStickerById(9L)).thenReturn(current);
        when(mapper.findCategoryById(3L)).thenReturn(category(3L));
        when(fileUploadService.saveDiaryStickerImage(any(), any()))
                .thenReturn("/uploads/diary-stickers/masking-tape/new.webp");
        DiaryStickerAdminService service = new DiaryStickerAdminService(mapper, fileUploadService);

        service.update(9L, form());

        ArgumentCaptor<DiaryStickerCatalogItem> updated =
                ArgumentCaptor.forClass(DiaryStickerCatalogItem.class);
        ArgumentCaptor<DiaryStickerCatalogItem> history =
                ArgumentCaptor.forClass(DiaryStickerCatalogItem.class);
        verify(mapper).updateSticker(updated.capture());
        verify(mapper).insertSticker(history.capture());
        assertThat(updated.getValue().getImageUrl())
                .isEqualTo("/uploads/diary-stickers/masking-tape/new.webp");
        assertThat(history.getValue().getCatalogKey()).startsWith("history-");
        assertThat(history.getValue().getImageUrl()).isEqualTo(current.getImageUrl());
        assertThat(history.getValue().isVisible()).isFalse();
        assertThat(history.getValue().getStickerType()).isEqualTo(DiaryStickerType.MASKING_TAPE);
        assertThat(history.getValue().getRepeatLeftUrl()).isEqualTo(current.getRepeatLeftUrl());
        assertThat(history.getValue().getRepeatCenterUrl()).isEqualTo(current.getRepeatCenterUrl());
        assertThat(history.getValue().getRepeatRightUrl()).isEqualTo(current.getRepeatRightUrl());
    }

    private DiaryStickerForm form() {
        DiaryStickerForm form = new DiaryStickerForm();
        form.setName("별빛 테이프");
        form.setCategoryId(3L);
        form.setStickerType(DiaryStickerType.MASKING_TAPE);
        form.setAccessTier(DiaryStickerAccessTier.PREMIUM);
        form.setVisible(true);
        form.setDisplayOrder(5);
        form.setImage(new MockMultipartFile(
                "image", "stars.webp", "image/webp", "RIFFxxxxWEBP".getBytes()));
        return form;
    }

    private DiaryStickerCategoryEntity category(Long id) {
        DiaryStickerCategoryEntity category = new DiaryStickerCategoryEntity();
        category.setId(id);
        category.setCode("decoration");
        category.setName("장식");
        category.setVisible(true);
        return category;
    }

    private DiaryStickerCatalogItem sticker(Long id, DiaryStickerType type) {
        DiaryStickerCatalogItem sticker = new DiaryStickerCatalogItem();
        sticker.setId(id);
        sticker.setCatalogKey("admin-old");
        sticker.setName("기존 스티커");
        sticker.setCategoryId(3L);
        sticker.setStickerType(type);
        sticker.setAccessTier(DiaryStickerAccessTier.FREE);
        sticker.setVisible(true);
        sticker.setDisplayOrder(1);
        sticker.setImageUrl("/uploads/diary-stickers/normal/old.png");
        return sticker;
    }
}
