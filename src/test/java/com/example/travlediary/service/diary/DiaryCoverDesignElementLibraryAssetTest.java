package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryCoverDesignElementLibraryAssetTest {

    @Mock private DiaryCoverDesignService designService;
    @Mock private DiaryCoverDesignElementMapper elementMapper;

    private DiaryCoverDesignElementService service;

    @BeforeEach
    void setUp() {
        service = new DiaryCoverDesignElementServiceImpl(
                designService, elementMapper, null, new DiaryLabelFontCatalog());
    }

    @Test
    void sharedPhotoAssetsUseTheControlledEndpointInTheEditor() {
        DiaryCoverDesign owned = new DiaryCoverDesign();
        owned.setId(41L);
        when(designService.getMyDesign(41L, 7L)).thenReturn(owned);
        DiaryCoverDesignElement shared = photo(101L, null, 701L);
        DiaryCoverDesignElement empty = photo(102L, null, null);
        DiaryCoverDesignElement personal = photo(103L, "/uploads/mine.jpg", null);
        when(elementMapper.findAllByDesignId(41L))
                .thenReturn(List.of(shared, empty, personal));

        List<DiaryCoverDesignElement> elements = service.getElements(41L, 7L);

        assertThat(elements).extracting(DiaryCoverDesignElement::getImageUrl)
                .containsExactly(
                        "/diaries/cover-library/assets/701", null, "/uploads/mine.jpg");
    }

    @Test
    void sharedPhotoAssetsUseTheControlledEndpointInTheDesignListBatch() {
        DiaryCoverDesignElement shared = photo(101L, null, 701L);
        when(elementMapper.findAllByDesignIds(List.of(41L), 7L))
                .thenReturn(List.of(shared));

        Map<Long, List<DiaryCoverDesignElement>> elements =
                service.getElementsByDesign(List.of(41L), 7L);

        assertThat(elements.get(41L).get(0).getImageUrl())
                .isEqualTo("/diaries/cover-library/assets/701");
    }

    private DiaryCoverDesignElement photo(Long id, String imageUrl, Long assetId) {
        DiaryCoverDesignElement element = new DiaryCoverDesignElement();
        element.setId(id);
        element.setDesignId(41L);
        element.setElementType("PHOTO");
        element.setImageUrl(imageUrl);
        element.setLibraryPhotoAssetId(assetId);
        return element;
    }
}
