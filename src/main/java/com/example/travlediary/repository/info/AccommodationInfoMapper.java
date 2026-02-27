package com.example.travlediary.repository.info;

import com.example.travlediary.model.AccommodationInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccommodationInfoMapper {
    void insert(AccommodationInfo info);

    AccommodationInfo findByDestinationId(Long destinationId);

    void update(AccommodationInfo info);



}
