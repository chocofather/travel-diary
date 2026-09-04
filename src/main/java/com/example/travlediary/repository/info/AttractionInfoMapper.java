package com.example.travlediary.repository.info;

import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.AttractionInfoTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttractionInfoMapper {
    void insert(AttractionInfo info);

    AttractionInfo findByDestinationId(Long destinationId);

    List<AttractionInfoTranslation> findTranslationsByDestinationId(Long destinationId);

    /** 관리자 저장용. 언어 한 줄씩 넣고/고치고/지운다. */
    void insertTranslation(AttractionInfoTranslation translation);

    void updateTranslation(AttractionInfoTranslation translation);

    void deleteTranslation(@Param("destinationId") Long destinationId,
                           @Param("languageCode") String languageCode);

    void update(AttractionInfo info);


}
