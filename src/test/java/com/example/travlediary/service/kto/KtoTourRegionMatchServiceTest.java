package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoTourRegionMatchResponse;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KtoTourRegionMatchServiceTest {

    @Mock
    private CountryCategoryService countryCategoryService;

    @Test
    void matchesSeoulAndJongnoByWalkingTheParentTree() {
        CountryCategory korea = region(900L, "대한민국", null, 1);
        CountryCategory seoul = region(410L, "서울", 900L, 2);
        CountryCategory jongno = region(815L, "종로구", 410L, 4);
        stubTree(korea, List.of(seoul), List.of(jongno), List.of());

        KtoTourRegionMatchResponse result = service().match("서울특별시 종로구 율곡로 99");

        assertThat(result.matched()).isTrue();
        assertThat(result.path()).extracting(KtoTourRegionMatchResponse.RegionPathItem::id)
                .containsExactly(900L, 410L, 815L);
        assertThat(result.path()).extracting(KtoTourRegionMatchResponse.RegionPathItem::regionName)
                .containsExactly("대한민국", "서울", "종로구");
        assertThat(result.deepestRegionId()).isEqualTo(815L);
    }

    @Test
    void supportsVariableDepthWithoutReadingDepthNumbers() {
        CountryCategory korea = region(100L, "대한민국", null, 91);
        CountryCategory gyeonggi = region(220L, "경기", 100L, 7);
        CountryCategory suwon = region(330L, "수원시", 220L, 43);
        CountryCategory paldal = region(440L, "팔달구", 330L, 3);
        when(countryCategoryService.getKoreaRootId()).thenReturn(korea.getId());
        when(countryCategoryService.getById(korea.getId())).thenReturn(korea);
        when(countryCategoryService.getRegionsByParentId(korea.getId())).thenReturn(List.of(gyeonggi));
        when(countryCategoryService.getRegionsByParentId(gyeonggi.getId())).thenReturn(List.of(suwon));
        when(countryCategoryService.getRegionsByParentId(suwon.getId())).thenReturn(List.of(paldal));
        when(countryCategoryService.getRegionsByParentId(paldal.getId())).thenReturn(List.of());

        KtoTourRegionMatchResponse result = service().match("경기도 수원시 팔달구 정조로 825");

        assertThat(result.matched()).isTrue();
        assertThat(result.path()).extracting(KtoTourRegionMatchResponse.RegionPathItem::id)
                .containsExactly(100L, 220L, 330L, 440L);
        assertThat(result.deepestRegionId()).isEqualTo(440L);
    }

    @Test
    void matchesAnotherMetropolitanAlias() {
        CountryCategory korea = region(1L, "대한민국", null, 1);
        CountryCategory busan = region(2L, "부산", 1L, 2);
        CountryCategory haeundae = region(3L, "해운대구", 2L, 4);
        stubTree(korea, List.of(busan), List.of(haeundae), List.of());

        KtoTourRegionMatchResponse result = service().match("부산광역시 해운대구 해운대해변로 264");

        assertThat(result.matched()).isTrue();
        assertThat(result.path()).extracting(KtoTourRegionMatchResponse.RegionPathItem::regionName)
                .containsExactly("대한민국", "부산", "해운대구");
    }

    @Test
    void failsClosedWhenAChildTokenDoesNotHaveOneExactMatch() {
        CountryCategory korea = region(1L, "대한민국", null, 1);
        CountryCategory seoulA = region(2L, "서울", 1L, 2);
        CountryCategory seoulB = region(3L, "서울", 1L, 8);
        when(countryCategoryService.getKoreaRootId()).thenReturn(korea.getId());
        when(countryCategoryService.getById(korea.getId())).thenReturn(korea);
        when(countryCategoryService.getRegionsByParentId(korea.getId()))
                .thenReturn(List.of(seoulA, seoulB));

        KtoTourRegionMatchResponse ambiguous = service().match("서울특별시 종로구 율곡로 99");
        KtoTourRegionMatchResponse missing = service().match("존재하지않는도시 어딘가로 1");
        KtoTourRegionMatchResponse blank = service().match("   ");

        assertThat(ambiguous.matched()).isFalse();
        assertThat(ambiguous.path()).isEmpty();
        assertThat(ambiguous.deepestRegionId()).isNull();
        assertThat(missing.matched()).isFalse();
        assertThat(blank.matched()).isFalse();
    }

    @Test
    void failsWhenChildrenExistButTheNextAddressTokenDoesNotMatch() {
        CountryCategory korea = region(10L, "대한민국", null, 1);
        CountryCategory seoul = region(20L, "서울", 10L, 2);
        CountryCategory jongno = region(30L, "종로구", 20L, 4);
        when(countryCategoryService.getKoreaRootId()).thenReturn(korea.getId());
        when(countryCategoryService.getById(korea.getId())).thenReturn(korea);
        when(countryCategoryService.getRegionsByParentId(korea.getId())).thenReturn(List.of(seoul));
        when(countryCategoryService.getRegionsByParentId(seoul.getId())).thenReturn(List.of(jongno));

        KtoTourRegionMatchResponse result = service().match("서울특별시 중구 세종대로 110");

        assertThat(result.matched()).isFalse();
        assertThat(result.path()).isEmpty();
    }

    private KtoTourRegionMatchService service() {
        return new KtoTourRegionMatchService(countryCategoryService);
    }

    private void stubTree(CountryCategory root,
                          List<CountryCategory> rootChildren,
                          List<CountryCategory> secondChildren,
                          List<CountryCategory> thirdChildren) {
        when(countryCategoryService.getKoreaRootId()).thenReturn(root.getId());
        when(countryCategoryService.getById(root.getId())).thenReturn(root);
        when(countryCategoryService.getRegionsByParentId(root.getId())).thenReturn(rootChildren);
        when(countryCategoryService.getRegionsByParentId(rootChildren.get(0).getId()))
                .thenReturn(secondChildren);
        when(countryCategoryService.getRegionsByParentId(secondChildren.get(0).getId()))
                .thenReturn(thirdChildren);
    }

    private CountryCategory region(Long id, String name, Long parentId, int depth) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setParentId(parentId);
        region.setDepth(depth);
        return region;
    }
}
