package com.renovandelve.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// 비동기 + 스케줄링(개인정보 보관 기간 경과분 파기) 활성화
@EnableAsync
@EnableScheduling
@Configuration
public class AsyncConfig {
}
