package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.AttractionInfoTranslationForm;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.AttractionInfoTranslation;
import com.example.travlediary.repository.info.AttractionInfoMapper;
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
public class AttractionInfoService {

    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();
    /** 관리자 입력 슬롯이 저장할 수 있는 언어. 한국어는 원본 입력이 대신한다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES =
            Set.copyOf(DestinationForm.SUBTYPE_TRANSLATION_LANGUAGES);

    private final AttractionInfoMapper attractionInfoMapper;

    public void save(AttractionInfo info) {
        attractionInfoMapper.insert(info);
    }

    public AttractionInfo findByDestinationId(Long destinationId) {
        return attractionInfoMapper.findByDestinationId(destinationId);
    }

    public AttractionInfo findLocalizedByDestinationId(Long destinationId,
                                                        SupportedLanguage requestedLanguage) {
        AttractionInfo base = attractionInfoMapper.findByDestinationId(destinationId);
        if (base == null) {
            return null;
        }

        List<AttractionInfoTranslation> translations =
                attractionInfoMapper.findTranslationsByDestinationId(destinationId);
        List<AttractionInfoTranslation> ordered = translations == null
                ? List.of()
                : translations.stream()
                .filter(translation -> translation != null)
                .sorted(Comparator
                        .comparing(AttractionInfoTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AttractionInfoTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        AttractionInfoTranslation requested = translationFor(
                ordered, language.getLanguageTag());
        AttractionInfoTranslation korean = translationFor(
                ordered, SupportedLanguage.KOREAN.getLanguageTag());

        AttractionInfo localized = copyOf(base);
        localized.setClosedDays(localizedField(
                AttractionInfoTranslation::getClosedDays, requested, korean, ordered,
                base.getClosedDays()));
        localized.setOpeningHours(localizedField(
                AttractionInfoTranslation::getOpeningHours, requested, korean, ordered,
                base.getOpeningHours()));
        localized.setAdmissionFee(localizedField(
                AttractionInfoTranslation::getAdmissionFee, requested, korean, ordered,
                base.getAdmissionFee()));
        localized.setGuide(localizedField(
                AttractionInfoTranslation::getGuide, requested, korean, ordered,
                base.getGuide()));
        return localized;
    }

    private AttractionInfoTranslation translationFor(
            List<AttractionInfoTranslation> translations, String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    private String localizedField(Function<AttractionInfoTranslation, String> field,
                                  AttractionInfoTranslation requested,
                                  AttractionInfoTranslation korean,
                                  List<AttractionInfoTranslation> ordered,
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

    private String value(Function<AttractionInfoTranslation, String> field,
                         AttractionInfoTranslation translation) {
        return translation == null ? null : nonBlank(field.apply(translation));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private AttractionInfo copyOf(AttractionInfo source) {
        AttractionInfo copy = new AttractionInfo();
        copy.setDestinationId(source.getDestinationId());
        copy.setClosedDays(source.getClosedDays());
        copy.setOpeningHours(source.getOpeningHours());
        copy.setAdmissionFee(source.getAdmissionFee());
        copy.setParkingAvailable(source.getParkingAvailable());
        copy.setContactNumber(source.getContactNumber());
        copy.setHomepageUrl(source.getHomepageUrl());
        copy.setGuide(source.getGuide());
        return copy;
    }

    public void update(AttractionInfo info) {
        attractionInfoMapper.update(info);
    }

    /**
     * 관리자 저장. 한국어는 원본(attraction_info) 값을 그대로 ko 번역에도 맞추고,
     * 나머지 언어는 입력 슬롯 값을 언어별로 저장한다. (식당 상세정보와 같은 규칙)
     *
     * <p>언어 한 줄이 단위다. 값이 있으면 넣거나 고치고, 그 언어 값이 모두 비면 줄을 지운다.
     * 한 언어를 지워도 다른 언어는 건드리지 않는다. legacy 'zh' 는 쓰지 않는다.
     */
    @Transactional
    public void saveTranslations(Long destinationId,
                                 AttractionInfo koreanBase,
                                 List<AttractionInfoTranslationForm> translationForms) {
        if (destinationId == null) {
            return;
        }

        Map<String, AttractionInfoTranslation> existing = new LinkedHashMap<>();
        List<AttractionInfoTranslation> stored =
                attractionInfoMapper.findTranslationsByDestinationId(destinationId);
        if (stored != null) {
            for (AttractionInfoTranslation translation : stored) {
                if (translation != null && translation.getLanguageCode() != null) {
                    existing.putIfAbsent(translation.getLanguageCode(), translation);
                }
            }
        }

        // 한국어는 화면의 원본 입력이 그대로 ko 번역이 된다.
        saveTranslation(destinationId, koreanTranslation(destinationId, koreanBase),
                existing.containsKey(KOREAN_CODE));

        if (translationForms == null) {
            return;
        }
        for (AttractionInfoTranslationForm form : translationForms) {
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
    public List<AttractionInfoTranslationForm> getTranslationForms(Long destinationId) {
        List<AttractionInfoTranslationForm> slots =
                DestinationForm.newAttractionInfoTranslationSlots();
        if (destinationId == null) {
            return slots;
        }

        List<AttractionInfoTranslation> stored =
                attractionInfoMapper.findTranslationsByDestinationId(destinationId);
        if (stored == null) {
            return slots;
        }
        for (AttractionInfoTranslation translation : stored) {
            if (translation == null || translation.getLanguageCode() == null) {
                continue;
            }
            for (AttractionInfoTranslationForm slot : slots) {
                if (slot.getLanguageCode().equals(translation.getLanguageCode())) {
                    slot.setClosedDays(translation.getClosedDays());
                    slot.setOpeningHours(translation.getOpeningHours());
                    slot.setAdmissionFee(translation.getAdmissionFee());
                    slot.setGuide(translation.getGuide());
                }
            }
        }
        return slots;
    }

    /** 값이 하나라도 있으면 INSERT/UPDATE, 모두 비었으면 있던 줄만 지운다. */
    private void saveTranslation(Long destinationId,
                                 AttractionInfoTranslation translation,
                                 boolean exists) {
        if (isEmpty(translation)) {
            if (exists) {
                attractionInfoMapper.deleteTranslation(destinationId, translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            attractionInfoMapper.updateTranslation(translation);
        } else {
            attractionInfoMapper.insertTranslation(translation);
        }
    }

    private AttractionInfoTranslation koreanTranslation(Long destinationId, AttractionInfo base) {
        AttractionInfoTranslation translation = new AttractionInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(KOREAN_CODE);
        if (base != null) {
            translation.setClosedDays(nonBlank(base.getClosedDays()));
            translation.setOpeningHours(nonBlank(base.getOpeningHours()));
            translation.setAdmissionFee(nonBlank(base.getAdmissionFee()));
            translation.setGuide(nonBlank(base.getGuide()));
        }
        return translation;
    }

    private AttractionInfoTranslation translationOf(Long destinationId,
                                                    AttractionInfoTranslationForm form) {
        AttractionInfoTranslation translation = new AttractionInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setClosedDays(nonBlank(form.getClosedDays()));
        translation.setOpeningHours(nonBlank(form.getOpeningHours()));
        translation.setAdmissionFee(nonBlank(form.getAdmissionFee()));
        translation.setGuide(nonBlank(form.getGuide()));
        return translation;
    }

    private boolean isEmpty(AttractionInfoTranslation translation) {
        return translation.getClosedDays() == null
                && translation.getOpeningHours() == null
                && translation.getAdmissionFee() == null
                && translation.getGuide() == null;
    }
}
