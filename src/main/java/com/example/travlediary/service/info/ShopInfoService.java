package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.ShopInfoTranslationForm;
import com.example.travlediary.model.ShopInfo;
import com.example.travlediary.model.ShopInfoTranslation;
import com.example.travlediary.repository.info.ShopInfoMapper;
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
public class ShopInfoService {

    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();
    /** 관리자 입력 슬롯이 저장할 수 있는 언어. 한국어는 원본 입력이 대신한다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES =
            Set.copyOf(DestinationForm.SUBTYPE_TRANSLATION_LANGUAGES);

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

    /**
     * 공개 상세용. 자유 텍스트(휴점일·영업시간·주요상품·기타 안내)만 요청 언어로 바꾼다.
     *
     * <p>번역은 이 여행지 것을 한 번에 읽고, 값은 필드마다 따로 고른다.
     * 차례는 요청 언어 → 한국어 → 남은 언어(언어 코드, id 순) → 원문이며,
     * 어디에도 값이 없으면 null 이다. (다른 유형 상세정보와 같은 규칙)
     *
     * <p>주차 가능 여부, 연락처, 홈페이지는 원본을 그대로 둔다.
     */
    public ShopInfo findLocalizedByDestinationId(Long destinationId,
                                                 SupportedLanguage requestedLanguage) {
        ShopInfo base = shopInfoMapper.findByDestinationId(destinationId);
        if (base == null) {
            return null;
        }

        List<ShopInfoTranslation> ordered = orderedTranslations(destinationId);
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        ShopInfoTranslation requested = translationFor(ordered, language.getLanguageTag());
        ShopInfoTranslation korean = translationFor(
                ordered, SupportedLanguage.KOREAN.getLanguageTag());

        ShopInfo localized = copyOf(base);
        localized.setClosedDays(localizedField(
                ShopInfoTranslation::getClosedDays, requested, korean, ordered,
                base.getClosedDays()));
        localized.setOpeningHours(localizedField(
                ShopInfoTranslation::getOpeningHours, requested, korean, ordered,
                base.getOpeningHours()));
        localized.setMainProducts(localizedField(
                ShopInfoTranslation::getMainProducts, requested, korean, ordered,
                base.getMainProducts()));
        localized.setGuide(localizedField(
                ShopInfoTranslation::getGuide, requested, korean, ordered,
                base.getGuide()));
        return localized;
    }

    /**
     * 관리자 저장. 한국어는 원본(shop_info) 값을 그대로 ko 번역에도 맞추고,
     * 나머지 언어는 입력 슬롯 값을 언어별로 저장한다.
     *
     * <p>언어 한 줄이 단위다. 값이 있으면 넣거나 고치고, 그 언어 값이 모두 비면 줄을 지운다.
     * legacy 'zh' 는 쓰지 않는다.
     */
    @Transactional
    public void saveTranslations(Long destinationId,
                                 ShopInfo koreanBase,
                                 List<ShopInfoTranslationForm> translationForms) {
        if (destinationId == null) {
            return;
        }

        Map<String, ShopInfoTranslation> existing = new LinkedHashMap<>();
        for (ShopInfoTranslation translation : storedTranslations(destinationId)) {
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
        for (ShopInfoTranslationForm form : translationForms) {
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
    public List<ShopInfoTranslationForm> getTranslationForms(Long destinationId) {
        List<ShopInfoTranslationForm> slots = DestinationForm.newShopInfoTranslationSlots();
        if (destinationId == null) {
            return slots;
        }

        for (ShopInfoTranslation translation : storedTranslations(destinationId)) {
            if (translation.getLanguageCode() == null) {
                continue;
            }
            for (ShopInfoTranslationForm slot : slots) {
                if (slot.getLanguageCode().equals(translation.getLanguageCode())) {
                    slot.setClosedDays(translation.getClosedDays());
                    slot.setOpeningHours(translation.getOpeningHours());
                    slot.setMainProducts(translation.getMainProducts());
                    slot.setGuide(translation.getGuide());
                }
            }
        }
        return slots;
    }

    /** 저장된 번역 줄. 조회 실패는 감추지 않고 그대로 올린다. */
    private List<ShopInfoTranslation> storedTranslations(Long destinationId) {
        List<ShopInfoTranslation> stored =
                shopInfoMapper.findTranslationsByDestinationId(destinationId);
        if (stored == null) {
            return List.of();
        }
        return stored.stream().filter(translation -> translation != null).toList();
    }

    private List<ShopInfoTranslation> orderedTranslations(Long destinationId) {
        return storedTranslations(destinationId).stream()
                .sorted(Comparator
                        .comparing(ShopInfoTranslation::getLanguageCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ShopInfoTranslation::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** 값이 하나라도 있으면 INSERT/UPDATE, 모두 비었으면 있던 줄만 지운다. */
    private void saveTranslation(Long destinationId,
                                 ShopInfoTranslation translation,
                                 boolean exists) {
        if (isEmpty(translation)) {
            if (exists) {
                shopInfoMapper.deleteTranslation(destinationId, translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            shopInfoMapper.updateTranslation(translation);
        } else {
            shopInfoMapper.insertTranslation(translation);
        }
    }

    private ShopInfoTranslation koreanTranslation(Long destinationId, ShopInfo base) {
        ShopInfoTranslation translation = new ShopInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(KOREAN_CODE);
        if (base != null) {
            translation.setClosedDays(nonBlank(base.getClosedDays()));
            translation.setOpeningHours(nonBlank(base.getOpeningHours()));
            translation.setMainProducts(nonBlank(base.getMainProducts()));
            translation.setGuide(nonBlank(base.getGuide()));
        }
        return translation;
    }

    private ShopInfoTranslation translationOf(Long destinationId, ShopInfoTranslationForm form) {
        ShopInfoTranslation translation = new ShopInfoTranslation();
        translation.setDestinationId(destinationId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setClosedDays(nonBlank(form.getClosedDays()));
        translation.setOpeningHours(nonBlank(form.getOpeningHours()));
        translation.setMainProducts(nonBlank(form.getMainProducts()));
        translation.setGuide(nonBlank(form.getGuide()));
        return translation;
    }

    private boolean isEmpty(ShopInfoTranslation translation) {
        return translation.getClosedDays() == null
                && translation.getOpeningHours() == null
                && translation.getMainProducts() == null
                && translation.getGuide() == null;
    }

    private ShopInfoTranslation translationFor(List<ShopInfoTranslation> translations,
                                               String languageTag) {
        return translations.stream()
                .filter(translation -> languageTag.equals(translation.getLanguageCode()))
                .findFirst()
                .orElse(null);
    }

    private String localizedField(Function<ShopInfoTranslation, String> field,
                                  ShopInfoTranslation requested,
                                  ShopInfoTranslation korean,
                                  List<ShopInfoTranslation> ordered,
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

    private String value(Function<ShopInfoTranslation, String> field,
                         ShopInfoTranslation translation) {
        return translation == null ? null : nonBlank(field.apply(translation));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 원본을 바꾸지 않도록 화면용 복사본을 만든다. 언어와 상관없는 값은 그대로 옮긴다. */
    private ShopInfo copyOf(ShopInfo source) {
        ShopInfo copy = new ShopInfo();
        copy.setDestinationId(source.getDestinationId());
        copy.setClosedDays(source.getClosedDays());
        copy.setOpeningHours(source.getOpeningHours());
        copy.setMainProducts(source.getMainProducts());
        copy.setParkingAvailable(source.getParkingAvailable());
        copy.setContactNumber(source.getContactNumber());
        copy.setHomepageUrl(source.getHomepageUrl());
        copy.setGuide(source.getGuide());
        return copy;
    }
}
