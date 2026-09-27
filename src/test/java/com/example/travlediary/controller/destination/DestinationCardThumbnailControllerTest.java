package com.example.travlediary.controller.destination;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.DestinationCardThumbnailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비로그인도 받을 수 있고, 주소가 바뀌지 않는 파일이라 원본보다 길게 캐시한다. */
@WebMvcTest(DestinationCardThumbnailController.class)
@Import(SecurityConfig.class)
class DestinationCardThumbnailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DestinationCardThumbnailService thumbnailService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @TempDir
    Path directory;

    @Test
    void guestsReceiveTheThumbnailWithALongPublicCacheAndUnknownAddressesAreNotFound() throws Exception {
        Path file = Files.write(directory.resolve("photo.jpg"), new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2});
        when(thumbnailService.resolve(anyString(), anyInt(), anyString())).thenAnswer(invocation ->
                "photo.jpg".equals(invocation.getArgument(2)) && (int) invocation.getArgument(1) == 480
                        ? Optional.of(file) : Optional.empty());
        when(thumbnailService.isThumbnailFile(file)).thenReturn(true);

        mockMvc.perform(get("/thumbnails/destinations/v2/480/photo.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", "max-age=2592000, public"))
                .andExpect(content().bytes(Files.readAllBytes(file)));
        mockMvc.perform(get("/thumbnails/destinations/v2/480/missing.jpg"))
                .andExpect(status().isNotFound());
    }

    /** 만들지 못해 원본을 대신 보낸 응답은 오래 캐시하지 않는다(다음에 썸네일을 받게). */
    @Test
    void anOriginalServedInsteadOfTheThumbnailIsNotCachedForLong() throws Exception {
        Path original = Files.write(directory.resolve("original.jpg"), new byte[]{(byte) 0xFF, (byte) 0xD8, 3, 4});
        when(thumbnailService.resolve(anyString(), anyInt(), anyString())).thenReturn(Optional.of(original));
        when(thumbnailService.isThumbnailFile(original)).thenReturn(false);

        mockMvc.perform(get("/thumbnails/destinations/v2/480/original.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().bytes(Files.readAllBytes(original)));
    }
}
