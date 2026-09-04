package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.RestaurantInfoTranslationForm;
import com.example.travlediary.model.RestaurantInfo;
import com.example.travlediary.model.RestaurantInfoTranslation;
import com.example.travlediary.repository.info.RestaurantInfoMapper;
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
public class RestaurantInfoService {

    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();
    /** 관리자 입력 슬롯이 저장할 수 있는 언어. 한국어는 원본 입력이 대신한다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES =
            Set.copyOf(DestinationForm.SUBTYPE_TRANSLATION_LANGUAGES);

    private final RestaurantInfoMapper restaurantInfoMapper;

    public void save(RestaurantInfo info) {
        restaurantInfoMapper.insert(info);
    }

    public RestaurantInfo findByDestinationId(Long destinationId) {
        return restaurantInfoMapper.findByDestinationId(destinationId);
    }

    /**
     * 공개 상세용. 자유 텍스트만 요청 언어로 바꾼다.
     *
     * <p>번역은 이 여행지 것을 한 번에 읽고, 값은 필드마다 따로 고른다.
     * 차례는 요청 언어 → 한국어 → 남은 언어(언어 코드, id 순) → 원문이며,
     * 어디에도 값이 없으면 null 이다. (관광명소 정보와 같은 규칙)
     *
     * <p>전화번호·홈페이지·좌석 수·가능 여부는 언어와 상관없어 원본을 그대로 둔다.
     */
    public RestaurantInfo findLocalizedByDestinationId(Long destinationId,
                                                       SupportedLanguage requestedLanguage) {
        RestaurantInfo base = restaurantInfoMapper.findByDestinationId(destinationId);
        if (base == null) {
            return null;
        }

        List<RestaurantInfoTranslation> translations =
                restaurantInfoMapper.findTranslationsByDestinationId(destinationId);
        List<RestaurantInfoTranslation> ordered = translations == null
                ? List.of()
                : translations.stream()
                .filter(translation -> translation != null)
                .sorted(Comparator
                        .comparing(RestaurantInfoTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RestaurantInfoTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        RestaurantInfoTranslation requested = translationFor(
                ordered, language.getLanguageTag());
        RestaurantInfoTranslation korean = translationFor(
                ordered, SupportedLanguage.KOREAN.getLanguageTag());

        RestaurantInfo localized = copyOf(base);
        localized.setMainMenu(localizedField(
                RestaurantInfoTranslation::getMainMenu, requested, korean, ordered,
                base.getMainMenu()));
        localized.setPriceRange(localizedField(
                RestaurantInfoTranslation::getPriceRange, requested, korean, ordered,
                base.getPriceRange()));
        localized.setOpeningHours(localizedField(
                RestaurantInfoTranslation::getOpeningHours, requested, korean, ordered,
                base.getOpeningHours()));
        localized.setBreakTime(localizedField(
                RestaurantInfoTranslation::getBreakTime, requested, korean, ordered,
                base.getBreakTime()));
        localized.setClosedDays(localizedField(
                RestaurantInfoTranslation::getClosedDays, requested, korean, ordered,
                base.getClosedDays()));
        localized.setEtc(localizedField(
                RestaurantInfoTranslation::getEtc, requested, korean, ordered,
                base.getEtc()));
        return localized;
    }

    private RestaurantInfoTranslation translationFor(
            List<RestaurantInfoTranslation> translations, String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    private String localizedField(Function<RestaurantInfoTranslation, String> field,
                                  RestaurantInfoTranslation requested,
                                  RestaurantInfoTranslation korean,
                                  List<RestaurantInfoTranslation> ordered,
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

    private String value(Function<RestaurantInfoTranslation, String> field,
                         RestaurantInfoTranslation translation) {
        return translation == null ? null : nonBlank(field.apply(translation));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 원본을 바꾸지 않도록 화면용 복사본을 만든다. 언어와 상관없는 값은 그대로 옮긴다. */
    private RestaurantInfo copyOf(RestaurantInfo source) {
        RestaurantInfo copy = new RestaurantInfo();
        copy.setDestinationId(source.getDestinationId());
        copy.setMainMenu(source.getMainMenu());
        copy.setPriceRange(source.getPriceRange());
        copy.setOpeningHours(source.getOpeningHours());
        copy.setBreakTime(source.getBreakTime());
        copy.setClosedDays(source.getClosedDays());
        copy.setParkingAvailable(source.getParkingAvailable());
        copy.setPetAllowed(source.getPetAllowed());
        copy.setSeatCount(source.getSeatCount());
        copy.setTakeoutAvailable(source.getTakeoutAvailable());
        copy.setDeliveryAvailable(source.getDeliveryAvailable());
        copy.setReservation(source.getReservation());
        copy.setContactNumber(source.getContactNumber());
        copy.setHomepageUrl(source.getHomepageUrl());
        copy.setEtc(source.getEtc());
        return copy;
    }

    public void update(RestaurantInfo info) {
        restaurantInfoMapper.update(info);
    }

    /**
     * 관리자 저장. 한국어는 원본(restaurant_info) 값을 그대로 ko 번역에도 맞추고,
     * 나머지 언어는 입력 슬롯 값을 언어별로 저장한다.
     *
     * <p>언어 한 줄이 단위다. 값이 있으면 넣거나 고치고, 그 언어 값이 모두 비면 줄을 지운다.
     * 한 언어를 지워도 다른 언어는 건드리지 않는다. legacy 'zh' 는 쓰지 않는다.
     *
     * <p>기존 줄 확인은 여행지당 한 번의 조회로 끝낸다.
     */
    @Transactional
    public void saveTranslations(Long destinationId,
                                 RestaurantInfo koreanBase,
                                 List<RestaurantInfoTranslationForm> translationForms) {
        if (destinationId == null) {
            return;
        }

        Map<String, RestaurantInfoTranslation> existing = new LinkedHashMap<>();
        List<RestaurantInfoTranslation> stored =
                restaurantInfoMapper.findTranslationsByDestinationId(destinationId);
        if (stored != null) {
            for (RestaurantInfoTranslation translation : stored) {
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
        for (RestaurantInfoTranslationForm form : translationForms) {
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
    public List<RestaurantInfoTranslationForm> getTranslationForms(Long destinationId) {
        List<RestaurantInfoTranslationForm> slots =
                DestinationForm.newRestaurantInfoTranslationSlots();
        if (destinationId == null) {
            return slots;
        }

        List<RestaurantInfoTranslation> stored =
                restaurantInfoMapper.findTranslationsByDestinationId(destinationId);
        if (stored == null) {
            return slots;
        }
        for (RestaurantInfoTranslation translation : stored) {
            if (translation == null || translation.getLanguageCode() == null) {
                continue;
            }
            for (RestaurantInfoTranslationForm slot : slots) {
                if (slot.getLanguageCode().equals(translation.getLanguageCode())) {
                    slot.setMainMenu(translation.getMainMenu());
                    slot.setPriceRange(translation.getPriceRange());
                    slot.setOpeningHours(translation.getOpeningHours());
                    slot.setBreakTime(translation.getBreakTime());
                    slot.setClosedDays(translation.getClosedDays());
                    slot.setEtc(translation.getEtc());
                }
            }
        }
        return slots;
    }

    /** 값이 하나라도 있으면 INSERT/UPDATE, 모두 비었으면 있던 줄만 지운다. */
    private void saveTranslation(Long destinationId,
                                 RestaurantInfoTranslation translation,
                                 boolean exists) {
        if (isEmpty(translation)) {
            if (exists) {
                restaurantInfoMapper.deleteTranslation(destinationId, translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            restaurantInfoMapper.updateTranslation(translation);
        } else {
            restaurantInfoMapper.insertTranslation(translation);
        }
    }

    private RestaurantInfoTranslation koreanTranslation(Long destinationId, RestaurantInfo base) {
        RestaurantInfoTranslation translation = new RestaurantInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(KOREAN_CODE);
        if (base != null) {
            translation.setMainMenu(nonBlank(base.getMainMenu()));
            translation.setPriceRange(nonBlank(base.getPriceRange()));
            translation.setOpeningHours(nonBlank(base.getOpeningHours()));
            translation.setBreakTime(nonBlank(base.getBreakTime()));
            translation.setClosedDays(nonBlank(base.getClosedDays()));
            translation.setEtc(nonBlank(base.getEtc()));
        }
        return translation;
    }

    private RestaurantInfoTranslation translationOf(Long destinationId,
                                                    RestaurantInfoTranslationForm form) {
        RestaurantInfoTranslation translation = new RestaurantInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setMainMenu(nonBlank(form.getMainMenu()));
        translation.setPriceRange(nonBlank(form.getPriceRange()));
        translation.setOpeningHours(nonBlank(form.getOpeningHours()));
        translation.setBreakTime(nonBlank(form.getBreakTime()));
        translation.setClosedDays(nonBlank(form.getClosedDays()));
        translation.setEtc(nonBlank(form.getEtc()));
        return translation;
    }

    private boolean isEmpty(RestaurantInfoTranslation translation) {
        return translation.getMainMenu() == null
                && translation.getPriceRange() == null
                && translation.getOpeningHours() == null
                && translation.getBreakTime() == null
                && translation.getClosedDays() == null
                && translation.getEtc() == null;
    }

}
