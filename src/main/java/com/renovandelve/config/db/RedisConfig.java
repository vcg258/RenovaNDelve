package com.renovandelve.config.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Value("${spring.data.redis.host}")
    private String host;
    @Value("${spring.data.redis.port}")
    private int port;

    @Bean public RedisConnectionFactory redisConnectionFactory() {
        /* Spring Data Redis가 실제 Redis 서버와 통신할 때 사용할 연결 팩토리 */
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    public RedisTemplate<String, Object>
    redisTemplate() {
        // key 는 사람이 읽기 쉬운 문자열로 저장하고,value 는 JSON 으로 저장.
        // 이렇게 설정하면 DTO가 Serializable 을 구현하지 않아도 Redis 에 저장할 수 있음.

        // 1. ObjectMapper 설정
        // Redis value 를 JSON 으로 직렬화/역직렬화할 때사용할 ObjectMapper.
        ObjectMapper objectMapper = new ObjectMapper();
        // ObjectMapper 기반 JSON serializer를 RedisTemplate value serializer로 사용.
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        // 2. RedisTemplate 설정
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // 테스트 코드를 사용해서 정상적인 동작을 확인.
        template.setConnectionFactory(redisConnectionFactory());
        // Redis key 는 일반 문자열 형태로 저장.
        template.setKeySerializer(new StringRedisSerializer());
        // Redis value 는 JSON 문자열 기반으로 저장.
        template.setValueSerializer(serializer);
        // template 설정 적용 후 반환.
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "securityRedisTemplate")
    public RedisTemplate<String, String> stringRedisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
