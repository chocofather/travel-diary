package com.example.travlediary.service.inquiry;

import com.example.travlediary.dto.InquiryAnswerForm;
import com.example.travlediary.dto.InquiryDetailDto;
import com.example.travlediary.dto.InquiryForm;
import com.example.travlediary.dto.InquiryListItemDto;
import com.example.travlediary.model.Inquiry;
import com.example.travlediary.model.InquiryAnswer;
import com.example.travlediary.model.InquiryStatus;
import com.example.travlediary.model.InquiryType;
import com.example.travlediary.repository.inquiry.InquiryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryMapper inquiryMapper;

    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryService = new InquiryService(inquiryMapper);
    }

    @Test
    void createUsesPrincipalUserAndServerControlledPendingStatus() {
        InquiryForm form = form();
        form.setSubject("  로그인이 되지 않습니다  ");
        form.setContent("  첫 줄\n<script>alert(1)</script>  ");
        doAnswer(invocation -> {
            Inquiry inquiry = invocation.getArgument(0);
            inquiry.setId(10L);
            return 1;
        }).when(inquiryMapper).insertInquiry(any(Inquiry.class));

        assertThat(inquiryService.create(form, 7L)).isEqualTo(10L);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryMapper).insertInquiry(captor.capture());
        Inquiry saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getStatus()).isEqualTo(InquiryStatus.PENDING);
        assertThat(saved.getInquiryType()).isEqualTo(InquiryType.ACCOUNT);
        assertThat(saved.getSubject()).isEqualTo("로그인이 되지 않습니다");
        assertThat(saved.getContent()).isEqualTo("첫 줄\n<script>alert(1)</script>");
    }

    @Test
    void createValidatesTypeSubjectAndContentLength() {
        InquiryForm missingType = form();
        missingType.setInquiryType(null);
        assertFieldError(missingType, "inquiryType");

        InquiryForm blankSubject = form();
        blankSubject.setSubject("  ");
        assertFieldError(blankSubject, "subject");

        InquiryForm longSubject = form();
        longSubject.setSubject("가".repeat(256));
        assertFieldError(longSubject, "subject");

        InquiryForm blankContent = form();
        blankContent.setContent("\n  ");
        assertFieldError(blankContent, "content");

        InquiryForm longContent = form();
        longContent.setContent("가".repeat(InquiryService.MAX_CONTENT_LENGTH + 1));
        assertFieldError(longContent, "content");

        verify(inquiryMapper, never()).insertInquiry(any());
    }

    @Test
    void myReadsPassOwnerToCountListAndDetail() {
        InquiryListItemDto item = new InquiryListItemDto();
        InquiryDetailDto detail = detail();
        when(inquiryMapper.countMyInquiries(7L)).thenReturn(1L);
        when(inquiryMapper.findMyInquiries(7L, 10L, 10)).thenReturn(List.of(item));
        when(inquiryMapper.findMyInquiryById(10L, 7L)).thenReturn(detail);

        assertThat(inquiryService.countMyInquiries(7L)).isEqualTo(1L);
        assertThat(inquiryService.getMyInquiries(7L, 10L, 10)).containsExactly(item);
        assertThat(inquiryService.getMyInquiry(10L, 7L)).isSameAs(detail);
    }

    @Test
    void missingOrOtherUsersInquiryIsIndistinguishableNotFound() {
        when(inquiryMapper.findMyInquiryById(10L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> inquiryService.getMyInquiry(10L, 7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void pendingOwnInquiryLoadsIntoSharedEditForm() {
        Inquiry inquiry = inquiry(InquiryStatus.PENDING);
        inquiry.setInquiryType(InquiryType.TRAVEL_INFO);
        inquiry.setSubject("여행정보 문의");
        inquiry.setContent("기존 문의 내용");
        inquiry.setUserId(7L);
        when(inquiryMapper.findEditableMyInquiry(10L, 7L)).thenReturn(inquiry);

        InquiryForm form = inquiryService.getEditableMyInquiry(10L, 7L);

        assertThat(form.getInquiryType()).isEqualTo(InquiryType.TRAVEL_INFO);
        assertThat(form.getSubject()).isEqualTo("여행정보 문의");
        assertThat(form.getContent()).isEqualTo("기존 문의 내용");
        verify(inquiryMapper).findEditableMyInquiry(10L, 7L);
    }

    @Test
    void answeredOwnInquiryIsAnEditConflictButMissingOrOtherInquiryIsNotFound() {
        when(inquiryMapper.findEditableMyInquiry(10L, 7L)).thenReturn(null);
        InquiryDetailDto answered = detail();
        answered.setStatus(InquiryStatus.ANSWERED);
        when(inquiryMapper.findMyInquiryById(10L, 7L)).thenReturn(answered);

        assertThatThrownBy(() -> inquiryService.getEditableMyInquiry(10L, 7L))
                .isInstanceOf(InquiryEditConflictException.class)
                .hasMessageContaining("답변이 완료");

        when(inquiryMapper.findEditableMyInquiry(99L, 7L)).thenReturn(null);
        when(inquiryMapper.findMyInquiryById(99L, 7L)).thenReturn(null);
        assertThatThrownBy(() -> inquiryService.getEditableMyInquiry(99L, 7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateUsesPrincipalOwnerAndOnlyEditableFieldsWhileKeepingPendingStatusServerControlled() {
        InquiryForm form = form();
        form.setInquiryType(InquiryType.COMMUNITY);
        form.setSubject("  수정 제목  ");
        form.setContent("  수정 내용  ");
        when(inquiryMapper.updatePendingMyInquiry(any(Inquiry.class))).thenReturn(1);

        inquiryService.updatePendingMyInquiry(10L, form, 7L);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryMapper).updatePendingMyInquiry(captor.capture());
        Inquiry updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(10L);
        assertThat(updated.getUserId()).isEqualTo(7L);
        assertThat(updated.getInquiryType()).isEqualTo(InquiryType.COMMUNITY);
        assertThat(updated.getSubject()).isEqualTo("수정 제목");
        assertThat(updated.getContent()).isEqualTo("수정 내용");
        assertThat(updated.getStatus()).isNull();
        assertThat(updated.getCreatedAt()).isNull();
    }

    @Test
    void updateRaceCannotOverwriteInquiryAnsweredAfterEditFormWasOpened() {
        when(inquiryMapper.updatePendingMyInquiry(any(Inquiry.class))).thenReturn(0);
        InquiryDetailDto answered = detail();
        answered.setStatus(InquiryStatus.ANSWERED);
        when(inquiryMapper.findMyInquiryById(10L, 7L)).thenReturn(answered);

        assertThatThrownBy(() -> inquiryService.updatePendingMyInquiry(10L, form(), 7L))
                .isInstanceOf(InquiryEditConflictException.class)
                .hasMessageContaining("답변이 완료");
    }

    @Test
    void unchangedPendingUpdateMayReportZeroWithoutBecomingAnError() {
        when(inquiryMapper.updatePendingMyInquiry(any(Inquiry.class))).thenReturn(0);
        when(inquiryMapper.findMyInquiryById(10L, 7L)).thenReturn(detail());

        inquiryService.updatePendingMyInquiry(10L, form(), 7L);

        verify(inquiryMapper).findMyInquiryById(10L, 7L);
    }

    @Test
    void updateReusesCreateValidationBeforeExecutingSql() {
        InquiryForm invalid = form();
        invalid.setContent(" ");

        assertThatThrownBy(() -> inquiryService.updatePendingMyInquiry(10L, invalid, 7L))
                .isInstanceOfSatisfying(InquiryValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("content"));
        verify(inquiryMapper, never()).updatePendingMyInquiry(any());
    }

    @Test
    void onlyOwnedPendingInquiryCanBeDeleted() {
        when(inquiryMapper.deletePendingMyInquiry(10L, 7L)).thenReturn(1);
        inquiryService.deletePendingMyInquiry(10L, 7L);
        verify(inquiryMapper).deletePendingMyInquiry(10L, 7L);

        when(inquiryMapper.deletePendingMyInquiry(11L, 7L)).thenReturn(0);
        assertThatThrownBy(() -> inquiryService.deletePendingMyInquiry(11L, 7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void firstAnswerUsesAdminPrincipalAndChangesStatusInSameTransactionalMethod() throws Exception {
        Inquiry inquiry = inquiry(InquiryStatus.PENDING);
        when(inquiryMapper.findByIdForUpdate(10L)).thenReturn(inquiry);
        when(inquiryMapper.findAnswerByInquiryId(10L)).thenReturn(null);
        doAnswer(invocation -> {
            InquiryAnswer answer = invocation.getArgument(0);
            answer.setId(20L);
            return 1;
        }).when(inquiryMapper).insertAnswer(any(InquiryAnswer.class));
        when(inquiryMapper.updateInquiryStatus(10L, InquiryStatus.ANSWERED)).thenReturn(1);

        InquiryAnswerForm form = answerForm("  답변입니다.  ");
        inquiryService.saveAnswer(10L, form, 99L);

        ArgumentCaptor<InquiryAnswer> captor = ArgumentCaptor.forClass(InquiryAnswer.class);
        verify(inquiryMapper).insertAnswer(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(99L);
        assertThat(captor.getValue().getInquiryId()).isEqualTo(10L);
        assertThat(captor.getValue().getContent()).isEqualTo("답변입니다.");
        verify(inquiryMapper).updateInquiryStatus(10L, InquiryStatus.ANSWERED);

        Method method = InquiryService.class.getMethod(
                "saveAnswer", Long.class, InquiryAnswerForm.class, Long.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void existingAnswerIsUpdatedWithoutChangingOriginalAuthorOrCreatingRow() {
        Inquiry inquiry = inquiry(InquiryStatus.ANSWERED);
        InquiryAnswer existing = new InquiryAnswer();
        existing.setId(20L);
        existing.setInquiryId(10L);
        existing.setUserId(55L);
        existing.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        when(inquiryMapper.findByIdForUpdate(10L)).thenReturn(inquiry);
        when(inquiryMapper.findAnswerByInquiryId(10L)).thenReturn(existing);
        when(inquiryMapper.updateAnswer(existing)).thenReturn(1);
        when(inquiryMapper.updateInquiryStatus(10L, InquiryStatus.ANSWERED)).thenReturn(1);

        inquiryService.saveAnswer(10L, answerForm("수정 답변"), 99L);

        assertThat(existing.getUserId()).isEqualTo(55L);
        assertThat(existing.getCreatedAt()).isEqualTo(Timestamp.valueOf("2026-08-12 10:00:00"));
        assertThat(existing.getContent()).isEqualTo("수정 답변");
        verify(inquiryMapper, never()).insertAnswer(any());
        verify(inquiryMapper).updateAnswer(existing);
    }

    @Test
    void duplicateAnswerBecomesExpectedValidationFailure() {
        when(inquiryMapper.findByIdForUpdate(10L)).thenReturn(inquiry(InquiryStatus.PENDING));
        when(inquiryMapper.findAnswerByInquiryId(10L)).thenReturn(null);
        when(inquiryMapper.insertAnswer(any()))
                .thenThrow(new DuplicateKeyException("duplicate inquiry_id"));

        assertThatThrownBy(() -> inquiryService.saveAnswer(10L, answerForm("답변"), 99L))
                .isInstanceOf(InquiryValidationException.class)
                .hasMessageContaining("이미 등록");
        verify(inquiryMapper, never()).updateInquiryStatus(any(), any());
    }

    @Test
    void missingAnswerTargetStopsBeforeAnswerWrite() {
        when(inquiryMapper.findByIdForUpdate(99L)).thenReturn(null);

        assertThatThrownBy(() -> inquiryService.saveAnswer(99L, answerForm("답변"), 99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(inquiryMapper, never()).findAnswerByInquiryId(any());
        verify(inquiryMapper, never()).insertAnswer(any());
        verify(inquiryMapper, never()).updateInquiryStatus(any(), any());
    }

    @Test
    void blankOrOversizedAnswerIsRejectedBeforeLocking() {
        assertThatThrownBy(() -> inquiryService.saveAnswer(10L, answerForm("  "), 99L))
                .isInstanceOfSatisfying(InquiryValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("content"));
        assertThatThrownBy(() -> inquiryService.saveAnswer(10L,
                answerForm("가".repeat(InquiryService.MAX_ANSWER_LENGTH + 1)), 99L))
                .isInstanceOf(InquiryValidationException.class);
        verify(inquiryMapper, never()).findByIdForUpdate(any());
    }

    private void assertFieldError(InquiryForm form, String field) {
        assertThatThrownBy(() -> inquiryService.create(form, 7L))
                .isInstanceOfSatisfying(InquiryValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo(field));
    }

    private InquiryForm form() {
        InquiryForm form = new InquiryForm();
        form.setInquiryType(InquiryType.ACCOUNT);
        form.setSubject("로그인이 되지 않습니다");
        form.setContent("문의 내용입니다.");
        return form;
    }

    private InquiryAnswerForm answerForm(String content) {
        InquiryAnswerForm form = new InquiryAnswerForm();
        form.setContent(content);
        return form;
    }

    private Inquiry inquiry(InquiryStatus status) {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(10L);
        inquiry.setStatus(status);
        return inquiry;
    }

    private InquiryDetailDto detail() {
        InquiryDetailDto detail = new InquiryDetailDto();
        detail.setId(10L);
        detail.setStatus(InquiryStatus.PENDING);
        return detail;
    }
}
