package com.example.travlediary.service.info;

import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.repository.info.AttractionInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttractionInfoService {

    private final AttractionInfoMapper attractionInfoMapper;

    public void save(AttractionInfo info) {
        attractionInfoMapper.insert(info);
    }

    public AttractionInfo findByDestinationId(Long destinationId) {
        return attractionInfoMapper.findByDestinationId(destinationId);
    }

    public void update(AttractionInfo info) {
        attractionInfoMapper.update(info);
    }
}
