package com.example.travlediary.controller.destination;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.Destination;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 삭제됐거나 존재하지 않는 여행지 상세 요청은 NPE 500 이 아니라 404 여야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationDetailNotFoundTest {

    @Mock
    private DestinationService destinationService;
    @Mock
    private DestinationImageService destinationImageService;
    @Mock
    private CountryCategoryService countryCategoryService;
    @Mock
    private ReferenceNameLocalizationService referenceNameLocalizationService;
    @Mock
    private DestinationCommentService destinationCommentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DestinationController controller = new DestinationController(
                destinationService,
                destinationImageService,
                countryCategoryService,
                destinationCommentService,
                referenceNameLocalizationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(anonymousPrincipalResolver())
                .build();
    }

    /** 비로그인 방문자: @AuthenticationPrincipal 은 null 로 들어온다. */
    private HandlerMethodArgumentResolver anonymousPrincipalResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return null;
            }
        };
    }

    @Test
    void deletedOrUnknownDestinationAnswersNotFound() throws Exception {
        when(destinationService.getDestinationDetailWithInfo(eq(404L), any(SupportedLanguage.class)))
                .thenReturn(null);

        mockMvc.perform(get("/destinations/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownDestinationIsNotCountedAsAView() throws Exception {
        when(destinationService.getDestinationDetailWithInfo(eq(404L), any(SupportedLanguage.class)))
                .thenReturn(null);

        mockMvc.perform(get("/destinations/404"))
                .andExpect(status().isNotFound());

        verify(destinationService, never()).incrementViewCount(404L);
    }

    @Test
    void existingDestinationStillRendersTheDetailPageAndCountsTheView() throws Exception {
        when(destinationService.getDestinationDetailWithInfo(eq(7L), any(SupportedLanguage.class)))
                .thenReturn(detailDto());
        when(countryCategoryService.getById(101L)).thenReturn(region(101L, "종로구", 10L));
        when(countryCategoryService.getById(10L)).thenReturn(region(10L, "서울", null));
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(10L));
        when(destinationService.getSimilarDestinations(7L, 4)).thenReturn(List.of());
        when(destinationService.convertToDtoWithBookmark(List.of(), null)).thenReturn(List.of());

        var result = mockMvc.perform(get("/destinations/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("destination/detail"))
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel().get("regionName")).isEqualTo("종로구");
        verify(destinationService).incrementViewCount(7L);
    }

    private DestinationDetailDto detailDto() {
        Destination destination = new Destination();
        destination.setId(7L);
        destination.setRegionId(101L);
        destination.setDescription("설명");
        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);
        dto.setImages(List.of());
        return dto;
    }

    private CountryCategory region(Long id, String name, Long parentId) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setParentId(parentId);
        region.setCode("KR-11");
        return region;
    }
}
