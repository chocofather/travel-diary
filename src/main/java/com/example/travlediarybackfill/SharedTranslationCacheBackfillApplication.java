package com.example.travlediarybackfill;

import com.example.travlediary.config.MyBatisConfig;
import com.example.travlediary.service.translation.DestinationCommentTranslationSourceReader;
import com.example.travlediary.service.translation.PostCommentTranslationSourceReader;
import com.example.travlediary.service.translation.SharedTranslationCacheBackfillResult;
import com.example.travlediary.service.translation.SharedTranslationCacheBackfillService;
import com.example.travlediary.service.translation.TranslationProviderMetadata;
import com.example.travlediary.service.translation.TranslationSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        MyBatisConfig.class,
        DestinationCommentTranslationSourceReader.class,
        PostCommentTranslationSourceReader.class,
        TranslationSourceRegistry.class,
        TranslationProviderMetadata.class,
        SharedTranslationCacheBackfillService.class
})
public class SharedTranslationCacheBackfillApplication {
    private static final Logger log =
            LoggerFactory.getLogger(SharedTranslationCacheBackfillApplication.class);

    public static void main(String[] args) {
        SpringApplication application =
                new SpringApplication(SharedTranslationCacheBackfillApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            SharedTranslationCacheBackfillResult result = context
                    .getBean(SharedTranslationCacheBackfillService.class)
                    .run();
            log.info("공용 번역 캐시 backfill 완료: 조회 {}, 삽입 {}, 건너뜀 {}",
                    result.scanned(), result.inserted(), result.skipped());
        }
    }
}
