package com.example.travlediary.api;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 에디터 이미지 업로드는 로그인 사용자만 쓸 수 있고, 실제 이미지만 저장한다.
 * (실제 FileUploadService 를 임시 폴더에 붙여 디스크에 무엇이 남는지까지 본다)
 */
@WebMvcTest(EditorImageUploadApi.class)
@Import({SecurityConfig.class, EditorImageUploadApiSecurityTest.TempUploadRoot.class})
class EditorImageUploadApiSecurityTest {

    @TempDir
    static Path uploadRoot;

    @TestConfiguration
    static class TempUploadRoot {
        @Bean
        FileUploadService fileUploadService() {
            return new FileUploadService(uploadRoot.toString());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    /** 비로그인 요청은 업로드되지 않는다. (공개 /api/** 규칙보다 앞선 인증 규칙) */
    @Test
    void guestUploadIsRejectedAndNothingIsStored() throws Exception {
        String before = listing();

        // 토큰까지 갖춰도 인증에서 막힌다 (CSRF 뒤에 인증이 있다는 것까지 본다)
        mockMvc.perform(multipart("/api/upload/editor-image")
                        .file(new MockMultipartFile("image", "a.png", "image/png", png()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(multipart("/api/upload/editor-image")
                        .file(new MockMultipartFile("image", "a.png", "image/png", png()))
                        .header("Accept", "application/json")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        // 같은 클래스의 다른 테스트가 먼저 저장했을 수 있으므로, 이번 요청으로 늘어난 파일이 없는지만 본다.
        assertThat(listing()).isEqualTo(before);
    }

    /** 로그인 사용자의 정상 이미지는 저장되고 판별한 확장자의 URL 을 받는다. */
    @Test
    void memberCanUploadARealImage() throws Exception {
        mockMvc.perform(multipart("/api/upload/editor-image")
                        .file(new MockMultipartFile("image", "photo.jpeg", "image/jpeg", png()))
                        .with(authentication(member()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(
                        org.hamcrest.Matchers.matchesPattern("^/uploads/editor/[0-9a-f-]{36}\\.png$")));
    }

    /** HTML 을 이미지처럼 보내도 400 과 안내 문구만 받고, 경로나 예외는 노출되지 않는다. */
    @Test
    void memberHtmlUploadIsRejectedWithAUserError() throws Exception {
        String before = listing();

        mockMvc.perform(multipart("/api/upload/editor-image")
                        .file(new MockMultipartFile("image", "evil.png", "image/png",
                                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                        .with(authentication(member()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(FileUploadService.UNSUPPORTED_IMAGE_MESSAGE))
                .andExpect(jsonPath("$.url").doesNotExist());

        assertThat(listing()).isEqualTo(before);
    }

    /** 로그인해도 CSRF 토큰이 없으면 업로드되지 않는다. 남의 사이트가 대신 올릴 수 없다. */
    @Test
    void memberUploadWithoutACsrfTokenIsRejectedAndNothingIsStored() throws Exception {
        String before = listing();

        mockMvc.perform(multipart("/api/upload/editor-image")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", png()))
                        .with(authentication(member())))
                .andExpect(status().isForbidden());

        assertThat(listing()).isEqualTo(before);
    }

    private UsernamePasswordAuthenticationToken member() {
        User user = new User();
        user.setId(7L);
        user.setUserPassword("encoded");
        user.setUserRole(UserRole.USER);
        CustomUserDetails principal = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private byte[] png() throws Exception {
        BufferedImage drawn = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(drawn, "png", bytes);
        return bytes.toByteArray();
    }

    private String listing() throws Exception {
        Path editor = uploadRoot.resolve("editor");
        if (Files.notExists(editor)) {
            return "";
        }
        try (var files = Files.list(editor)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList().toString();
        }
    }
}
