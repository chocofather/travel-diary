package com.example.travlediary.service.travelinfo;

import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FestivalInfoService {

    private static final String ADMIN_SOURCE_TYPE = "ADMIN";

    private final FestivalInfoMapper festivalInfoMapper;

    @Transactional(readOnly = true)
    public FestivalInfo getByInfoId(Long infoId) {
        return infoId == null ? null : festivalInfoMapper.findByInfoId(infoId);
    }

    @Transactional
    public void create(FestivalInfo festivalInfo) {
        applyDefaultSourceType(festivalInfo);
        festivalInfoMapper.insert(festivalInfo);
    }

    @Transactional
    public void update(FestivalInfo festivalInfo) {
        applyDefaultSourceType(festivalInfo);
        festivalInfoMapper.update(festivalInfo);
    }

    @Transactional
    public void deleteByInfoId(Long infoId) {
        if (infoId != null) {
            festivalInfoMapper.deleteByInfoId(infoId);
        }
    }

    private void applyDefaultSourceType(FestivalInfo festivalInfo) {
        if (festivalInfo == null) {
            throw new IllegalArgumentException("축제 상세정보를 입력해 주세요.");
        }
        String sourceType = festivalInfo.getSourceType();
        festivalInfo.setSourceType(sourceType == null || sourceType.isBlank()
                ? ADMIN_SOURCE_TYPE
                : sourceType.strip());
    }
}
