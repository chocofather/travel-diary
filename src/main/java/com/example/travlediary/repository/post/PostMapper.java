package com.example.travlediary.repository.post;

import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.dto.PostEditDto;
import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.PostType;
import com.example.travlediary.model.UserPost;
import com.example.travlediary.model.translation.UserPostLanguageBackfillRow;
import com.example.travlediary.model.translation.UserPostTranslationSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper {

    int incrementViews(@Param("postId") Long postId);

    PostDetailDto findPostDetail(@Param("postId") Long postId,
                                 @Param("currentUserId") Long currentUserId);

    UserPost findActivePost(@Param("postId") Long postId);

    UserPost findActivePostForUpdate(@Param("postId") Long postId);

    UserPostTranslationSource findVisibleTranslationSource(@Param("postId") Long postId);

    UserPostTranslationSource findVisibleTranslationSourceForUpdate(@Param("postId") Long postId);

    int correctTitleSourceLanguage(@Param("postId") Long postId,
                                   @Param("sourceLanguage") String sourceLanguage,
                                   @Param("updatedAt") java.sql.Timestamp updatedAt);

    int correctContentSourceLanguage(@Param("postId") Long postId,
                                     @Param("sourceLanguage") String sourceLanguage,
                                     @Param("updatedAt") java.sql.Timestamp updatedAt);

    List<UserPostLanguageBackfillRow> findUndeterminedPostLanguagesAfter(
            @Param("afterId") Long afterId,
            @Param("limit") int limit);

    int updateTitleLanguageIfUndetermined(@Param("postId") Long postId,
                                          @Param("sourceLanguage") String sourceLanguage,
                                          @Param("title") String title,
                                          @Param("updatedAt") java.sql.Timestamp updatedAt);

    int updateContentLanguageIfUndetermined(@Param("postId") Long postId,
                                            @Param("sourceLanguage") String sourceLanguage,
                                            @Param("content") String content,
                                            @Param("updatedAt") java.sql.Timestamp updatedAt);

    PostEditDto findPostForEdit(@Param("postId") Long postId);

    List<PostImage> findPostImages(@Param("postId") Long postId);

    // 게시글 저장
    int insertPost(UserPost post);

    // 이미지 저장
    int insertPostImage(PostImage image);

    int updatePost(@Param("postId") Long postId,
                   @Param("userId") Long userId,
                   @Param("title") String title,
                   @Param("postType") PostType postType,
                   @Param("content") String content,
                   @Param("titleSourceLanguage") String titleSourceLanguage,
                   @Param("contentSourceLanguage") String contentSourceLanguage);

    int softDeletePost(@Param("postId") Long postId,
                       @Param("userId") Long userId);
}
