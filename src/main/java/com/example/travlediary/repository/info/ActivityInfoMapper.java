package com.example.travlediary.repository.info;


import com.example.travlediary.model.ActivityInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActivityInfoMapper {

    void insert(ActivityInfo info);

    ActivityInfo findByDestinationId(Long destinationId);

    void update(ActivityInfo info);

}
