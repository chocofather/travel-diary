package com.example.travlediary.service.user;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class NicknamePolicy {

    public static final String INVALID_MESSAGE =
            "2~12자의 한글, 영문, 숫자만 사용할 수 있습니다.";
    public static final String FORBIDDEN_MESSAGE = "사용할 수 없는 닉네임입니다.";

    private static final Pattern ALLOWED_PATTERN =
            Pattern.compile("^[가-힣A-Za-z0-9]{2,12}$");

    private static final List<ForbiddenNicknameRule> FORBIDDEN_RULES = List.of(
            contains("관리자", Category.IMPERSONATION),
            contains("운영자", Category.IMPERSONATION),
            contains("운영진", Category.IMPERSONATION),
            contains("어드민", Category.IMPERSONATION),
            contains("admin", Category.IMPERSONATION),
            contains("administrator", Category.IMPERSONATION),
            contains("staff", Category.IMPERSONATION),
            contains("official", Category.IMPERSONATION),
            contains("여행일기", Category.IMPERSONATION),
            contains("traveldiary", Category.IMPERSONATION),
            contains("travlediary", Category.IMPERSONATION),

            contains("씨발", Category.PROFANITY),
            contains("개새끼", Category.PROFANITY),
            contains("좆", Category.PROFANITY),
            contains("fuck", Category.PROFANITY),
            exact("시발", Category.PROFANITY),
            exact("shit", Category.PROFANITY),
            exact("ass", Category.PROFANITY),

            contains("병신", Category.ABUSIVE),
            contains("등신", Category.ABUSIVE),
            contains("멍청이", Category.ABUSIVE),
            exact("쓰레기", Category.ABUSIVE)
    );

    private NicknamePolicy() {
    }

    public static String normalizeAndValidate(String nickname) {
        String normalized = normalizeAndValidateFormat(nickname);
        validateForbiddenExpression(normalized);
        return normalized;
    }

    public static String normalizeAndValidateFormat(String nickname) {
        String normalized = nickname == null ? "" : nickname.strip();
        if (!ALLOWED_PATTERN.matcher(normalized).matches()) {
            throw new ViolationException(ViolationType.INVALID_FORMAT, INVALID_MESSAGE);
        }
        return normalized;
    }

    public static void validateForbiddenExpression(String nickname) {
        if (isForbidden(nickname)) {
            throw new ViolationException(ViolationType.FORBIDDEN, FORBIDDEN_MESSAGE);
        }
    }

    public static boolean isForbidden(String nickname) {
        String normalized = normalizeForInspection(nickname == null ? "" : nickname.strip());
        String withoutDigits = removeAsciiDigits(normalized);
        String leetspeakCanonical = canonicalizeConservativeLeetspeak(normalized);

        return FORBIDDEN_RULES.stream().anyMatch(rule ->
                rule.matches(normalized)
                        || (!withoutDigits.equals(normalized) && rule.matches(withoutDigits))
                        || (rule.isEnglishRule()
                        && !leetspeakCanonical.equals(normalized)
                        && rule.matches(leetspeakCanonical)));
    }

    private static String normalizeForInspection(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static String removeAsciiDigits(String value) {
        StringBuilder canonical = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                canonical.append(character);
            }
        }
        return canonical.toString();
    }

    private static String canonicalizeConservativeLeetspeak(String value) {
        StringBuilder canonical = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            canonical.append(switch (character) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '7' -> 't';
                case '8' -> 'b';
                default -> character;
            });
        }
        return canonical.toString();
    }

    private static ForbiddenNicknameRule exact(String word, Category category) {
        return new ForbiddenNicknameRule(word, MatchType.EXACT, category);
    }

    private static ForbiddenNicknameRule contains(String word, Category category) {
        return new ForbiddenNicknameRule(word, MatchType.CONTAINS, category);
    }

    public enum MatchType {
        EXACT,
        CONTAINS
    }

    public enum Category {
        IMPERSONATION,
        PROFANITY,
        ABUSIVE
    }

    public enum ViolationType {
        INVALID_FORMAT,
        FORBIDDEN
    }

    public record ForbiddenNicknameRule(String word, MatchType matchType, Category category) {

        public ForbiddenNicknameRule {
            word = normalizeForInspection(Objects.requireNonNull(word));
            Objects.requireNonNull(matchType);
            Objects.requireNonNull(category);
        }

        boolean matches(String nickname) {
            return switch (matchType) {
                case EXACT -> nickname.equals(word);
                case CONTAINS -> nickname.contains(word);
            };
        }

        boolean isEnglishRule() {
            return word.chars().allMatch(character -> character >= 'a' && character <= 'z');
        }
    }

    public static final class ViolationException extends IllegalArgumentException {
        private final ViolationType violationType;

        public ViolationException(ViolationType violationType, String message) {
            super(message);
            this.violationType = violationType;
        }

        public ViolationType getViolationType() {
            return violationType;
        }
    }
}
