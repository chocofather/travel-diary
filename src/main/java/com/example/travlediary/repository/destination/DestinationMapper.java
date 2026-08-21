    package com.example.travlediary.repository.destination;

    import com.example.travlediary.model.Destination;
    import com.example.travlediary.model.DestinationCategory;
    import com.example.travlediary.model.DestinationImage;
    import com.example.travlediary.model.DestinationTranslation;
    import org.apache.ibatis.annotations.Mapper;
    import org.apache.ibatis.annotations.Param;

    import java.util.List;

    @Mapper
    public interface DestinationMapper {
        void insertDestination(Destination destination);

        void insertTranslation(DestinationTranslation translation);

        void insertCategory(DestinationCategory category);

        void insertImage(DestinationImage image);

        Destination findById(Long id);

        List<Destination> findDomestic(); // 국내만

        void insertDestinationCategory(@Param("destinationId") Long destinationId,
                                       @Param("categoryId") Long categoryId);

        // 상세페이지
        Destination findDestinationDetail(Long id);

        // 이미지 url 리스트 조회 (nested select 용)
        List<DestinationImage> findImagesByDestinationId(Long destinationId);

        List<Destination> findAllWithRegion();

        DestinationImage findImageById(Long imageId);
        void deleteImageById(Long imageId);
        void clearMainImagesByDestinationId(Long destinationId);
        void setMainImage(Long imageId);
        void updateImageSlide(@Param("imageId") Long imageId,
                              @Param("isSlide") boolean isSlide);
        void updateImageOrder(@Param("imageId") Long imageId,
                              @Param("orderIndex") int orderIndex);

        List<Destination> findByCountryCategoryId(@Param("cityId") Long cityId);

        List<Destination> findByRegionIds(@Param("regionIds") List<Long> regionIds,
                                          @Param("keyword") String keyword,
                                          @Param("chosungPattern") String chosungPattern);

        // 조회수 증가
        void incrementViewCount(Long id);

        List<DestinationTranslation> findTranslationsByDestinationId(Long destinationId);


        void deleteById(Long id); // destinations

        void deleteTranslationsByDestinationId(Long destinationId); // destination_translations

        void deleteImagesByDestinationId(Long destinationId); // destination_images

        void deleteCommentsByDestinationId(Long destinationId); // destination_comments

        void deleteDestinationCategoriesByDestinationId(Long destinationId);

        // 댓글 좋아요 관련
        void incrementCommentLikeCount(@Param("commentId") Long commentId);

        void decrementCommentLikeCount(@Param("commentId") Long commentId);

        // 수정 관련
        void updateDestination(Destination destination); // 여행지 기본 정보 수정

        void updateTranslation(DestinationTranslation translation); // 번역 수정(필요시)

        List<Long> findCategoryIdsByDestinationId(Long destinationId); // 카테고리 ID 리스트

        void deleteDestinationCategory(@Param("destinationId") Long destinationId, @Param("categoryId") Long categoryId); // 카테고리 해제


        // 추천 여행지 (같은 지역, 비슷한 카테고리, 자기자신 제외, 랜덤 5개)
        List<Destination> findSimilarDestinations(
                @Param("regionId") Long regionId,
                @Param("categoryIds") List<Long> categoryIds,
                @Param("exceptId") Long exceptId,
                @Param("limit") int limit
        );

        // 페이징
        List<Destination> findByRegionIdsPaged(@Param("regionIds") List<Long> regionIds,
                                               @Param("offset") int offset,
                                               @Param("size") int size,
                                               @Param("sort") String sort);
        int countByRegionIds(@Param("regionIds") List<Long> regionIds);

        List<Destination> findDomesticPaged(@Param("offset") int offset,
                                            @Param("size") int size,
                                            @Param("sort") String sort);
        int countDomestic();


        // 특정 루트 지역(대륙/국가/도시 등) 아래의 모든 하위 지역 id 목록 반환
        List<Long> findAllRegionIdsUnder(@Param("rootRegionId") Long rootRegionId);

    }
