package com.example.travlediary.controller.user;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.service.user.NicknamePolicy;
import com.example.travlediary.service.user.NicknameVocabulary;
import com.example.travlediary.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserApiControllerNicknameTest {

    private final UserService userService = mock(UserService.class);
    private final UserApiController controller = new UserApiController(userService);

    @Test
    void existingSignupBooleanContractRemainsAvailableForValidNames() {
        when(userService.isNicknameExists("여행왕123")).thenReturn(false);

        Map<String, Object> response = controller.checkNickname("여행왕123");

        assertThat(response)
                .containsEntry("exists", false)
                .containsEntry("status", "AVAILABLE");
        verify(userService).isNicknameExists("여행왕123");
    }

    @Test
    void forbiddenSignupNicknameIsNeverReportedAsAvailable() {
        when(userService.isNicknameExists("관12리34자"))
                .thenThrow(new NicknamePolicy.ViolationException(
                        NicknamePolicy.ViolationType.FORBIDDEN,
                        NicknamePolicy.FORBIDDEN_MESSAGE));

        Map<String, Object> response = controller.checkNickname("관12리34자");

        assertThat(response)
                .containsEntry("exists", true)
                .containsEntry("status", "FORBIDDEN")
                .doesNotContainKey("word")
                .doesNotContainKey("category");
    }

    /** 추천 닉네임은 요청 시점의 사이트 언어 단어로 만들어진다(형용사 + 동물 + 숫자). */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 귀여운,상냥한,멋진,빠른,행복한,용감한,차가운,따뜻한,강한,조용한 | 고양이,강아지,토끼,호랑이,사자,부엉이,여우,늑대,펭귄,곰",
            "en    | Cute,Kind,Cool,Fast,Happy,Brave,Chill,Warm,Strong,Quiet     | Cat,Dog,Rabbit,Tiger,Lion,Owl,Fox,Wolf,Penguin,Bear",
            "ja    | かわいい,やさしい,すてき,はやい,しあわせ,勇敢,クール,あたたかい,つよい,しずか | 猫,犬,うさぎ,虎,ライオン,ふくろう,きつね,おおかみ,ペンギン,熊",
            "zh-CN | 可爱,温柔,帅气,敏捷,幸福,勇敢,冷静,温暖,强壮,安静              | 猫,小狗,兔子,老虎,狮子,猫头鹰,狐狸,狼,企鹅,熊",
            "zh-TW | 可愛,溫柔,帥氣,敏捷,幸福,勇敢,冷靜,溫暖,強壯,安靜              | 貓,小狗,兔子,老虎,獅子,貓頭鷹,狐狸,狼,企鵝,熊"
    })
    void recommendationUsesTheCurrentSiteLanguage(String languageTag, String adjectiveList,
                                                  String animalList) {
        when(userService.isAnyNicknameExists(anyCollection())).thenReturn(false);
        List<String> adjectives = List.of(adjectiveList.split(","));
        List<String> animals = List.of(animalList.split(","));
        LocaleContextHolder.setLocale(Locale.forLanguageTag(languageTag));
        try {
            for (int attempt = 0; attempt < 60; attempt++) {
                String nickname = controller.generateNickname();

                assertThat(nickname).as("attempt %s in %s", attempt, languageTag)
                        .matches(candidate -> adjectives.stream().anyMatch(candidate::startsWith),
                                "starts with a localized adjective");
                String withoutNumber = nickname.replaceAll("\\d+$", "");
                assertThat(nickname).matches(".*\\d+$");
                assertThat(animals).anySatisfy(
                        animal -> assertThat(withoutNumber).endsWith(animal));
                // 5개 언어 모두 현재 닉네임 정책을 통과해야 실제로 가입에 쓸 수 있다.
                assertThat(NicknamePolicy.normalizeAndValidate(nickname)).isEqualTo(nickname);
            }
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /** 지원하지 않는 언어는 기존 기본값(한국어) 단어를 쓴다. */
    @Test
    void unsupportedLocaleFallsBackToKoreanWords() {
        when(userService.isAnyNicknameExists(anyCollection())).thenReturn(false);
        LocaleContextHolder.setLocale(Locale.FRANCE);
        try {
            assertThat(controller.generateNickname()).matches("^[가-힣]+\\d+$");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /** 중복이면 사용 가능한 조합이 나올 때까지 다시 뽑는다. */
    @Test
    void duplicateRecommendationIsRetriedUntilAvailable() {
        when(userService.isAnyNicknameExists(anyCollection()))
                .thenReturn(true).thenReturn(true).thenReturn(false);
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            assertThat(controller.generateNickname()).isNotBlank();
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
        verify(userService, times(3)).isAnyNicknameExists(anyCollection());
    }

    /** 중복 확인은 언어별 문자열이 아니라 canonical 조합의 5개 언어 표기 전부로 한다. */
    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void availabilityIsCheckedForEveryLanguageVariantOfTheSameCombination(String languageTag) {
        when(userService.isAnyNicknameExists(anyCollection())).thenReturn(false);
        LocaleContextHolder.setLocale(Locale.forLanguageTag(languageTag));
        try {
            controller.generateNickname();
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userService).isAnyNicknameExists(captor.capture());
        Collection<String> checked = captor.getValue();

        // 간체/번체가 같아지는 조합이 있어 3~5개가 정상이고, 현재 언어 표기는 반드시 포함된다.
        assertThat(checked).hasSizeBetween(3, 5).doesNotHaveDuplicates();
        assertThat(checked).contains(
                controllerResultFor(Locale.forLanguageTag(languageTag), checked));
    }

    /**
     * 다른 언어 표기가 이미 쓰이고 있으면 그 조합은 통째로 폐기하고 다시 뽑는다.
     * (예: `귀여운토끼123` 이 있으면 en 에서 `CuteRabbit123` 을 돌려주지 않는다)
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "en    | 귀여운토끼123      | CuteRabbit123",
            "ja    | 귀여운토끼123      | かわいいうさぎ123",
            "ko    | CuteRabbit456     | 귀여운토끼456",
            "zh-CN | かわいいうさぎ789   | 可爱兔子789",
            "ko    | 可爱兔子321        | 귀여운토끼321",
            "zh-TW | CuteRabbit654     | 可愛兔子654"
    })
    void aCombinationTakenInAnotherLanguageIsNeverRecommended(String languageTag,
                                                              String takenVariant,
                                                              String blockedResult) {
        // 해당 조합의 표기 묶음에 이미 쓰인 값이 들어 있으면 사용 중으로 본다.
        when(userService.isAnyNicknameExists(anyCollection()))
                .thenAnswer(invocation -> {
                    Collection<?> candidates = invocation.getArgument(0);
                    return candidates.contains(takenVariant);
                });

        LocaleContextHolder.setLocale(Locale.forLanguageTag(languageTag));
        try {
            for (int attempt = 0; attempt < 200; attempt++) {
                assertThat(controller.generateNickname())
                        .as("attempt %s in %s", attempt, languageTag)
                        .isNotEqualTo(blockedResult);
            }
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /** 같은 조합이라도 숫자가 다르면 정상 추천된다. */
    @Test
    void anotherNumberOfTheSameWordsIsStillAllowed() {
        when(userService.isAnyNicknameExists(anyCollection()))
                .thenAnswer(invocation ->
                        ((Collection<?>) invocation.getArgument(0)).contains("귀여운토끼123"));

        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            boolean sawSameWordsWithAnotherNumber = false;
            for (int attempt = 0; attempt < 400 && !sawSameWordsWithAnotherNumber; attempt++) {
                String nickname = controller.generateNickname();
                assertThat(nickname).isNotEqualTo("귀여운토끼123");
                sawSameWordsWithAnotherNumber =
                        nickname.startsWith("귀여운토끼") && !nickname.equals("귀여운토끼123");
            }
            assertThat(sawSameWordsWithAnotherNumber)
                    .as("같은 단어 + 다른 숫자는 계속 추천될 수 있어야 한다").isTrue();
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /** 현재 locale 표기를 그대로 돌려주는지 확인하기 위한 도우미. */
    private String controllerResultFor(Locale locale, Collection<String> checked) {
        return checked.stream()
                .filter(candidate -> NicknameVocabulary.ADJECTIVES.stream().anyMatch(adjective ->
                        candidate.startsWith(adjective.nameIn(
                                SupportedLanguage.fromLocale(locale)
                                        .orElse(SupportedLanguage.KOREAN)))))
                .findFirst()
                .orElseThrow();
    }
}
