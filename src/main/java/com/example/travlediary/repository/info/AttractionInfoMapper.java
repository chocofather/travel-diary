package com.example.travlediary.repository.info;

import com.example.travlediary.model.AttractionInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttractionInfoMapper {
    void insert(AttractionInfo info);

    AttractionInfo findByDestinationId(Long destinationId);

    void update(AttractionInfo info);


}
