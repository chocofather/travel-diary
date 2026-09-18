package com.example.travlediary.config;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategorySeedTransactionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 국가·지역 기준 데이터를 기동할 때 채운다.
 *
 * <p>여기서 실패하면 애플리케이션을 띄우지 않는다. 지역 트리가 반쯤 없는 채로 서비스가 뜨면
 * 여행지·코스·검색에서 일부 지역만 사라지는데, 그것은 오류로 보이지 않아 한참 뒤에야 발견된다.
 * 기동이 실패하는 쪽이 눈에 띄고 되돌리기도 쉽다.
 *
 * <p>실제 저장은 {@link CountryCategorySeedTransactionService} 가 맡는다. 이 메서드는
 * {@code @PostConstruct} 라 같은 빈 안에서는 트랜잭션이 걸리지 않기 때문이다.
 * 주입받은 참조는 프록시이므로 그쪽을 부르면 한 트랜잭션으로 묶인다.
 */
@Component
public class CountryCategoryLoader {

    /** 기준 데이터 원본. 없는 행을 채우는 데만 쓰고 기존 행은 덮어쓰지 않는다. */
    static final String RESOURCE_PATH = "/json/country_categories.json";

    private static final Logger log = LoggerFactory.getLogger(CountryCategoryLoader.class);

    private final CountryCategorySeedTransactionService seedService;
    private final String resourcePath;

    @Autowired
    public CountryCategoryLoader(CountryCategorySeedTransactionService seedService) {
        this(seedService, RESOURCE_PATH);
    }

    /** 읽기 실패 경로를 확인하기 위해 자원 이름을 바꿔 끼울 수 있게 열어 둔 자리다. */
    CountryCategoryLoader(CountryCategorySeedTransactionService seedService, String resourcePath) {
        this.seedService = seedService;
        this.resourcePath = resourcePath;
    }

    @PostConstruct
    public void loadInitialData() {
        final List<CountryCategory> flatList;
        try {
            flatList = flatten(readCategories());
        } catch (IOException | RuntimeException exception) {
            log.error("Country category seed data could not be read: resource={}, exceptionType={}",
                    resourcePath, exception.getClass().getSimpleName(), exception);
            throw new IllegalStateException(
                    "국가·지역 기준 데이터를 읽지 못했습니다: " + resourcePath, exception);
        }

        try {
            int inserted = seedService.insertMissing(flatList);
            log.info("Country category seed data loaded: total={}, inserted={}",
                    flatList.size(), inserted);
        } catch (RuntimeException exception) {
            // 트랜잭션이 이미 되돌렸으므로 절반만 들어간 상태는 남지 않는다.
            log.error("Country category seed data could not be stored: total={}, exceptionType={}",
                    flatList.size(), exception.getClass().getSimpleName(), exception);
            throw new IllegalStateException("국가·지역 기준 데이터를 저장하지 못했습니다.", exception);
        }
    }

    /** JSON 을 읽어 트리 그대로 돌려준다. 스트림은 읽고 나면 바로 닫는다. */
    private List<CountryCategory> readCategories() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("기준 데이터 파일을 찾을 수 없습니다: " + resourcePath);
            }
            return objectMapper.readValue(input, new TypeReference<>() {});
        }
    }

    // 트리 구조 -> 평탄화
    private List<CountryCategory> flatten(List<CountryCategory> list) {
        List<CountryCategory> result = new ArrayList<>();
        for (CountryCategory item : list) {
            result.add(item);
            if (item.getChildren() != null && !item.getChildren().isEmpty()) {
                result.addAll(flatten(item.getChildren()));
            }
        }
        return result;
    }
}
