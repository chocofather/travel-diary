package com.example.travlediary.service.inquiry;

import com.example.travlediary.model.Inquiry;
import com.example.travlediary.model.InquiryStatus;
import com.example.travlediary.repository.inquiry.InquiryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 문의 한 건의 삭제 트랜잭션.
 * 목록을 읽은 뒤 상태가 바뀌었을 수 있으므로, 잠그고 조건을 다시 확인한 뒤에만 지운다.
 */
@ExtendWith(MockitoExtension.class)
class InquiryRetentionTransactionServiceTest {

    private static final Long INQUIRY_ID = 3L;
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2023, 9, 12, 4, 40);

    @Mock
    private InquiryMapper inquiryMapper;

    private InquiryRetentionTransactionService service() {
        return new InquiryRetentionTransactionService(inquiryMapper);
    }

    @Test
    void anExpiredInquiryIsLockedAndThenDeleted() {
        when(inquiryMapper.findExpiredInquiryByIdForUpdate(INQUIRY_ID, CUTOFF))
                .thenReturn(answeredInquiry());
        when(inquiryMapper.deleteInquiryById(INQUIRY_ID)).thenReturn(1);

        assertThat(service().purgeOne(INQUIRY_ID, CUTOFF)).isTrue();

        InOrder order = inOrder(inquiryMapper);
        order.verify(inquiryMapper).findExpiredInquiryByIdForUpdate(INQUIRY_ID, CUTOFF);
        order.verify(inquiryMapper).deleteInquiryById(INQUIRY_ID);
        // 답변은 FK ON DELETE CASCADE 로 함께 지워진다. 따로 지우지 않는다.
        verifyNoMoreInteractions(inquiryMapper);
    }

    /** 관리자가 상태를 되돌렸거나 이미 지워졌다면 아무것도 하지 않는다. */
    @Test
    void anInquiryThatNoLongerMatchesIsLeftAlone() {
        when(inquiryMapper.findExpiredInquiryByIdForUpdate(INQUIRY_ID, CUTOFF)).thenReturn(null);

        assertThat(service().purgeOne(INQUIRY_ID, CUTOFF)).isFalse();

        verify(inquiryMapper, never()).deleteInquiryById(anyLong());
    }

    /** 삭제가 반영되지 않으면 트랜잭션을 되돌린다. 배치가 그 건만 실패로 처리한다. */
    @Test
    void aDeleteThatChangedNothingFailsTheTransaction() {
        when(inquiryMapper.findExpiredInquiryByIdForUpdate(INQUIRY_ID, CUTOFF))
                .thenReturn(answeredInquiry());
        when(inquiryMapper.deleteInquiryById(INQUIRY_ID)).thenReturn(0);

        assertThatThrownBy(() -> service().purgeOne(INQUIRY_ID, CUTOFF))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingArgumentsDoNothing() {
        assertThat(service().purgeOne(null, CUTOFF)).isFalse();
        assertThat(service().purgeOne(INQUIRY_ID, null)).isFalse();

        verifyNoMoreInteractions(inquiryMapper);
    }

    private Inquiry answeredInquiry() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(INQUIRY_ID);
        inquiry.setStatus(InquiryStatus.ANSWERED);
        inquiry.setUserId(7L);
        return inquiry;
    }
}
