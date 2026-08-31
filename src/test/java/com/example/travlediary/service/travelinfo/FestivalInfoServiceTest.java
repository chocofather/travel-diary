package com.example.travlediary.service.travelinfo;

import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FestivalInfoServiceTest {

    @Mock
    private FestivalInfoMapper festivalInfoMapper;

    private FestivalInfoService festivalInfoService;

    @BeforeEach
    void setUp() {
        festivalInfoService = new FestivalInfoService(festivalInfoMapper);
    }

    @Test
    void getByInfoIdReturnsMapperResult() {
        FestivalInfo festivalInfo = festivalInfo(10L, "KTO_TOURAPI");
        when(festivalInfoMapper.findByInfoId(10L)).thenReturn(festivalInfo);

        assertThat(festivalInfoService.getByInfoId(10L)).isSameAs(festivalInfo);
    }

    @Test
    void createDefaultsMissingSourceTypeToAdminBeforeInsert() {
        FestivalInfo festivalInfo = festivalInfo(10L, null);

        festivalInfoService.create(festivalInfo);

        ArgumentCaptor<FestivalInfo> captor = ArgumentCaptor.forClass(FestivalInfo.class);
        verify(festivalInfoMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("ADMIN");
    }

    @Test
    void updateDefaultsBlankSourceTypeToAdminBeforeUpdate() {
        FestivalInfo festivalInfo = festivalInfo(10L, "  ");

        festivalInfoService.update(festivalInfo);

        ArgumentCaptor<FestivalInfo> captor = ArgumentCaptor.forClass(FestivalInfo.class);
        verify(festivalInfoMapper).update(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("ADMIN");
    }

    @Test
    void deleteByInfoIdDelegatesToMapper() {
        festivalInfoService.deleteByInfoId(10L);

        verify(festivalInfoMapper).deleteByInfoId(10L);
    }

    private FestivalInfo festivalInfo(Long infoId, String sourceType) {
        FestivalInfo festivalInfo = new FestivalInfo();
        festivalInfo.setInfoId(infoId);
        festivalInfo.setSourceType(sourceType);
        festivalInfo.setExternalContentId("12345");
        return festivalInfo;
    }
}
