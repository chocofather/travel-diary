package com.tripbora.repository.destination;

import com.tripbora.model.DestinationTranslationSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DestinationTranslationSourceMapper {
    void insert(DestinationTranslationSource source);
    DestinationTranslationSource findByTranslationId(Long translationId);
    void updateModified(@Param("translationId") Long translationId,
                        @Param("contentModified") boolean contentModified);
    void deleteByTranslationId(Long translationId);
}
