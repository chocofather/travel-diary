package com.example.travlediary.service.amenity;

import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.amenity.AmenityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 유형별 편의시설 조회는 DestinationType enum 값을 그대로 매핑 조건으로 넘긴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmenityDestinationTypeServiceTest {

    @Mock
    private AmenityMapper amenityMapper;

    @InjectMocks
    private AmenityService amenityService;

    @Test
    void everyDestinationTypeIsQueriedWithItsOwnEnumName() {
        for (DestinationType type : DestinationType.values()) {
            when(amenityMapper.findTranslationsByDestinationTypeAndLang(type.name(), "ko"))
                    .thenReturn(List.of(translation(1, "주차장")));

            assertThat(amenityService.getAmenityTranslationsByDestinationType(type, "ko"))
                    .extracting(AmenityTranslation::getName)
                    .containsExactly("주차장");
            verify(amenityMapper).findTranslationsByDestinationTypeAndLang(type.name(), "ko");
        }
    }

    @Test
    void severalTypesAreMergedWithoutDuplicatedAmenities() {
        when(amenityMapper.findTranslationsByDestinationTypeAndLang("RESTAURANTS", "ko"))
                .thenReturn(List.of(translation(1, "주차장"), translation(2, "포장 가능")));
        when(amenityMapper.findTranslationsByDestinationTypeAndLang("CAFE", "ko"))
                .thenReturn(List.of(translation(1, "주차장"), translation(3, "콘센트")));

        List<AmenityTranslation> merged = amenityService.getAmenityTranslationsByDestinationTypes(
                "ko", DestinationType.RESTAURANTS, DestinationType.CAFE);

        assertThat(merged).extracting(AmenityTranslation::getAmenityId)
                .containsExactly(1, 2, 3);
    }

    @Test
    void missingMappingsSimplyYieldAnEmptyTypeList() {
        when(amenityMapper.findTranslationsByDestinationTypeAndLang("SHOP", "ko"))
                .thenReturn(null);

        assertThat(amenityService.getAmenityTranslationsByDestinationType(DestinationType.SHOP, "ko"))
                .isEmpty();
    }

    @Test
    void theFullAmenityListKeepsItsExistingBehaviour() {
        when(amenityMapper.findTranslationsByLang("ko"))
                .thenReturn(List.of(translation(1, "주차장"), translation(9, "미분류 편의시설")));

        assertThat(amenityService.getAllAmenityTranslations("ko"))
                .extracting(AmenityTranslation::getAmenityId)
                .containsExactly(1, 9);
    }

    private AmenityTranslation translation(int amenityId, String name) {
        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(amenityId);
        translation.setLanguageCode("ko");
        translation.setName(name);
        return translation;
    }
}
