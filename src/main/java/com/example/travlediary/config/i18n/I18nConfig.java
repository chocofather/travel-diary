package com.example.travlediary.config.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

    private final MessageSource messageSource;

    public I18nConfig(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new TravelDiaryLocaleResolver();
    }

    /**
     * Bean Validation 메시지도 messages 번들에서 찾게 한다.
     * {signup.error.xxx} 처럼 중괄호로 적은 메시지만 번들에서 해석되고,
     * 기존처럼 문자열을 그대로 적어 둔 메시지는 영향을 받지 않는다.
     */
    @Override
    public Validator getValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
