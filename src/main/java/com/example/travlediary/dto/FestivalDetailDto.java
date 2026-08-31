package com.example.travlediary.dto;

import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.InfoImage;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public class FestivalDetailDto {

    private static final String KTO_SOURCE_TYPE = "KTO_TOURAPI";

    private final TravelInfoDetailDto travelInfo;
    private final FestivalInfo festivalInfo;
    private final List<InfoImage> images;

    public FestivalDetailDto(TravelInfoDetailDto travelInfo,
                             FestivalInfo festivalInfo,
                             InfoImage mainImage) {
        this(travelInfo, festivalInfo, mainImage == null ? List.of() : List.of(mainImage));
    }

    public FestivalDetailDto(TravelInfoDetailDto travelInfo,
                             FestivalInfo festivalInfo,
                             List<InfoImage> images) {
        this.travelInfo = travelInfo;
        this.festivalInfo = festivalInfo;
        this.images = images == null ? List.of() : List.copyOf(images);
    }

    public TravelInfoDetailDto getTravelInfo() {
        return travelInfo;
    }

    public FestivalInfo getFestivalInfo() {
        return festivalInfo;
    }

    public InfoImage getMainImage() {
        return images.stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsMain()))
                .findFirst()
                .orElse(images.isEmpty() ? null : images.get(0));
    }

    public List<InfoImage> getImages() {
        return images;
    }

    public List<FestivalGalleryImageDto> getGalleryImages() {
        return images.stream()
                .map(FestivalGalleryImageDto::new)
                .filter(image -> image.getImageUrl() != null)
                .toList();
    }

    public boolean isGallery() {
        return getGalleryImages().size() > 1;
    }

    public boolean isGalleryAttributionPresent() {
        return getGalleryImages().stream().anyMatch(FestivalGalleryImageDto::isAttributionPresent);
    }

    public TravelInfoPeriodDto getPrimaryPeriod() {
        if (travelInfo == null || travelInfo.getPeriods() == null || travelInfo.getPeriods().isEmpty()) {
            return null;
        }
        return travelInfo.getPeriods().get(0);
    }

    public String getEventPlace() {
        return text(festivalInfo == null ? null : festivalInfo.getEventPlace());
    }

    public String getAddress() {
        return text(festivalInfo == null ? null : festivalInfo.getAddress());
    }

    public String getPlayTime() {
        return text(festivalInfo == null ? null : festivalInfo.getPlayTime());
    }

    public String getUseTime() {
        return text(festivalInfo == null ? null : festivalInfo.getUseTime());
    }

    public String getSponsor1() {
        return text(festivalInfo == null ? null : festivalInfo.getSponsor1());
    }

    public String getSponsor1Tel() {
        return text(festivalInfo == null ? null : festivalInfo.getSponsor1Tel());
    }

    public String getSponsor2() {
        return text(festivalInfo == null ? null : festivalInfo.getSponsor2());
    }

    public String getSponsor2Tel() {
        return text(festivalInfo == null ? null : festivalInfo.getSponsor2Tel());
    }

    public String getContactTel() {
        return text(festivalInfo == null ? null : festivalInfo.getContactTel());
    }

    public String getHomepageUrl() {
        String value = text(festivalInfo == null ? null : festivalInfo.getHomepageUrl());
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null
                    ? ""
                    : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String getImageUrl() {
        InfoImage mainImage = getMainImage();
        return text(mainImage == null ? null : mainImage.getImageUrl());
    }

    public String getImageSourceName() {
        InfoImage mainImage = getMainImage();
        return text(mainImage == null ? null : mainImage.getSourceName());
    }

    public String getImageSourceTitle() {
        InfoImage mainImage = getMainImage();
        return text(mainImage == null ? null : mainImage.getSourceTitle());
    }

    public String getLicenseLabel() {
        InfoImage mainImage = getMainImage();
        String licenseType = text(mainImage == null ? null : mainImage.getLicenseType());
        if ("KOGL_TYPE_1".equals(licenseType)) {
            return "공공누리 제1유형";
        }
        if ("KOGL_TYPE_3".equals(licenseType)) {
            return "공공누리 제3유형";
        }
        return null;
    }

    public boolean isTourApiImage() {
        InfoImage mainImage = getMainImage();
        return mainImage != null && KTO_SOURCE_TYPE.equals(text(mainImage.getSourceType()));
    }

    public boolean isEventInfoPresent() {
        return getPrimaryPeriod() != null
                || getEventPlace() != null
                || getAddress() != null
                || getPlayTime() != null
                || getUseTime() != null
                || getSponsor1() != null
                || getSponsor1Tel() != null
                || getSponsor2() != null
                || getSponsor2Tel() != null
                || getContactTel() != null
                || getHomepageUrl() != null;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
