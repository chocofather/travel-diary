package com.example.travlediarybackfill;

import com.example.travlediary.config.MyBatisConfig;
import com.example.travlediary.service.translation.DestinationCommentLanguageBackfillResult;
import com.example.travlediary.service.translation.DestinationCommentLanguageBackfillService;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
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
        LocalContentLanguageDetector.class,
        DestinationCommentLanguageBackfillService.class
})
public class DestinationCommentLanguageBackfillApplication {
    private static final Logger log =
            LoggerFactory.getLogger(DestinationCommentLanguageBackfillApplication.class);

    public static void main(String[] args) {
        SpringApplication application =
                new SpringApplication(DestinationCommentLanguageBackfillApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            DestinationCommentLanguageBackfillResult result = context
                    .getBean(DestinationCommentLanguageBackfillService.class)
                    .run();
            log.info("댓글 원문 언어 backfill 완료: 조회 {}, 갱신 {}, 미확정 {}",
                    result.scanned(), result.updated(), result.undetermined());
        }
    }
}
