package com.renovandelve.dto.response;

import com.renovandelve.dto.MonsterDTO;

import java.util.List;

/**
 * AI(RAG)가 생성한 노드 정보. Redis 프리페치 캐시에도 이 형태로 저장된다.
 * story = LLM이 생성한 현재 노드의 서사 텍스트
 */
public record AsyncStageResponseDTO(
        String type,               // "BATTLE" or "ENCOUNTER"
        String story,              // 노드 서사
        List<MonsterDTO> monsters, // 전투일 때
        Encounter encounter        // 인카운터일 때
) {
    /**
     * 전투 노드를 만든다.
     *
     * @param story    노드 서사
     * @param monsters 출현할 몬스터
     * @return 전투 노드
     */
    public static AsyncStageResponseDTO battle(String story, List<MonsterDTO> monsters) {
        return new AsyncStageResponseDTO("BATTLE", story, monsters, null);
    }

    /**
     * 조우 노드를 만든다.
     *
     * @param story     노드 서사
     * @param encounter 상황과 선택지 문구
     * @return 조우 노드
     */
    public static AsyncStageResponseDTO encounter(String story, Encounter encounter) {
        return new AsyncStageResponseDTO("ENCOUNTER", story, null, encounter);
    }
}
