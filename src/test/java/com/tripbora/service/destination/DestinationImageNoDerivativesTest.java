package com.tripbora.service.destination;

import com.tripbora.dto.SeasonDestinationDto;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 목록·카드 썸네일의 공공누리 제3유형(변경금지) 표시. */
@ExtendWith(MockitoExtension.class)
class DestinationImageNoDerivativesTest {

    private static final String TYPE1 = "/uploads/destinations/type1.jpg";
    private static final String TYPE3 = "/uploads/destinations/type3.jpg";

    @Mock private DestinationMapper destinationMapper;
    @Mock private FileUploadService fileUploadService;

    private DestinationImageService service;

    @BeforeEach
    void setUp() {
        service = new DestinationImageService(destinationMapper, fileUploadService);
    }

    @Test
    void marksOnlyType3ImagesWithOneQueryForTheWholeList() {
        when(destinationMapper.findImageUrlsByLicenseType(any(), eq("KOGL_TYPE_3"))).thenReturn(List.of(TYPE3));
        SeasonDestinationDto type1 = destination(TYPE1);
        SeasonDestinationDto type3 = destination(TYPE3);
        SeasonDestinationDto sameType3 = destination(TYPE3);
        SeasonDestinationDto noImage = destination(null);

        service.markNoDerivatives(new ArrayList<>(java.util.Arrays.asList(type1, type3, null, sameType3, noImage)),
                SeasonDestinationDto::getImageUrl, SeasonDestinationDto::setImageNoDerivatives);

        assertThat(type1.isImageNoDerivatives()).isFalse();
        assertThat(type3.isImageNoDerivatives()).isTrue();
        assertThat(sameType3.isImageNoDerivatives()).isTrue();
        assertThat(noImage.isImageNoDerivatives()).isFalse();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> urls = ArgumentCaptor.forClass(Collection.class);
        verify(destinationMapper).findImageUrlsByLicenseType(urls.capture(), eq("KOGL_TYPE_3"));
        assertThat(urls.getValue()).containsExactly(TYPE1, TYPE3);
    }

    @Test
    void listsWithoutImagesDoNotQuery() {
        service.markNoDerivatives(List.of(destination(null), destination("  ")),
                SeasonDestinationDto::getImageUrl, SeasonDestinationDto::setImageNoDerivatives);
        service.markNoDerivatives(List.of(), SeasonDestinationDto::getImageUrl,
                SeasonDestinationDto::setImageNoDerivatives);
        service.markNoDerivatives(null, SeasonDestinationDto::getImageUrl,
                SeasonDestinationDto::setImageNoDerivatives);

        verify(destinationMapper, never()).findImageUrlsByLicenseType(any(), anyString());
    }

    private SeasonDestinationDto destination(String imageUrl) {
        SeasonDestinationDto destination = new SeasonDestinationDto();
        destination.setImageUrl(imageUrl);
        return destination;
    }
}
