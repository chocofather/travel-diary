package com.example.travlediary.config;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategorySeedTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 기준 데이터 적재가 실패를 숨기지 않는지.
 *
 * <p>지역 트리가 반쯤 없는 채로 서비스가 뜨면 "일부 지역이 목록에 없다" 는 형태로만 드러나
 * 원인을 찾기 어렵다. 그래서 읽기든 저장이든 실패하면 기동 자체가 실패해야 한다.
 *
 * <p>저장이 한 트랜잭션으로 묶이는지는
 * {@code CountryCategorySeedTransactionServiceTest} 가 이어서 본다.
 */
@ExtendWith(MockitoExtension.class)
class CountryCategoryLoaderTest {

    @Mock
    private CountryCategorySeedTransactionService seedService;

    /**
     * 실제 기준 데이터를 읽어 저장 경계로 넘긴다.
     *
     * <p>현재 파일은 이미 평탄한 목록이라 flatten 은 그대로 통과시킨다. 중첩 형식이 들어와도
     * 자식이 목록에 올라오는지는 아래 테스트가 따로 본다.
     */
    @Test
    void theSeedFileIsReadAndHandedToTheTransactionalBoundary() {
        when(seedService.insertMissing(anyList())).thenReturn(3);

        loader().loadInitialData();

        ArgumentCaptor<List<CountryCategory>> captured = captor();
        verify(seedService).insertMissing(captured.capture());
        List<CountryCategory> categories = captured.getValue();
        assertThat(categories).isNotEmpty();
        // snake_case 매핑이 실제로 걸려 이름과 계층이 채워진 채로 넘어간다
        assertThat(categories).allSatisfy(category -> {
            assertThat(category.getId()).isNotNull();
            assertThat(category.getRegionName()).isNotBlank();
            assertThat(category.getDepth()).isNotNull();
        });
        // 번호는 겹치지 않는다. 겹치면 "이미 있는 행" 판정이 흔들린다.
        assertThat(categories).extracting(CountryCategory::getId).doesNotHaveDuplicates();
    }

    /** 중첩 형식이 들어오면 자식도 같은 목록에 부모 뒤로 올라온다. */
    @Test
    void nestedCategoriesAreFlattenedIntoTheSameList() {
        when(seedService.insertMissing(anyList())).thenReturn(3);
        CountryCategoryLoader loader =
                new CountryCategoryLoader(seedService, "/json/nested-country-categories-test.json");

        loader.loadInitialData();

        ArgumentCaptor<List<CountryCategory>> captured = captor();
        verify(seedService).insertMissing(captured.capture());
        assertThat(captured.getValue()).extracting(CountryCategory::getId)
                .containsExactly(9001L, 9002L, 9003L);
    }

    /** 저장이 실패하면 기동을 계속하지 않는다. */
    @Test
    void aStorageFailureStopsStartup() {
        when(seedService.insertMissing(anyList()))
                .thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> loader().loadInitialData())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("저장하지 못했습니다")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /** 기준 데이터 파일이 없으면 저장을 시도하기도 전에 기동이 멈춘다. */
    @Test
    void aMissingSeedFileStopsStartupBeforeAnyWrite() {
        CountryCategoryLoader loader =
                new CountryCategoryLoader(seedService, "/json/does-not-exist.json");

        assertThatThrownBy(loader::loadInitialData)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("읽지 못했습니다")
                .hasCauseInstanceOf(IOException.class);

        verify(seedService, never()).insertMissing(anyList());
    }

    /** 내용이 깨진 파일도 같은 방식으로 기동을 멈춘다. */
    @Test
    void aMalformedSeedFileStopsStartupBeforeAnyWrite(@org.junit.jupiter.api.io.TempDir Path ignored) {
        // 실제 classpath 에 올라와 있는 JSON 이 아닌 파일을 가리켜 파싱 실패 경로를 태운다.
        CountryCategoryLoader loader =
                new CountryCategoryLoader(seedService, "/messages.properties");

        assertThatThrownBy(loader::loadInitialData)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("읽지 못했습니다");

        verify(seedService, never()).insertMissing(anyList());
    }

    /**
     * 읽기 경로가 자원을 열어 둔 채로 끝나지 않는지.
     *
     * <p>스트림이 닫혔는지는 바깥에서 관찰하기 어려우므로, 소스가 try-with-resources 를 쓰는지와
     * 기동 경로를 되풀이해도 결과가 같은지를 함께 본다.
     * (이 저장소의 다른 계약 테스트들과 같은 방식이다)
     */
    @Test
    void theSeedFileIsReadWithTryWithResourcesAndNeverPrintsStackTraces() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/CountryCategoryLoader.java"),
                StandardCharsets.UTF_8);

        assertThat(source).contains("try (InputStream input = getClass().getResourceAsStream(");
        assertThat(source).doesNotContain("printStackTrace");
        assertThat(source).doesNotContain("System.out");
    }

    /** 같은 경로를 되풀이해 돌려도 자원이 잠기거나 결과가 달라지지 않는다. */
    @Test
    void repeatedLoadsKeepWorking() {
        when(seedService.insertMissing(anyList())).thenReturn(0);
        CountryCategoryLoader loader = loader();

        assertThatCode(() -> {
            loader.loadInitialData();
            loader.loadInitialData();
            loader.loadInitialData();
        }).doesNotThrowAnyException();

        verify(seedService, times(3)).insertMissing(anyList());
    }

    /* ===== 도우미 ===== */

    private CountryCategoryLoader loader() {
        return new CountryCategoryLoader(seedService);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<CountryCategory>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
