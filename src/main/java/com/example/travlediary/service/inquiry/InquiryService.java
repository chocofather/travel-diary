package com.example.travlediary.service.inquiry;

import com.example.travlediary.dto.InquiryAnswerForm;
import com.example.travlediary.dto.InquiryDetailDto;
import com.example.travlediary.dto.InquiryForm;
import com.example.travlediary.dto.InquiryListItemDto;
import com.example.travlediary.model.Inquiry;
import com.example.travlediary.model.InquiryAnswer;
import com.example.travlediary.model.InquiryStatus;
import com.example.travlediary.repository.inquiry.InquiryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InquiryService {

    public static final int MAX_CONTENT_LENGTH = 5_000;
    public static final int MAX_ANSWER_LENGTH = 5_000;

    private final InquiryMapper inquiryMapper;

    @Transactional
    public Long create(InquiryForm form, Long userId) {
        requireUserId(userId);
        ValidatedInquiry validated = validateInquiry(form);

        Inquiry inquiry = new Inquiry();
        inquiry.setInquiryType(form.getInquiryType());
        inquiry.setSubject(validated.subject());
        inquiry.setContent(validated.content());
        inquiry.setStatus(InquiryStatus.PENDING);
        inquiry.setUserId(userId);

        if (inquiryMapper.insertInquiry(inquiry) != 1 || inquiry.getId() == null) {
            throw new IllegalStateException("1:1 문의 저장에 실패했습니다.");
        }
        return inquiry.getId();
    }

    @Transactional(readOnly = true)
    public long countMyInquiries(Long userId) {
        requireUserId(userId);
        return inquiryMapper.countMyInquiries(userId);
    }

    @Transactional(readOnly = true)
    public List<InquiryListItemDto> getMyInquiries(Long userId, long offset, int limit) {
        requireUserId(userId);
        return inquiryMapper.findMyInquiries(userId, offset, limit);
    }

    @Transactional(readOnly = true)
    public InquiryDetailDto getMyInquiry(Long id, Long userId) {
        requireUserId(userId);
        InquiryDetailDto detail = inquiryMapper.findMyInquiryById(id, userId);
        if (detail == null) {
            throw notFound();
        }
        return detail;
    }

    @Transactional(readOnly = true)
    public InquiryForm getEditableMyInquiry(Long id, Long userId) {
        requireUserId(userId);
        Inquiry inquiry = inquiryMapper.findEditableMyInquiry(id, userId);
        if (inquiry == null) {
            throwEditUnavailable(id, userId);
        }

        InquiryForm form = new InquiryForm();
        form.setInquiryType(inquiry.getInquiryType());
        form.setSubject(inquiry.getSubject());
        form.setContent(inquiry.getContent());
        return form;
    }

    @Transactional
    public void updatePendingMyInquiry(Long id, InquiryForm form, Long userId) {
        requireUserId(userId);
        ValidatedInquiry validated = validateInquiry(form);

        Inquiry inquiry = new Inquiry();
        inquiry.setId(id);
        inquiry.setInquiryType(form.getInquiryType());
        inquiry.setSubject(validated.subject());
        inquiry.setContent(validated.content());
        inquiry.setUserId(userId);

        if (inquiryMapper.updatePendingMyInquiry(inquiry) == 0) {
            InquiryDetailDto current = inquiryMapper.findMyInquiryById(id, userId);
            if (current == null) {
                throw notFound();
            }
            if (!current.isPending()) {
                throw editConflict(current);
            }
            // MySQL 설정에 따라 값이 바뀌지 않은 UPDATE가 0건으로 보고될 수 있다.
        }
    }

    @Transactional
    public void deletePendingMyInquiry(Long id, Long userId) {
        requireUserId(userId);
        if (inquiryMapper.deletePendingMyInquiry(id, userId) != 1) {
            throw notFound();
        }
    }

    @Transactional(readOnly = true)
    public long countAdminInquiries(InquiryStatus status) {
        return inquiryMapper.countAdminInquiries(status);
    }

    @Transactional(readOnly = true)
    public List<InquiryListItemDto> getAdminInquiries(InquiryStatus status,
                                                       long offset,
                                                       int limit) {
        return inquiryMapper.findAdminInquiries(status, offset, limit);
    }

    @Transactional(readOnly = true)
    public InquiryDetailDto getAdminInquiry(Long id) {
        InquiryDetailDto detail = inquiryMapper.findAdminInquiryById(id);
        if (detail == null) {
            throw notFound();
        }
        return detail;
    }

    @Transactional
    public void saveAnswer(Long inquiryId, InquiryAnswerForm form, Long adminUserId) {
        requireUserId(adminUserId);
        String content = validateAnswer(form);
        Inquiry inquiry = inquiryMapper.findByIdForUpdate(inquiryId);
        if (inquiry == null) {
            throw notFound();
        }
        if (inquiry.getStatus() != InquiryStatus.PENDING
                && inquiry.getStatus() != InquiryStatus.ANSWERED) {
            throw new InquiryValidationException(null, "현재 상태에서는 답변을 등록할 수 없습니다.");
        }

        InquiryAnswer answer = inquiryMapper.findAnswerByInquiryId(inquiryId);
        if (answer == null) {
            answer = new InquiryAnswer();
            answer.setContent(content);
            answer.setInquiryId(inquiryId);
            answer.setUserId(adminUserId);
            try {
                if (inquiryMapper.insertAnswer(answer) != 1) {
                    throw new IllegalStateException("관리자 답변 저장에 실패했습니다.");
                }
            } catch (DuplicateKeyException exception) {
                throw new InquiryValidationException(null,
                        "답변이 이미 등록되었습니다. 페이지를 새로고침한 뒤 수정해 주세요.");
            }
        } else {
            answer.setContent(content);
            if (inquiryMapper.updateAnswer(answer) != 1) {
                throw new IllegalStateException("관리자 답변 수정에 실패했습니다.");
            }
        }

        if (inquiryMapper.updateInquiryStatus(inquiryId, InquiryStatus.ANSWERED) != 1) {
            throw notFound();
        }
    }

    private ValidatedInquiry validateInquiry(InquiryForm form) {
        if (form == null) {
            throw new InquiryValidationException(null, "문의 내용을 입력해 주세요.");
        }
        if (form.getInquiryType() == null) {
            throw new InquiryValidationException("inquiryType", "문의 유형을 선택해 주세요.");
        }

        String subject = form.getSubject() == null ? "" : form.getSubject().strip();
        form.setSubject(subject);
        if (subject.isEmpty()) {
            throw new InquiryValidationException("subject", "제목을 입력해 주세요.");
        }
        if (subject.length() > 255) {
            throw new InquiryValidationException("subject", "제목은 255자 이하로 입력해 주세요.");
        }

        String content = form.getContent() == null ? "" : form.getContent().strip();
        form.setContent(content);
        if (content.isEmpty()) {
            throw new InquiryValidationException("content", "문의 내용을 입력해 주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new InquiryValidationException("content",
                    "문의 내용은 " + MAX_CONTENT_LENGTH + "자 이하로 입력해 주세요.");
        }
        return new ValidatedInquiry(subject, content);
    }

    private String validateAnswer(InquiryAnswerForm form) {
        String content = form == null || form.getContent() == null
                ? ""
                : form.getContent().strip();
        if (form != null) {
            form.setContent(content);
        }
        if (content.isEmpty()) {
            throw new InquiryValidationException("content", "답변 내용을 입력해 주세요.");
        }
        if (content.length() > MAX_ANSWER_LENGTH) {
            throw new InquiryValidationException("content",
                    "답변은 " + MAX_ANSWER_LENGTH + "자 이하로 입력해 주세요.");
        }
        return content;
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 정보를 확인할 수 없습니다.");
        }
    }

    private void throwEditUnavailable(Long id, Long userId) {
        InquiryDetailDto current = inquiryMapper.findMyInquiryById(id, userId);
        if (current == null) {
            throw notFound();
        }
        throw editConflict(current);
    }

    private InquiryEditConflictException editConflict(InquiryDetailDto inquiry) {
        String message = inquiry.isAnswered()
                ? "답변이 완료된 문의는 수정할 수 없습니다."
                : "답변대기 상태의 문의만 수정할 수 있습니다.";
        return new InquiryEditConflictException(message);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "1:1 문의를 찾을 수 없습니다.");
    }

    private record ValidatedInquiry(String subject, String content) {
    }
}
