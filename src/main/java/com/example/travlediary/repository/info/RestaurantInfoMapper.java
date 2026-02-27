package com.example.travlediary.repository.info;

import com.example.travlediary.model.RestaurantInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RestaurantInfoMapper {
    void insert(RestaurantInfo info);

    RestaurantInfo findByDestinationId(Long destinationId);

    void update(RestaurantInfo info);

}
