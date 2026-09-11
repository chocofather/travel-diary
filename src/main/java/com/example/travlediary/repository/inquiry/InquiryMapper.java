package com.example.travlediary.repository.inquiry;

import com.example.travlediary.dto.InquiryDetailDto;
import com.example.travlediary.dto.InquiryListItemDto;
import com.example.travlediary.model.Inquiry;
import com.example.travlediary.model.InquiryAnswer;
import com.example.travlediary.model.InquiryStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InquiryMapper {

    int insertInquiry(Inquiry inquiry);

    long countMyInquiries(@Param("userId") Long userId);

    List<InquiryListItemDto> findMyInquiries(@Param("userId") Long userId,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit);

    InquiryDetailDto findMyInquiryById(@Param("id") Long id,
                                       @Param("userId") Long userId);

    Inquiry findEditableMyInquiry(@Param("id") Long id,
                                  @Param("userId") Long userId);

    int updatePendingMyInquiry(Inquiry inquiry);

    int deletePendingMyInquiry(@Param("id") Long id,
                               @Param("userId") Long userId);

    long countAdminInquiries(@Param("status") InquiryStatus status);

    List<InquiryListItemDto> findAdminInquiries(@Param("status") InquiryStatus status,
                                                 @Param("offset") long offset,
                                                 @Param("limit") int limit);

    InquiryDetailDto findAdminInquiryById(@Param("id") Long id);

    Inquiry findByIdForUpdate(@Param("id") Long id);

    InquiryAnswer findAnswerByInquiryId(@Param("inquiryId") Long inquiryId);

    int insertAnswer(InquiryAnswer answer);

    int updateAnswer(InquiryAnswer answer);

    int updateInquiryStatus(@Param("id") Long id,
                            @Param("status") InquiryStatus status);

    /* ---------- 보존기간(3년) 만료 정리 ---------- */

    /**
     * 보존기간이 끝난 문의 번호. 한 번에 다 읽지 않고 batch 크기만큼만 가져온다.
     * 처리가 끝난 문의만 대상이고, 기준 시점은 답변 등록 시각(취소 문의는 마지막 상태 변경 시각)이다.
     *
     * @param retentionCutoff 이 시각 이전에 처리가 끝난 문의가 정리 대상이다
     */
    List<Long> findExpiredInquiryIds(@Param("retentionCutoff") LocalDateTime retentionCutoff,
                                     @Param("limit") int limit);

    /**
     * 삭제 직전의 잠금 조회. 관리자가 그 사이 상태를 되돌렸을 수 있으므로
     * 행을 잠근 뒤 보존기간 조건을 전부 다시 확인한다. 대상이 아니면 null 이다.
     */
    Inquiry findExpiredInquiryByIdForUpdate(
            @Param("id") Long id,
            @Param("retentionCutoff") LocalDateTime retentionCutoff);

    /** 문의 삭제. 답변(inquiry_answers)은 FK ON DELETE CASCADE 로 함께 지워진다. */
    int deleteInquiryById(@Param("id") Long id);
}
