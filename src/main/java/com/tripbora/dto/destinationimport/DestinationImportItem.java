package com.tripbora.dto.destinationimport;

import com.tripbora.model.DestinationSeason;
import com.tripbora.model.DestinationType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * JSON 일괄등록 파일의 여행지 한 건. DB PK 와 이미지 정보는 담지 않는다.
 *
 * <p>문자열은 앞뒤 공백을 지우고 NFC 로 맞춘 값이며, 빈 문자열은 null 이다.
 * 형식이 틀린 값(모르는 type 등)은 null 로 두고 오류는 파서가 따로 모은다.</p>
 *
 * @param key          파일 안에서 행을 가리키는 이름. 저장하지 않는다
 * @param region       사람이 읽는 지역 이름 경로. ID 로 바꾸는 일은 Master Resolver 가 한다
 * @param categories   카테고리 이름(categories.name)
 * @param amenities    편의시설 code(amenities.code)
 * @param translations 언어 코드 → 이름·설명
 * @param evidence     사실정보 근거. 미리보기에서만 보여주고 저장하지 않는다
 */
public record DestinationImportItem(
        String key,
        DestinationType type,
        DestinationSeason season,
        Region region,
        BigDecimal latitude,
        BigDecimal longitude,
        External external,
        List<String> categories,
        String mainCategory,
        List<String> amenities,
        Map<String, Text> translations,
        Info info,
        List<Evidence> evidence
) {
    public DestinationImportItem {
        region = region == null ? Region.EMPTY : region;
        external = external == null ? External.EMPTY : external;
        categories = categories == null ? List.of() : List.copyOf(categories);
        amenities = amenities == null ? List.of() : List.copyOf(amenities);
        translations = translations == null ? Map.of() : translations;
        info = info == null ? Info.EMPTY : info;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public Text translation(String languageCode) {
        return translations.get(languageCode);
    }

    /** 한국어 이름. 없으면 null */
    public String koreanName() {
        Text korean = translation("ko");
        return korean == null ? null : korean.name();
    }

    public record Region(String country, String city, String district) {
        public static final Region EMPTY = new Region(null, null, null);
    }

    public record External(String tourApiContentId, String wikidataQid, String googlePlaceId) {
        public static final External EMPTY = new External(null, null, null);
    }

    public record Text(String name, String shortDescription, String description) {
    }

    /**
     * 유형별 운영정보. 값은 String·Boolean·Long·BigDecimal 중 하나이며, 허용 필드는 type 마다 다르다.
     *
     * @param values       한국어 원문(= *_info base 이자 ko 번역)
     * @param translations 언어 코드(en·ja·zh-CN·zh-TW) → 번역 가능한 필드 값
     */
    public record Info(Map<String, Object> values, Map<String, Map<String, String>> translations) {
        public static final Info EMPTY = new Info(Map.of(), Map.of());

        public Info {
            values = values == null ? Map.of() : values;
            translations = translations == null ? Map.of() : translations;
        }

        public String text(String field) {
            return values.get(field) instanceof String text ? text : null;
        }

        public Boolean bool(String field) {
            return values.get(field) instanceof Boolean flag ? flag : null;
        }

        public Integer integer(String field) {
            return values.get(field) instanceof Long number ? Math.toIntExact(number) : null;
        }

        public BigDecimal decimal(String field) {
            return values.get(field) instanceof BigDecimal number ? number : null;
        }

        public String translated(String languageCode, String field) {
            Map<String, String> language = translations.get(languageCode);
            return language == null ? null : language.get(field);
        }
    }

    public record Evidence(String url, List<String> fields) {
        public Evidence {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }
}
