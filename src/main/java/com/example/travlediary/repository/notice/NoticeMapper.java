package com.example.travlediary.repository.notice;

import com.example.travlediary.dto.NoticeDetailDto;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.model.Notice;
import com.example.travlediary.model.NoticeTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoticeMapper {

    List<NoticeListItemDto> findAdminList();

    List<NoticeListItemDto> findPublicList(@Param("offset") long offset,
                                            @Param("limit") int limit);

    long countPublicList();

    int incrementPublicViews(@Param("id") Long id);

    NoticeDetailDto findPublicDetailById(@Param("id") Long id);

    Notice findById(@Param("id") Long id);

    Notice findByIdForUpdate(@Param("id") Long id);

    int insertNotice(Notice notice);

    int updateNotice(Notice notice);

    int deleteNotice(@Param("id") Long id);

    /** 관리자 수정 화면용. 공지 한 건의 번역을 언어 코드 순으로 읽는다. */
    List<NoticeTranslation> findTranslationsByNoticeId(@Param("noticeId") Long noticeId);

    /** 공개 목록용. 여러 공지의 번역을 한 번에 읽어 언어 대체에서 N+1 이 생기지 않게 한다. */
    List<NoticeTranslation> findTranslationsByNoticeIds(@Param("noticeIds") List<Long> noticeIds);

    /** 관리자 저장용. 언어 한 줄이 단위이며 UNIQUE(notice_id, language_code) 를 따른다. */
    int insertTranslation(NoticeTranslation translation);

    int updateTranslation(NoticeTranslation translation);

    int deleteTranslation(@Param("noticeId") Long noticeId,
                          @Param("languageCode") String languageCode);
}
