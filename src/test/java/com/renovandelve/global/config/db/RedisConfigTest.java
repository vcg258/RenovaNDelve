package com.renovandelve.global.config.db;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisConfigTest {
    @Autowired private RedisTemplate<String, String> redisTemplate;

    @Test
    void redisTemplate() {
        ValueOperations<String, String> operations = redisTemplate.opsForValue();
        String key = "name";
        operations.set(key, "test");
        String value = operations.get(key);
        Assertions.assertEquals("test", value);
    }

}