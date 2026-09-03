package com.example.travlediary.service.info;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.AttractionInfoTranslation;
import com.example.travlediary.repository.info.AttractionInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

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
}
