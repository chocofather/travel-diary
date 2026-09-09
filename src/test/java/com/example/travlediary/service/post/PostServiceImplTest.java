package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.dto.PostEditDto;
import com.example.travlediary.dto.PostUpdateRequest;
import com.example.travlediary.model.PostType;
import com.example.travlediary.model.UserPost;
import com.example.travlediary.repository.post.PostMapper;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostMapper postMapper;

    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostServiceImpl(
                postMapper, new PostContentSanitizer(), new LocalContentLanguageDetector());
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void editLookupDoesNotIncrementViews() {
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));
        PostEditDto edit = new PostEditDto();
        edit.setId(10L);
        edit.setContent("<p>기존 본문</p>");
        when(postMapper.findPostForEdit(10L)).thenReturn(edit);

        assertThat(service.getPostForEdit(10L, 7L).getContent()).isEqualTo("<p>기존 본문</p>");

        verify(postMapper, never()).incrementViews(any());
    }

    @Test
    void editLookupDistinguishesNotFoundAndForbidden() {
        when(postMapper.findActivePost(10L)).thenReturn(null);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getPostForEdit(10L, 7L));

        when(postMapper.findActivePost(11L)).thenReturn(post(11L, 8L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.getPostForEdit(11L, 7L));
    }

    @Test
    void updateUsesOwnerAndSanitizedValidatedFields() {
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));
        when(postMapper.updatePost(10L, 7L, "수정 제목", PostType.TIP, "<p>수정 본문</p>", "ko", "ko"))
                .thenReturn(1);

        service.updatePost(10L, 7L, request("  수정 제목  ", PostType.TIP, "<p>수정 본문</p>"));

        verify(postMapper).updatePost(
                10L, 7L, "수정 제목", PostType.TIP, "<p>수정 본문</p>", "ko", "ko");
        verify(postMapper, never()).insertPostImage(any());
    }

    @Test
    void updateDistinguishesNotFoundAndForbidden() {
        PostUpdateRequest request = request("제목", PostType.QUESTION, "<p>본문</p>");
        when(postMapper.findActivePost(10L)).thenReturn(null);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updatePost(10L, 7L, request));

        when(postMapper.findActivePost(11L)).thenReturn(post(11L, 8L));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.updatePost(11L, 7L, request));
        verify(postMapper, never()).updatePost(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateRejectsBlankAndOversizedInput() {
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));

        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updatePost(10L, 7L, request(" ", PostType.QUESTION, "<p>본문</p>")));
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updatePost(10L, 7L, request("가".repeat(256), PostType.QUESTION, "<p>본문</p>")));
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updatePost(10L, 7L, request("제목", PostType.QUESTION, "<p><br></p>")));
        verify(postMapper, never()).updatePost(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createUsesTheSameValidationPolicy() {
        UserPost post = post(10L, 7L);
        post.setTitle(" ");
        post.setPostType(PostType.QUESTION);
        post.setContent("<p>본문</p>");

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createPost(post, List.of()));
        verify(postMapper, never()).insertPost(any());
    }

    @Test
    void createStoresTitleAndHtmlBodyLanguagesIndependently() {
        UserPost post = post(10L, 7L);
        post.setTitle("The best places for a spring journey");
        post.setPostType(PostType.TIP);
        post.setContent("<p>この旅行先は本当に素晴らしい場所でした。</p>");
        when(postMapper.insertPost(any(UserPost.class))).thenReturn(1);

        service.createPost(post, List.of());

        assertThat(post.getTitleSourceLanguage()).isEqualTo("en");
        assertThat(post.getContentSourceLanguage()).isEqualTo("ja");
    }

    @Test
    void titleOnlyEditRedetectsOnlyTitleLanguage() {
        UserPost existing = post(10L, 7L);
        when(postMapper.findActivePost(10L)).thenReturn(existing);
        when(postMapper.updatePost(10L, 7L,
                "Useful travel advice for everyone", PostType.TIP, "<p>기존 본문</p>", "en", "ko"))
                .thenReturn(1);

        service.updatePost(10L, 7L, request(
                "Useful travel advice for everyone", PostType.TIP, "<p>기존 본문</p>"));

        verify(postMapper).updatePost(10L, 7L,
                "Useful travel advice for everyone", PostType.TIP, "<p>기존 본문</p>", "en", "ko");
    }

    @Test
    void contentOnlyEditRedetectsOnlyContentLanguage() {
        UserPost existing = post(10L, 7L);
        when(postMapper.findActivePost(10L)).thenReturn(existing);
        when(postMapper.updatePost(10L, 7L, "기존 제목", PostType.TIP,
                "<p>This updated article contains clear English sentences.</p>", "ko", "en"))
                .thenReturn(1);

        service.updatePost(10L, 7L, request(
                "기존 제목", PostType.TIP,
                "<p>This updated article contains clear English sentences.</p>"));

        verify(postMapper).updatePost(10L, 7L, "기존 제목", PostType.TIP,
                "<p>This updated article contains clear English sentences.</p>", "ko", "en");
    }

    @Test
    void detailOffersTranslationWhenEitherCertainFieldUsesAnotherLanguage() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        when(postMapper.incrementViews(10L)).thenReturn(1);
        PostDetailDto different = new PostDetailDto();
        different.setContent("<p>한국어 본문입니다.</p>");
        different.setTitleSourceLanguage("en");
        different.setContentSourceLanguage("ko");
        when(postMapper.findPostDetail(10L, null)).thenReturn(different);
        when(postMapper.findPostImages(10L)).thenReturn(List.of());

        assertThat(service.getPostDetail(10L, null).isTranslationAvailable()).isTrue();
    }

    @Test
    void detailHidesTranslationWhenBothFieldsMatchLocaleOrAreUndetermined() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        when(postMapper.incrementViews(10L)).thenReturn(1);
        PostDetailDto same = new PostDetailDto();
        same.setContent("<p>한국어 본문입니다.</p>");
        same.setTitleSourceLanguage("ko");
        same.setContentSourceLanguage("ko");
        PostDetailDto undetermined = new PostDetailDto();
        undetermined.setContent("<p>123 😊</p>");
        undetermined.setTitleSourceLanguage("und");
        undetermined.setContentSourceLanguage("und");
        when(postMapper.findPostDetail(10L, null)).thenReturn(same, undetermined);
        when(postMapper.findPostImages(10L)).thenReturn(List.of());

        assertThat(service.getPostDetail(10L, null).isTranslationAvailable()).isFalse();
        assertThat(service.getPostDetail(10L, null).isTranslationAvailable()).isFalse();
    }

    @Test
    void deleteOnlySoftDeletesOwnedActivePost() {
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));
        when(postMapper.softDeletePost(10L, 7L)).thenReturn(1);

        service.deletePost(10L, 7L);

        verify(postMapper).softDeletePost(10L, 7L);
    }

    @Test
    void detailCalculatesMyPostOnServer() {
        when(postMapper.incrementViews(10L)).thenReturn(1);
        PostDetailDto detail = new PostDetailDto();
        detail.setContent("<p>본문</p>");
        detail.setBookmarked(true);
        when(postMapper.findPostDetail(10L, 7L)).thenReturn(detail);
        when(postMapper.findPostImages(10L)).thenReturn(List.of());
        when(postMapper.findActivePost(10L)).thenReturn(post(10L, 7L));

        PostDetailDto result = service.getPostDetail(10L, 7L);

        assertThat(result.isMyPost()).isTrue();
        assertThat(result.isBookmarked()).isTrue();
    }

    private PostUpdateRequest request(String title, PostType postType, String content) {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle(title);
        request.setPostType(postType);
        request.setContent(content);
        return request;
    }

    private UserPost post(Long id, Long userId) {
        UserPost post = new UserPost();
        post.setId(id);
        post.setUserId(userId);
        post.setTitle("기존 제목");
        post.setContent("<p>기존 본문</p>");
        post.setTitleSourceLanguage("ko");
        post.setContentSourceLanguage("ko");
        return post;
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(expected));
    }
}
