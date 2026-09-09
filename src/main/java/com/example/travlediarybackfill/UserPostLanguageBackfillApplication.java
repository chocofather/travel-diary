package com.example.travlediarybackfill;

import com.example.travlediary.config.MyBatisConfig;
import com.example.travlediary.service.post.PostContentSanitizer;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
import com.example.travlediary.service.translation.UserPostLanguageBackfillResult;
import com.example.travlediary.service.translation.UserPostLanguageBackfillService;
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
        PostContentSanitizer.class,
        UserPostLanguageBackfillService.class
})
public class UserPostLanguageBackfillApplication {
    private static final Logger log =
            LoggerFactory.getLogger(UserPostLanguageBackfillApplication.class);

    public static void main(String[] args) {
        SpringApplication application =
                new SpringApplication(UserPostLanguageBackfillApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            UserPostLanguageBackfillResult result = context
                    .getBean(UserPostLanguageBackfillService.class)
                    .run();
            log.info("사용자 게시글 원문 언어 backfill 완료: 조회 {}, 제목 갱신 {}, 본문 갱신 {}, 미확정 {}",
                    result.scanned(), result.titleUpdated(), result.contentUpdated(),
                    result.undeterminedFields());
        }
    }
}
