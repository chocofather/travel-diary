package com.example.travlediary.repository.info;

import com.example.travlediary.model.ShopInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopInfoMapper {
    void insert(ShopInfo info);

    ShopInfo findByDestinationId(Long destinationId);

    void update(ShopInfo info);
}
