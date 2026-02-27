package com.example.travlediary.service.info;

import com.example.travlediary.model.ActivityInfo;
import com.example.travlediary.repository.info.ActivityInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityInfoService {

    private final ActivityInfoMapper activityInfoMapper;

    public void save(ActivityInfo info) {
        activityInfoMapper.insert(info);
    }

    public ActivityInfo findByDestinationId(Long destinationId) {
        return activityInfoMapper.findByDestinationId(destinationId);
    }

    public void update(ActivityInfo info) {
        activityInfoMapper.update(info);
    }
}
