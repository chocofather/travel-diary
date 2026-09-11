package com.example.travlediary.service.inquiry;

import com.example.travlediary.repository.inquiry.InquiryMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보존기간이 끝난 문의를 모아 한 건씩 정리한다. 회원 파기 배치와 같은 구조다.
 *
 * <p>여기에는 트랜잭션을 걸지 않는다. 문의 한 건의 경계는
 * {@link InquiryRetentionTransactionService#purgeOne}이 갖고, 이 클래스가 별도 빈을 거쳐
 * 호출하므로 Spring 프록시를 정상적으로 통과한다.
 *
 * <p>한 건의 실패는 그 건의 트랜잭션만 되돌리고 다음 문의 처리를 막지 않는다.
 * 실패한 문의는 그대로 남아 다음 실행에서 다시 선택된다.
 */
@Service
@RequiredArgsConstructor
public class InquiryRetentionBatchService {

    /** 한 번 실행에서 처리할 최대 문의 수. 남은 문의는 다음 주기가 이어받는다. */
    static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(InquiryRetentionBatchService.class);

    private final InquiryMapper inquiryMapper;
    private final InquiryRetentionTransactionService inquiryRetentionTransactionService;

    /**
     * @param currentTime 만료 판정 기준 시각
     * @return 실제로 지운 문의 수
     */
    public int purgeExpiredInquiries(LocalDateTime currentTime) {
        if (currentTime == null) {
            return 0;
        }

        LocalDateTime retentionCutoff = InquiryRetentionPolicy.retentionCutoff(currentTime);
        List<Long> expiredIds = inquiryMapper.findExpiredInquiryIds(retentionCutoff, BATCH_SIZE);
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }

        int purged = 0;
        int failed = 0;
        for (Long inquiryId : expiredIds) {
            try {
                if (inquiryRetentionTransactionService.purgeOne(inquiryId, retentionCutoff)) {
                    purged++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.error("Inquiry retention purge failed, the inquiry stays: inquiryId={}, exceptionType={}",
                        inquiryId, exception.getClass().getSimpleName(), exception);
            }
        }

        log.info("Inquiry retention batch completed: due={}, purged={}, failed={}, retentionCutoff={}",
                expiredIds.size(), purged, failed, retentionCutoff);
        return purged;
    }
}
