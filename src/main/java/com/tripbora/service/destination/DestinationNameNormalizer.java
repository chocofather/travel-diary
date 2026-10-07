package com.tripbora.service.destination;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 중복 판별용 여행지명 정규화. 표기 차이만 지우고 단어 자체는 바꾸지 않는다.
 *
 * <ol>
 *   <li>NFKC 로 전각·호환 문자를 맞춘다 (（） → (), Ａ → A)</li>
 *   <li>대소문자를 무시한다</li>
 *   <li>괄호로 덧붙인 보충 설명을 지운다 ("경복궁 (景福宮)", "남산타워[N서울타워]")</li>
 *   <li>공백·문장부호·기호를 지우고 글자와 숫자만 남긴다</li>
 * </ol>
 * 괄호를 지우면 아무것도 남지 않는 이름은 괄호 안 글자를 그대로 쓴다.
 * "서울 경복궁"과 "경복궁"처럼 단어가 더 붙은 이름은 다른 이름으로 본다.
 * JSON 일괄등록이 TourAPI 제목을 대조할 때도 같은 규칙을 쓴다.
 */
public final class DestinationNameNormalizer {

    /** 이보다 짧게 정규화된 이름은 비교하지 않는다 (한 글자 이름은 우연히 겹치기 쉽다). */
    static final int MIN_LENGTH = 2;

    private static final Pattern BRACKETED = Pattern.compile(
            "\\([^()]*\\)|\\[[^\\[\\]]*]|\\{[^{}]*}|<[^<>]*>|「[^」]*」|『[^』]*』|【[^】]*】|〈[^〉]*〉|《[^》]*》");

    private DestinationNameNormalizer() {
    }

    /** @return 비교용 이름. 비교할 수 없으면 null */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String compact = lettersAndDigits(BRACKETED.matcher(folded).replaceAll(" "));
        if (compact.isEmpty()) {
            compact = lettersAndDigits(folded);
        }
        return compact.codePointCount(0, compact.length()) < MIN_LENGTH ? null : compact;
    }

    private static String lettersAndDigits(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }
}
