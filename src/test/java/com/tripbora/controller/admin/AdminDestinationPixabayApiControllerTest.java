package com.tripbora.controller.admin;

import com.tripbora.service.destination.DestinationPixabayImageManagementService;
import com.tripbora.service.destination.DestinationPixabayImageManagementService.Photo;
import com.tripbora.service.destination.DestinationPixabayImageManagementService.PhotoPage;
import com.tripbora.service.pixabay.PixabayApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDestinationPixabayApiControllerTest {

    private static final String URL = "/admin/api/destinations/10/pixabay/photos";

    @Mock private DestinationPixabayImageManagementService pixabayService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminDestinationPixabayApiController(pixabayService)).build();
    }

    @Test
    void returnsTheRequestedSliceWithRegisteredStateButNoDownloadUrls() throws Exception {
        when(pixabayService.search(10L, "Seoul", 30)).thenReturn(new PhotoPage("Seoul", 30, 120,
                List.of(new Photo(195893, "https://pixabay.com/photos/seoul-195893/",
                        "https://pixabay.com/get/w_640.jpg", 4000, 3000, "Hans", "seoul", true)), 50, 20));

        mockMvc.perform(get(URL).param("query", "Seoul").param("offset", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextOffset").value(50))
                .andExpect(jsonPath("$.photos[0].id").value(195893))
                .andExpect(jsonPath("$.photos[0].registered").value(true))
                .andExpect(jsonPath("$.photos[0].width").value(4000))
                .andExpect(jsonPath("$.photos[0].user").value("Hans"))
                .andExpect(jsonPath("$.photos[0].largeImageUrl").doesNotExist());
    }

    @Test
    void pixabayFailuresBecomeReadableErrorsWithMatchingStatus() throws Exception {
        when(pixabayService.search(10L, "Seoul", 0))
                .thenThrow(PixabayApiException.notConfigured())
                .thenThrow(PixabayApiException.rateLimited())
                .thenThrow(new PixabayApiException(PixabayApiException.Reason.TIMEOUT, "Pixabay 응답 시간이 초과되었습니다."))
                .thenThrow(new PixabayApiException(PixabayApiException.Reason.UPSTREAM, "Pixabay 서버 오류"))
                .thenThrow(new IllegalArgumentException("검색어를 입력해 주세요."));

        mockMvc.perform(get(URL).param("query", "Seoul"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Pixabay API Key가 설정되지 않았습니다."));
        mockMvc.perform(get(URL).param("query", "Seoul"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Pixabay 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."));
        mockMvc.perform(get(URL).param("query", "Seoul")).andExpect(status().isGatewayTimeout());
        mockMvc.perform(get(URL).param("query", "Seoul")).andExpect(status().isBadGateway());
        mockMvc.perform(get(URL).param("query", "Seoul"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어를 입력해 주세요."));
    }
}
