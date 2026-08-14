package com.example.travlediary.service.category;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountryCategoryRandomScopeTest {

    @Mock
    private CountryCategoryMapper mapper;

    @Test
    void resolvesDomesticCountryAndOverseasContinentsFromHierarchy() {
        CountryCategory asia = category(301L, null);
        CountryCategory korea = category(909L, null);
        CountryCategory japan = category(808L, 301L);
        when(mapper.selectCourseCountries()).thenReturn(List.of(korea, japan));
        when(mapper.findByDepth(1, null)).thenReturn(List.of(asia, korea));

        CountryCategoryService service = new CountryCategoryService(mapper);

        assertThat(service.getDomesticRootIds()).containsExactly(909L);
        assertThat(service.getOverseasRootIds()).containsExactly(301L);
        assertThat(service.getKoreaRootId()).isEqualTo(909L);
    }

    @Test
    void returnsNoDomesticRootWhenTheHierarchyContainsNoRootCountry() {
        CountryCategory asia = category(301L, null);
        CountryCategory japan = category(808L, 301L);
        when(mapper.selectCourseCountries()).thenReturn(List.of(japan));
        when(mapper.findByDepth(1, null)).thenReturn(List.of(asia));

        CountryCategoryService service = new CountryCategoryService(mapper);

        assertThat(service.getDomesticRootIds()).isEmpty();
        assertThat(service.getKoreaRootId()).isNull();
        assertThat(service.getOverseasRootIds()).containsExactly(301L);
    }

    private CountryCategory category(Long id, Long parentId) {
        CountryCategory category = new CountryCategory();
        category.setId(id);
        category.setParentId(parentId);
        return category;
    }
}
