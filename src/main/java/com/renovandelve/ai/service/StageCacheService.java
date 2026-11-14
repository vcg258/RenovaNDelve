package com.renovandelve.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renovandelve.ai.dto.response.AsyncStageResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StageCacheService {
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final Long TTL_MINUTES = 60L; // 상점/보상에서 오래 머물러도 프리페치가 증발하지 않도록 여유있게

    /**
     * 다음 노드의 정보를 레디스에 저장
     * @param conversationId 사용자 아이디
     * @param stage 스테이지
     * @param node 노드
     * @param asyncStageResponseDTO 스테이지 정보 (전투, 선택지)
     */
    public void save(String conversationId, int stage, int node, AsyncStageResponseDTO asyncStageResponseDTO) {
        String key = conversationId + ":" + stage + ":" + node;
        log.info("레디스 다음스테이지 정보 저장 : {}", key);
        redisTemplate.opsForValue().set(key, asyncStageResponseDTO, TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 다음 노드의 정보를 레디스에서 조회
     * @param conversationId 사용자 아이디
     * @param stage 스테이지
     * @param node 노드
     * @return 스테이지 정보
     */
    public AsyncStageResponseDTO get(String conversationId, int stage, int node) {
        Object value = redisTemplate.opsForValue().get(conversationId + ":" + stage + ":" + node);
        if (value == null) return null;
        log.info("레디스 다음 스테이지 정보 조회 : {}", value);
        return objectMapper.convertValue(value, AsyncStageResponseDTO.class);
    }

    /**
     * 레디스에 스테이지 정보 제거
     * @param conversationId 사용자 아이디
     * @param stage 스테이지
     * @param node 노드
     */
    public void delete(String conversationId, int stage, int node) {
        log.info("레디스 값 제거");
        redisTemplate.delete(conversationId + ":" + stage + ":" + node);
    }
}
