package com.example.travlediary.service.info;

import com.example.travlediary.model.RestaurantInfo;
import com.example.travlediary.repository.info.RestaurantInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RestaurantInfoService {

    private final RestaurantInfoMapper restaurantInfoMapper;

    public void save(RestaurantInfo info) {
        restaurantInfoMapper.insert(info);
    }

    public RestaurantInfo findByDestinationId(Long destinationId) {
        return restaurantInfoMapper.findByDestinationId(destinationId);
    }

    public void update(RestaurantInfo info) {
        restaurantInfoMapper.update(info);
    }

}
