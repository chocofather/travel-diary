package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FestivalInfoTranslationForm;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.FestivalInfoTranslation;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FestivalInfoService {

    private static final String ADMIN_SOURCE_TYPE = "ADMIN";
    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();

    /** 번역 슬롯 언어. 한국어는 base 입력이 대신하므로 여기에서 뺀다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES = SupportedLanguage.all().stream()
            .filter(language -> language != SupportedLanguage.KOREAN)
            .map(SupportedLanguage::getLanguageTag)
            .collect(Collectors.toUnmodifiableSet());

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

    /**
     * 관리자 수정 화면 복원용. 저장된 줄이 있으면 슬롯에 채우고, 없으면 빈 슬롯을 돌려준다.
     *
     * <p>슬롯은 언어 코드로 찾아 채운다. 자리 번호나 조회 순서에 뜻을 두지 않는다.
     */
    @Transactional(readOnly = true)
    public List<FestivalInfoTranslationForm> getTranslationForms(Long infoId) {
        List<FestivalInfoTranslationForm> slots =
                FestivalInfoTranslationForm.newTranslationSlots();
        if (infoId == null) {
            return slots;
        }

        Map<String, FestivalInfoTranslationForm> slotsByLanguage = new LinkedHashMap<>();
        for (FestivalInfoTranslationForm slot : slots) {
            slotsByLanguage.putIfAbsent(slot.getLanguageCode(), slot);
        }

        List<FestivalInfoTranslation> stored = festivalInfoMapper.findTranslationsByInfoId(infoId);
        if (stored != null) {
            for (FestivalInfoTranslation translation : stored) {
                if (translation == null || translation.getLanguageCode() == null) {
                    continue;
                }
                FestivalInfoTranslationForm slot =
                        slotsByLanguage.get(translation.getLanguageCode());
                if (slot == null) {
                    // 슬롯에 없는 언어가 남아 있어도 화면에는 그리지 않는다.
                    continue;
                }
                slot.setEventPlace(formText(translation.getEventPlace()));
                slot.setAddress(formText(translation.getAddress()));
                slot.setPlayTime(formText(translation.getPlayTime()));
                slot.setUseTime(formText(translation.getUseTime()));
                slot.setSponsor1(formText(translation.getSponsor1()));
                slot.setSponsor2(formText(translation.getSponsor2()));
            }
        }
        return slots;
    }

    /**
     * 행사 상세정보 번역을 언어 한 줄씩 저장한다.
     *
     * <p>한국어는 화면의 행사 상세정보 입력이 그대로 ko 줄이 된다. 그래서 번역 입력에 ko 슬롯이
     * 섞여 들어와도 쓰지 않는다 — base 를 번역 입력으로 덮어쓰는 길은 두지 않는다.
     *
     * <p>나머지 언어는 여섯 칸 중 하나라도 값이 있으면 남기고, 전부 비면 그 언어 줄만 지운다.
     * 여섯 칸이 모두 빈 한국어도 같은 규칙을 따른다. (ko 백필이 값 없는 축제를 건너뛴 것과 같다)
     */
    @Transactional
    public void saveTranslations(Long infoId,
                                 FestivalInfo base,
                                 List<FestivalInfoTranslationForm> translationForms) {
        if (infoId == null) {
            return;
        }

        // 기존 줄은 한 번만 읽고 언어 코드로 찾아 쓴다.
        Map<String, FestivalInfoTranslation> existing = new LinkedHashMap<>();
        List<FestivalInfoTranslation> stored = festivalInfoMapper.findTranslationsByInfoId(infoId);
        if (stored != null) {
            for (FestivalInfoTranslation translation : stored) {
                if (translation != null && translation.getLanguageCode() != null) {
                    existing.putIfAbsent(translation.getLanguageCode(), translation);
                }
            }
        }

        saveTranslation(koreanTranslation(infoId, base), existing.containsKey(KOREAN_CODE));

        if (translationForms == null) {
            return;
        }
        Set<String> handledLanguages = new HashSet<>();
        for (FestivalInfoTranslationForm form : translationForms) {
            if (form == null || form.getLanguageCode() == null) {
                continue;
            }
            String languageCode = form.getLanguageCode();
            if (!SUPPORTED_TRANSLATION_CODES.contains(languageCode)) {
                // 화면이 정한 슬롯 언어만 저장한다. ko 슬롯과 임의 언어 코드는 무시한다.
                continue;
            }
            if (!handledLanguages.add(languageCode)) {
                // 같은 언어가 두 번 들어오면 앞의 값만 쓴다. (UNIQUE 충돌을 만들지 않는다)
                continue;
            }
            saveTranslation(translationOf(infoId, form), existing.containsKey(languageCode));
        }
    }

    /** 여섯 칸이 모두 비면 그 언어 줄을 남기지 않는다. */
    private void saveTranslation(FestivalInfoTranslation translation, boolean exists) {
        if (isEmpty(translation)) {
            if (exists) {
                festivalInfoMapper.deleteTranslation(
                        translation.getInfoId(), translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            festivalInfoMapper.updateTranslation(translation);
        } else {
            festivalInfoMapper.insertTranslation(translation);
        }
    }

    private FestivalInfoTranslation koreanTranslation(Long infoId, FestivalInfo base) {
        FestivalInfoTranslation translation = new FestivalInfoTranslation();
        translation.setInfoId(infoId);
        translation.setLanguageCode(KOREAN_CODE);
        if (base != null) {
            translation.setEventPlace(text(base.getEventPlace()));
            translation.setAddress(text(base.getAddress()));
            translation.setPlayTime(text(base.getPlayTime()));
            translation.setUseTime(text(base.getUseTime()));
            translation.setSponsor1(text(base.getSponsor1()));
            translation.setSponsor2(text(base.getSponsor2()));
        }
        return translation;
    }

    private FestivalInfoTranslation translationOf(Long infoId, FestivalInfoTranslationForm form) {
        FestivalInfoTranslation translation = new FestivalInfoTranslation();
        translation.setInfoId(infoId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setEventPlace(text(form.getEventPlace()));
        translation.setAddress(text(form.getAddress()));
        translation.setPlayTime(text(form.getPlayTime()));
        translation.setUseTime(text(form.getUseTime()));
        translation.setSponsor1(text(form.getSponsor1()));
        translation.setSponsor2(text(form.getSponsor2()));
        return translation;
    }

    private boolean isEmpty(FestivalInfoTranslation translation) {
        return translation.getEventPlace() == null
                && translation.getAddress() == null
                && translation.getPlayTime() == null
                && translation.getUseTime() == null
                && translation.getSponsor1() == null
                && translation.getSponsor2() == null;
    }

    /** 공백만 있는 칸은 값이 없는 것으로 본다. */
    private String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 화면 슬롯은 빈 칸을 빈 문자열로 들고 있는다. */
    private String formText(String value) {
        return value == null ? "" : value;
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
