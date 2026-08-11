package com.example.travlediary.service.notice;

import com.example.travlediary.dto.NoticeDetailDto;
import com.example.travlediary.dto.NoticeForm;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.model.Notice;
import com.example.travlediary.repository.notice.NoticeMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeMapper noticeMapper;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeMapper, new PostContentSanitizer());
    }

    @Test
    void createUsesAuthenticatedAdminIdAndSanitizesQuillHtml() {
        NoticeForm form = form();
        form.setContent("<p onclick=\"alert(1)\"><strong>안내</strong></p><script>alert(1)</script>");
        doAnswer(invocation -> {
            Notice notice = invocation.getArgument(0);
            notice.setId(10L);
            return 1;
        }).when(noticeMapper).insertNotice(any(Notice.class));

        assertThat(noticeService.create(form, 7L)).isEqualTo(10L);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insertNotice(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().isPinned()).isTrue();
        assertThat(captor.getValue().getContent())
                .contains("<strong>안내</strong>")
                .doesNotContain("onclick", "script");
    }

    @Test
    void updateKeepsOriginalUserAndViews() {
        Notice existing = notice(10L);
        existing.setUserId(3L);
        existing.setViews(25);
        when(noticeMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(noticeMapper.updateNotice(existing)).thenReturn(1);

        NoticeForm form = form();
        form.setTitle("수정 공지");
        noticeService.update(10L, form);

        assertThat(existing.getUserId()).isEqualTo(3L);
        assertThat(existing.getViews()).isEqualTo(25);
        assertThat(existing.getTitle()).isEqualTo("수정 공지");
    }

    @Test
    void publicDetailIncrementsOnceThenSanitizesContent() {
        NoticeDetailDto detail = new NoticeDetailDto();
        detail.setId(10L);
        detail.setContent("<p onclick=\"bad()\">공지 본문</p><script>bad()</script>");
        when(noticeMapper.incrementPublicViews(10L)).thenReturn(1);
        when(noticeMapper.findPublicDetailById(10L)).thenReturn(detail);

        NoticeDetailDto result = noticeService.getPublicDetail(10L);

        assertThat(result.getContent()).isEqualTo("<p>공지 본문</p>");
        verify(noticeMapper).incrementPublicViews(10L);
        verify(noticeMapper).findPublicDetailById(10L);
    }

    @Test
    void missingPublicNoticeReturnsNotFoundWithoutSelect() {
        when(noticeMapper.incrementPublicViews(99L)).thenReturn(0);

        assertThatThrownBy(() -> noticeService.getPublicDetail(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(noticeMapper, never()).findPublicDetailById(any());
    }

    @Test
    void listAndAdminReadsNeverIncrementViews() {
        NoticeListItemDto item = new NoticeListItemDto();
        when(noticeMapper.findPublicList(0L, 10)).thenReturn(List.of(item));
        when(noticeMapper.findAdminList()).thenReturn(List.of(item));
        when(noticeMapper.countPublicList()).thenReturn(1L);

        assertThat(noticeService.getPublicList(0L, 10)).containsExactly(item);
        assertThat(noticeService.getAdminList()).containsExactly(item);
        assertThat(noticeService.countPublicList()).isEqualTo(1L);
        verify(noticeMapper, never()).incrementPublicViews(any());
    }

    @Test
    void blankContentIsRejectedAfterSanitizing() {
        NoticeForm form = form();
        form.setContent("<p><br></p><script>alert(1)</script>");

        assertThatThrownBy(() -> noticeService.create(form, 7L))
                .isInstanceOfSatisfying(NoticeValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("content"));
        verify(noticeMapper, never()).insertNotice(any());
    }

    private NoticeForm form() {
        NoticeForm form = new NoticeForm();
        form.setTitle("서비스 점검 안내");
        form.setContent("<p>공지 본문</p>");
        form.setPinned(true);
        return form;
    }

    private Notice notice(Long id) {
        Notice notice = new Notice();
        notice.setId(id);
        notice.setTitle("기존 공지");
        notice.setContent("<p>기존 본문</p>");
        return notice;
    }
}
