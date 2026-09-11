package com.example.travlediary.service.inquiry;

import com.example.travlediary.model.Inquiry;
import com.example.travlediary.repository.inquiry.InquiryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보존기간이 끝난 문의 한 건의 삭제를 한 트랜잭션에서 끝낸다.
 *
 * <p>목록을 읽은 시점과 지우는 시점 사이에 관리자가 상태를 되돌렸을 수 있으므로,
 * 행을 잠그고 보존기간 조건을 전부 다시 확인한 뒤에만 지운다.
 * 답변(inquiry_answers)은 FK ON DELETE CASCADE 로 같은 트랜잭션에서 함께 사라진다.
 *
 * <p>문의에는 첨부파일 구조가 없어 지울 파일이 없다. 그래서 회원 파기와 달리
 * 후처리 task 를 남기지 않고 DB 삭제만으로 끝난다.
 */
@Service
@RequiredArgsConstructor
public class InquiryRetentionTransactionService {

    private final InquiryMapper inquiryMapper;

    /**
     * @param retentionCutoff 이 시각 이전에 처리가 끝난 문의만 지운다
     * @return 실제로 지웠으면 true, 더 이상 대상이 아니면 false
     */
    @Transactional
    public boolean purgeOne(Long inquiryId, LocalDateTime retentionCutoff) {
        if (inquiryId == null || retentionCutoff == null) {
            return false;
        }

        Inquiry target = inquiryMapper.findExpiredInquiryByIdForUpdate(inquiryId, retentionCutoff);
        if (target == null) {
            return false;
        }

        if (inquiryMapper.deleteInquiryById(inquiryId) != 1) {
            throw new IllegalStateException("문의를 삭제하지 못했습니다. inquiryId=" + inquiryId);
        }
        return true;
    }
}
