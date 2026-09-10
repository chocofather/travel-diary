package com.example.travlediary.service.user;

import com.example.travlediary.config.i18n.SupportedLanguage;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자동 추천 닉네임(형용사 + 동물 + 숫자)에 쓰는 단어장.
 * 화면 고정 문구가 아니라 생성용 데이터라 messages 번들 대신 여기 한 곳에서만 관리한다.
 * 후보 개수(형용사 10 x 동물 10)는 기존 그대로다.
 */
public final class NicknameVocabulary {

    private NicknameVocabulary() {
    }

    public enum Adjective {
        CUTE("귀여운", "Cute", "かわいい", "可爱", "可愛"),
        KIND("상냥한", "Kind", "やさしい", "温柔", "溫柔"),
        NICE("멋진", "Cool", "すてき", "帅气", "帥氣"),
        FAST("빠른", "Fast", "はやい", "敏捷", "敏捷"),
        HAPPY("행복한", "Happy", "しあわせ", "幸福", "幸福"),
        BRAVE("용감한", "Brave", "勇敢", "勇敢", "勇敢"),
        COLD("차가운", "Chill", "クール", "冷静", "冷靜"),
        WARM("따뜻한", "Warm", "あたたかい", "温暖", "溫暖"),
        STRONG("강한", "Strong", "つよい", "强壮", "強壯"),
        QUIET("조용한", "Quiet", "しずか", "安静", "安靜");

        private final Map<SupportedLanguage, String> names;

        Adjective(String korean, String english, String japanese,
                  String simplifiedChinese, String traditionalChinese) {
            this.names = localizedNames(
                    korean, english, japanese, simplifiedChinese, traditionalChinese);
        }

        public String nameIn(SupportedLanguage language) {
            return names.get(language);
        }
    }

    public enum Animal {
        CAT("고양이", "Cat", "猫", "猫", "貓"),
        DOG("강아지", "Dog", "犬", "小狗", "小狗"),
        RABBIT("토끼", "Rabbit", "うさぎ", "兔子", "兔子"),
        TIGER("호랑이", "Tiger", "虎", "老虎", "老虎"),
        LION("사자", "Lion", "ライオン", "狮子", "獅子"),
        OWL("부엉이", "Owl", "ふくろう", "猫头鹰", "貓頭鷹"),
        FOX("여우", "Fox", "きつね", "狐狸", "狐狸"),
        WOLF("늑대", "Wolf", "おおかみ", "狼", "狼"),
        PENGUIN("펭귄", "Penguin", "ペンギン", "企鹅", "企鵝"),
        BEAR("곰", "Bear", "熊", "熊", "熊");

        private final Map<SupportedLanguage, String> names;

        Animal(String korean, String english, String japanese,
               String simplifiedChinese, String traditionalChinese) {
            this.names = localizedNames(
                    korean, english, japanese, simplifiedChinese, traditionalChinese);
        }

        public String nameIn(SupportedLanguage language) {
            return names.get(language);
        }
    }

    public static final List<Adjective> ADJECTIVES = List.of(Adjective.values());
    public static final List<Animal> ANIMALS = List.of(Animal.values());

    /**
     * 자동 추천의 한 조합. 언어와 무관한 canonical 값이라
     * 같은 조합이면 언어만 다른 표기끼리도 같은 추천으로 본다.
     */
    public record Combination(Adjective adjective, Animal animal, int number) {

        /** 현재 화면에 돌려줄 표기. */
        public String displayIn(SupportedLanguage language) {
            return adjective.nameIn(language) + animal.nameIn(language) + number;
        }

        /**
         * 지원 5개 언어의 표기 전부.
         * 간체/번체처럼 표기가 같아지는 경우가 있어 중복은 제거한다.
         */
        public Set<String> allDisplayNames() {
            Set<String> names = new LinkedHashSet<>();
            for (SupportedLanguage language : SupportedLanguage.all()) {
                names.add(displayIn(language));
            }
            return names;
        }
    }

    private static Map<SupportedLanguage, String> localizedNames(
            String korean, String english, String japanese,
            String simplifiedChinese, String traditionalChinese) {
        Map<SupportedLanguage, String> names = new EnumMap<>(SupportedLanguage.class);
        names.put(SupportedLanguage.KOREAN, korean);
        names.put(SupportedLanguage.ENGLISH, english);
        names.put(SupportedLanguage.JAPANESE, japanese);
        names.put(SupportedLanguage.CHINESE_SIMPLIFIED, simplifiedChinese);
        names.put(SupportedLanguage.CHINESE_TRADITIONAL, traditionalChinese);
        return Map.copyOf(names);
    }
}
