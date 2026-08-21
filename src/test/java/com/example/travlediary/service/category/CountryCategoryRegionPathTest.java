package com.example.travlediary.service.category;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountryCategoryRegionPathTest {

    @Mock
    private CountryCategoryMapper mapper;

    private CountryCategoryService service;

    @BeforeEach
    void setUp() {
        service = new CountryCategoryService(mapper);
    }

    @Test
    void regionPathIsReturnedFromRootToSelectedRegion() {
        CountryCategory korea = region(7L, "대한민국", null);
        CountryCategory seoul = region(38L, "서울", 7L);
        CountryCategory jongno = region(235L, "종로구", 38L);
        when(mapper.selectById(235L)).thenReturn(jongno);
        when(mapper.selectById(38L)).thenReturn(seoul);
        when(mapper.selectById(7L)).thenReturn(korea);

        assertThat(service.getRegionPath(235L))
                .extracting(CountryCategory::getId)
                .containsExactly(7L, 38L, 235L);
    }

    @Test
    void regionPathIsEmptyWhenRegionIsMissing() {
        assertThat(service.getRegionPath(null)).isEmpty();

        when(mapper.selectById(999L)).thenReturn(null);
        assertThat(service.getRegionPath(999L)).isEmpty();
    }

    @Test
    void regionPathIsEmptyWhenParentChainLoops() {
        CountryCategory first = region(11L, "A", 12L);
        CountryCategory second = region(12L, "B", 11L);
        lenient().when(mapper.selectById(11L)).thenReturn(first);
        lenient().when(mapper.selectById(12L)).thenReturn(second);

        assertThat(service.getRegionPath(11L)).isEmpty();
    }

    private CountryCategory region(Long id, String regionName, Long parentId) {
        CountryCategory category = new CountryCategory();
        category.setId(id);
        category.setRegionName(regionName);
        category.setParentId(parentId);
        return category;
    }
}
