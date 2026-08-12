package com.example.travlediary.repository.inquiry;

import com.example.travlediary.dto.InquiryDetailDto;
import com.example.travlediary.dto.InquiryListItemDto;
import com.example.travlediary.model.Inquiry;
import com.example.travlediary.model.InquiryAnswer;
import com.example.travlediary.model.InquiryStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
