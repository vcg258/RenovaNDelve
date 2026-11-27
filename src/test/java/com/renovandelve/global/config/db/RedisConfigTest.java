package com.renovandelve.global.config.db;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisConfigTest {
    @Autowired @Qualifier("securityRedisTemplate") private RedisTemplate<String, String> redisTemplate; // 문자열 템플릿이 두 개라 이름으로 고른다

    @Test
    void redisTemplate() {
        ValueOperations<String, String> operations = redisTemplate.opsForValue();
        String key = "name";
        operations.set(key, "test");
        String value = operations.get(key);
        Assertions.assertEquals("test", value);
    }

}