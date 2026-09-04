package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.ActivityInfoTranslationForm;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.model.ActivityInfo;
import com.example.travlediary.model.ActivityInfoTranslation;
import com.example.travlediary.repository.info.ActivityInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ActivityInfoService {

    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();
    /** 관리자 입력 슬롯이 저장할 수 있는 언어. 한국어는 원본 입력이 대신한다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES =
            Set.copyOf(DestinationForm.SUBTYPE_TRANSLATION_LANGUAGES);

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

    /**
     * 공개 상세용. 자유 텍스트(운영 시간·소요 시간·참가비·연령 제한·이용 안내)만 요청 언어로 바꾼다.
     *
     * <p>번역은 이 여행지 것을 한 번에 읽고, 값은 필드마다 따로 고른다.
     * 차례는 요청 언어 → 한국어 → 남은 언어(언어 코드, id 순) → 원문이며,
     * 어디에도 값이 없으면 null 이다. (관광지·식당·숙박 상세정보와 같은 규칙)
     *
     * <p>사전 예약·장비 포함·주차 여부, 연락처, 홈페이지는 원본을 그대로 둔다.
     */
    public ActivityInfo findLocalizedByDestinationId(Long destinationId,
                                                     SupportedLanguage requestedLanguage) {
        ActivityInfo base = activityInfoMapper.findByDestinationId(destinationId);
        if (base == null) {
            return null;
        }

        List<ActivityInfoTranslation> ordered = orderedTranslations(destinationId);
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        ActivityInfoTranslation requested = translationFor(ordered, language.getLanguageTag());
        ActivityInfoTranslation korean = translationFor(
                ordered, SupportedLanguage.KOREAN.getLanguageTag());

        ActivityInfo localized = copyOf(base);
        localized.setOpeningHours(localizedField(
                ActivityInfoTranslation::getOpeningHours, requested, korean, ordered,
                base.getOpeningHours()));
        localized.setRequiredTime(localizedField(
                ActivityInfoTranslation::getRequiredTime, requested, korean, ordered,
                base.getRequiredTime()));
        localized.setAdmissionFee(localizedField(
                ActivityInfoTranslation::getAdmissionFee, requested, korean, ordered,
                base.getAdmissionFee()));
        localized.setAgeLimit(localizedField(
                ActivityInfoTranslation::getAgeLimit, requested, korean, ordered,
                base.getAgeLimit()));
        localized.setGuide(localizedField(
                ActivityInfoTranslation::getGuide, requested, korean, ordered,
                base.getGuide()));
        return localized;
    }

    /**
     * 관리자 저장. 한국어는 원본(activity_info) 값을 그대로 ko 번역에도 맞추고,
     * 나머지 언어는 입력 슬롯 값을 언어별로 저장한다.
     *
     * <p>언어 한 줄이 단위다. 값이 있으면 넣거나 고치고, 그 언어 값이 모두 비면 줄을 지운다.
     * legacy 'zh' 는 쓰지 않는다.
     */
    @Transactional
    public void saveTranslations(Long destinationId,
                                 ActivityInfo koreanBase,
                                 List<ActivityInfoTranslationForm> translationForms) {
        if (destinationId == null) {
            return;
        }

        Map<String, ActivityInfoTranslation> existing = new LinkedHashMap<>();
        for (ActivityInfoTranslation translation : storedTranslations(destinationId)) {
            if (translation.getLanguageCode() != null) {
                existing.putIfAbsent(translation.getLanguageCode(), translation);
            }
        }

        // 한국어는 화면의 원본 입력이 그대로 ko 번역이 된다.
        saveTranslation(destinationId, koreanTranslation(destinationId, koreanBase),
                existing.containsKey(KOREAN_CODE));

        if (translationForms == null) {
            return;
        }
        for (ActivityInfoTranslationForm form : translationForms) {
            if (form == null || form.getLanguageCode() == null
                    || !SUPPORTED_TRANSLATION_CODES.contains(form.getLanguageCode())) {
                // 화면이 정한 슬롯 언어만 저장한다. 임의 언어 코드는 무시한다.
                continue;
            }
            saveTranslation(destinationId, translationOf(destinationId, form),
                    existing.containsKey(form.getLanguageCode()));
        }
    }

    /** 관리자 수정 화면 복원용. 저장된 줄이 있으면 슬롯에 채우고, 없으면 빈 슬롯을 돌려준다. */
    public List<ActivityInfoTranslationForm> getTranslationForms(Long destinationId) {
        List<ActivityInfoTranslationForm> slots =
                DestinationForm.newActivityInfoTranslationSlots();
        if (destinationId == null) {
            return slots;
        }

        for (ActivityInfoTranslation translation : storedTranslations(destinationId)) {
            if (translation.getLanguageCode() == null) {
                continue;
            }
            for (ActivityInfoTranslationForm slot : slots) {
                if (slot.getLanguageCode().equals(translation.getLanguageCode())) {
                    slot.setOpeningHours(translation.getOpeningHours());
                    slot.setRequiredTime(translation.getRequiredTime());
                    slot.setAdmissionFee(translation.getAdmissionFee());
                    slot.setAgeLimit(translation.getAgeLimit());
                    slot.setGuide(translation.getGuide());
                }
            }
        }
        return slots;
    }

    /** 저장된 번역 줄. 조회 실패는 감추지 않고 그대로 올린다. */
    private List<ActivityInfoTranslation> storedTranslations(Long destinationId) {
        List<ActivityInfoTranslation> stored =
                activityInfoMapper.findTranslationsByDestinationId(destinationId);
        if (stored == null) {
            return List.of();
        }
        return stored.stream().filter(translation -> translation != null).toList();
    }

    private List<ActivityInfoTranslation> orderedTranslations(Long destinationId) {
        return storedTranslations(destinationId).stream()
                .sorted(Comparator
                        .comparing(ActivityInfoTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ActivityInfoTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** 값이 하나라도 있으면 INSERT/UPDATE, 모두 비었으면 있던 줄만 지운다. */
    private void saveTranslation(Long destinationId,
                                 ActivityInfoTranslation translation,
                                 boolean exists) {
        if (isEmpty(translation)) {
            if (exists) {
                activityInfoMapper.deleteTranslation(
                        destinationId, translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            activityInfoMapper.updateTranslation(translation);
        } else {
            activityInfoMapper.insertTranslation(translation);
        }
    }

    private ActivityInfoTranslation koreanTranslation(Long destinationId, ActivityInfo base) {
        ActivityInfoTranslation translation = new ActivityInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(KOREAN_CODE);
        if (base != null) {
            translation.setOpeningHours(nonBlank(base.getOpeningHours()));
            translation.setRequiredTime(nonBlank(base.getRequiredTime()));
            translation.setAdmissionFee(nonBlank(base.getAdmissionFee()));
            translation.setAgeLimit(nonBlank(base.getAgeLimit()));
            translation.setGuide(nonBlank(base.getGuide()));
        }
        return translation;
    }

    private ActivityInfoTranslation translationOf(Long destinationId,
                                                  ActivityInfoTranslationForm form) {
        ActivityInfoTranslation translation = new ActivityInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setOpeningHours(nonBlank(form.getOpeningHours()));
        translation.setRequiredTime(nonBlank(form.getRequiredTime()));
        translation.setAdmissionFee(nonBlank(form.getAdmissionFee()));
        translation.setAgeLimit(nonBlank(form.getAgeLimit()));
        translation.setGuide(nonBlank(form.getGuide()));
        return translation;
    }

    private boolean isEmpty(ActivityInfoTranslation translation) {
        return translation.getOpeningHours() == null
                && translation.getRequiredTime() == null
                && translation.getAdmissionFee() == null
                && translation.getAgeLimit() == null
                && translation.getGuide() == null;
    }

    private ActivityInfoTranslation translationFor(List<ActivityInfoTranslation> translations,
                                                   String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    private String localizedField(Function<ActivityInfoTranslation, String> field,
                                  ActivityInfoTranslation requested,
                                  ActivityInfoTranslation korean,
                                  List<ActivityInfoTranslation> ordered,
                                  String baseValue) {
        String requestedValue = value(field, requested);
        if (requestedValue != null) {
            return requestedValue;
        }
        String koreanValue = value(field, korean);
        if (koreanValue != null) {
            return koreanValue;
        }
        String otherValue = ordered.stream()
                .map(translation -> value(field, translation))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
        if (otherValue != null) {
            return otherValue;
        }
        return nonBlank(baseValue);
    }

    private String value(Function<ActivityInfoTranslation, String> field,
                         ActivityInfoTranslation translation) {
        return translation == null ? null : nonBlank(field.apply(translation));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 원본을 바꾸지 않도록 화면용 복사본을 만든다. 언어와 상관없는 값은 그대로 옮긴다. */
    private ActivityInfo copyOf(ActivityInfo source) {
        ActivityInfo copy = new ActivityInfo();
        copy.setDestinationId(source.getDestinationId());
        copy.setOpeningHours(source.getOpeningHours());
        copy.setRequiredTime(source.getRequiredTime());
        copy.setAdmissionFee(source.getAdmissionFee());
        copy.setAgeLimit(source.getAgeLimit());
        copy.setReservation(source.getReservation());
        copy.setEquipmentIncluded(source.getEquipmentIncluded());
        copy.setParkingAvailable(source.getParkingAvailable());
        copy.setContactNumber(source.getContactNumber());
        copy.setHomepageUrl(source.getHomepageUrl());
        copy.setGuide(source.getGuide());
        return copy;
    }
}
