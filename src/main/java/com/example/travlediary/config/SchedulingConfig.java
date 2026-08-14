package com.example.travlediary.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 기간제한 자동 만료 등 주기 작업을 위한 스케줄링 활성화. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}