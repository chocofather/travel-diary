package com.example.travlediary.repository.notice;

import com.example.travlediary.dto.NoticeDetailDto;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.model.Notice;
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
}
