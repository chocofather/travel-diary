package com.example.travlediary.service.info;

import com.example.travlediary.model.AccommodationInfo;
import com.example.travlediary.repository.info.AccommodationInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccommodationInfoService {

    private final AccommodationInfoMapper accommodationInfoMapper;

    public void save(AccommodationInfo info) {
        accommodationInfoMapper.insert(info);
    }

    public AccommodationInfo findByDestinationId(Long destinationId) {
        return accommodationInfoMapper.findByDestinationId(destinationId);
    }

    public void update(AccommodationInfo info) {
        accommodationInfoMapper.update(info);
    }

}
