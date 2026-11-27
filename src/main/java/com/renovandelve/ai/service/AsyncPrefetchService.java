package com.renovandelve.ai.service;

import com.renovandelve.ai.dto.response.AsyncStageResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncPrefetchService {
    private final MonsterRagService monsterRagService;
    private final StageCacheService stageCacheService;

    public AsyncPrefetchService(@Lazy MonsterRagService monsterRagService, StageCacheService stageCacheService) {
        this.monsterRagService = monsterRagService;
        this.stageCacheService = stageCacheService;
    }

    /**
     * 다음 노드의 스토리를 저장 (비동기)
     * @param stage 스테이지
     * @param nextNode 다음 노드
     * @param conversationId 사용자
     */
    @Async
    public void prefetchNextStage(int stage, int nextNode, String conversationId) {
        try {
            log.info("다음 노드 프리페치 : 스테이지 : {}, 노드 : {} ", stage, nextNode);
            AsyncStageResponseDTO result = monsterRagService.generateNextStory(stage, nextNode, conversationId);
            stageCacheService.save(conversationId, stage, nextNode, result);
        } catch (Exception e) {
            log.error("프리패치 실패 스테이지 : {}, 노드 : {}, 이유 : {} ", stage, nextNode, e.getMessage());
        }
    }

}
