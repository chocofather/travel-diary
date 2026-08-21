package com.example.travlediary.service.amenity;

import com.example.travlediary.model.AmenityDestinationType;
import com.example.travlediary.repository.amenity.AmenityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 편의시설별 "적용 가능한 여행지 유형" 태그.
 * 화면 필터가 이름이 아니라 이 매핑으로만 동작하도록 서버에서 만들어 내려준다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmenityDestinationTypeTagTest {

    @Mock
    private AmenityMapper amenityMapper;

    @InjectMocks
    private AmenityService amenityService;

    @Test
    void tagsAreGroupedPerAmenity() {
        when(amenityMapper.findAmenityDestinationTypes()).thenReturn(List.of(
                mapping(1, "ATTRACTION"),
                mapping(1, "RESTAURANTS"),
                mapping(1, "CAFE"),
                mapping(2, "ACCOMMODATION")));

        Map<Integer, String> tags = amenityService.getAmenityDestinationTypeTags();

        assertThat(tags.get(1)).isEqualTo("ATTRACTION RESTAURANTS CAFE");
        assertThat(tags.get(2)).isEqualTo("ACCOMMODATION");
    }

    @Test
    void anAmenityWithoutMappingSimplyHasNoTag() {
        when(amenityMapper.findAmenityDestinationTypes())
                .thenReturn(List.of(mapping(1, "SHOP")));

        Map<Integer, String> tags = amenityService.getAmenityDestinationTypeTags();

        // 매핑이 없는 편의시설은 태그가 없어 [전체] 에서만 보인다
        assertThat(tags.get(9)).isNull();
        assertThat(tags).containsOnlyKeys(1);
    }

    @Test
    void emptyOrMissingMappingRowsAreSafe() {
        when(amenityMapper.findAmenityDestinationTypes()).thenReturn(null);

        assertThat(amenityService.getAmenityDestinationTypeTags()).isEmpty();
    }

    private AmenityDestinationType mapping(int amenityId, String destinationType) {
        AmenityDestinationType mapping = new AmenityDestinationType();
        mapping.setAmenityId(amenityId);
        mapping.setDestinationType(destinationType);
        return mapping;
    }
}
