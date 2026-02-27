package com.example.travlediary.service.info;

import com.example.travlediary.model.ShopInfo;
import com.example.travlediary.repository.info.ShopInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShopInfoService {

    private final ShopInfoMapper shopInfoMapper;

    public void save(ShopInfo info) {
        shopInfoMapper.insert(info);
    }

    public ShopInfo findByDestinationId(Long destinationId) {
        return shopInfoMapper.findByDestinationId(destinationId);
    }

    public void update(ShopInfo info) {
        shopInfoMapper.update(info);
    }
}
