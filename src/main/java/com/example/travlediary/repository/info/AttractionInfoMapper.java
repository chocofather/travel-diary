package com.example.travlediary.repository.info;

import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.AttractionInfoTranslation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AttractionInfoMapper {
    void insert(AttractionInfo info);

    AttractionInfo findByDestinationId(Long destinationId);

    List<AttractionInfoTranslation> findTranslationsByDestinationId(Long destinationId);

    void update(AttractionInfo info);


}
