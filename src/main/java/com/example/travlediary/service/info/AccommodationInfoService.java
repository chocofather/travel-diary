package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.AccommodationInfoTranslationForm;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.model.AccommodationInfo;
import com.example.travlediary.model.AccommodationInfoTranslation;
import com.example.travlediary.repository.info.AccommodationInfoMapper;
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
public class AccommodationInfoService {

    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();
    /** 관리자 입력 슬롯이 저장할 수 있는 언어. 한국어는 원본 입력이 대신한다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES =
            Set.copyOf(DestinationForm.SUBTYPE_TRANSLATION_LANGUAGES);

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

    /**
     * 공개 상세용. 자유 텍스트(객실 유형·기타 안내)만 요청 언어로 바꾼다.
     *
     * <p>번역은 이 여행지 것을 한 번에 읽고, 값은 필드마다 따로 고른다.
     * 차례는 요청 언어 → 한국어 → 남은 언어(언어 코드, id 순) → 원문이며,
     * 어디에도 값이 없으면 null 이다. (관광지·식당 상세정보와 같은 규칙)
     *
     * <p>체크인/체크아웃 시각, 객실 수, 등급, 여부 값, 연락처, 홈페이지는 원본을 그대로 둔다.
     */
    public AccommodationInfo findLocalizedByDestinationId(Long destinationId,
                                                          SupportedLanguage requestedLanguage) {
        AccommodationInfo base = accommodationInfoMapper.findByDestinationId(destinationId);
        if (base == null) {
            return null;
        }

        List<AccommodationInfoTranslation> ordered = orderedTranslations(destinationId);
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        AccommodationInfoTranslation requested = translationFor(
                ordered, language.getLanguageTag());
        AccommodationInfoTranslation korean = translationFor(
                ordered, SupportedLanguage.KOREAN.getLanguageTag());

        AccommodationInfo localized = copyOf(base);
        localized.setRoomType(localizedField(
                AccommodationInfoTranslation::getRoomType, requested, korean, ordered,
                base.getRoomType()));
        localized.setEtc(localizedField(
                AccommodationInfoTranslation::getEtc, requested, korean, ordered,
                base.getEtc()));
        return localized;
    }

    /**
     * 관리자 저장. 한국어는 원본(accommodation_info) 값을 그대로 ko 번역에도 맞추고,
     * 나머지 언어는 입력 슬롯 값을 언어별로 저장한다.
     *
     * <p>언어 한 줄이 단위다. 값이 있으면 넣거나 고치고, 그 언어 값이 모두 비면 줄을 지운다.
     * legacy 'zh' 는 쓰지 않는다.
     */
    @Transactional
    public void saveTranslations(Long destinationId,
                                 AccommodationInfo koreanBase,
                                 List<AccommodationInfoTranslationForm> translationForms) {
        if (destinationId == null) {
            return;
        }

        Map<String, AccommodationInfoTranslation> existing = new LinkedHashMap<>();
        for (AccommodationInfoTranslation translation : storedTranslations(destinationId)) {
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
        for (AccommodationInfoTranslationForm form : translationForms) {
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
    public List<AccommodationInfoTranslationForm> getTranslationForms(Long destinationId) {
        List<AccommodationInfoTranslationForm> slots =
                DestinationForm.newAccommodationInfoTranslationSlots();
        if (destinationId == null) {
            return slots;
        }

        for (AccommodationInfoTranslation translation : storedTranslations(destinationId)) {
            if (translation.getLanguageCode() == null) {
                continue;
            }
            for (AccommodationInfoTranslationForm slot : slots) {
                if (slot.getLanguageCode().equals(translation.getLanguageCode())) {
                    slot.setRoomType(translation.getRoomType());
                    slot.setEtc(translation.getEtc());
                }
            }
        }
        return slots;
    }

    /** 저장된 번역 줄. 조회 실패는 감추지 않고 그대로 올린다. */
    private List<AccommodationInfoTranslation> storedTranslations(Long destinationId) {
        List<AccommodationInfoTranslation> stored =
                accommodationInfoMapper.findTranslationsByDestinationId(destinationId);
        if (stored == null) {
            return List.of();
        }
        return stored.stream().filter(translation -> translation != null).toList();
    }

    private List<AccommodationInfoTranslation> orderedTranslations(Long destinationId) {
        return storedTranslations(destinationId).stream()
                .sorted(Comparator
                        .comparing(AccommodationInfoTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AccommodationInfoTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** 값이 하나라도 있으면 INSERT/UPDATE, 모두 비었으면 있던 줄만 지운다. */
    private void saveTranslation(Long destinationId,
                                 AccommodationInfoTranslation translation,
                                 boolean exists) {
        if (isEmpty(translation)) {
            if (exists) {
                accommodationInfoMapper.deleteTranslation(
                        destinationId, translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            accommodationInfoMapper.updateTranslation(translation);
        } else {
            accommodationInfoMapper.insertTranslation(translation);
        }
    }

    private AccommodationInfoTranslation koreanTranslation(Long destinationId,
                                                           AccommodationInfo base) {
        AccommodationInfoTranslation translation = new AccommodationInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(KOREAN_CODE);
        if (base != null) {
            translation.setRoomType(nonBlank(base.getRoomType()));
            translation.setEtc(nonBlank(base.getEtc()));
        }
        return translation;
    }

    private AccommodationInfoTranslation translationOf(Long destinationId,
                                                       AccommodationInfoTranslationForm form) {
        AccommodationInfoTranslation translation = new AccommodationInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setRoomType(nonBlank(form.getRoomType()));
        translation.setEtc(nonBlank(form.getEtc()));
        return translation;
    }

    private boolean isEmpty(AccommodationInfoTranslation translation) {
        return translation.getRoomType() == null && translation.getEtc() == null;
    }

    private AccommodationInfoTranslation translationFor(
            List<AccommodationInfoTranslation> translations, String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    private String localizedField(Function<AccommodationInfoTranslation, String> field,
                                  AccommodationInfoTranslation requested,
                                  AccommodationInfoTranslation korean,
                                  List<AccommodationInfoTranslation> ordered,
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

    private String value(Function<AccommodationInfoTranslation, String> field,
                         AccommodationInfoTranslation translation) {
        return translation == null ? null : nonBlank(field.apply(translation));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 원본을 바꾸지 않도록 화면용 복사본을 만든다. 언어와 상관없는 값은 그대로 옮긴다. */
    private AccommodationInfo copyOf(AccommodationInfo source) {
        AccommodationInfo copy = new AccommodationInfo();
        copy.setDestinationId(source.getDestinationId());
        copy.setCheckinTime(source.getCheckinTime());
        copy.setCheckoutTime(source.getCheckoutTime());
        copy.setRoomCount(source.getRoomCount());
        copy.setRoomType(source.getRoomType());
        copy.setStarRating(source.getStarRating());
        copy.setBreakfastIncluded(source.getBreakfastIncluded());
        copy.setParkingAvailable(source.getParkingAvailable());
        copy.setPetAllowed(source.getPetAllowed());
        copy.setContactNumber(source.getContactNumber());
        copy.setHomepageUrl(source.getHomepageUrl());
        copy.setEtc(source.getEtc());
        return copy;
    }

}
