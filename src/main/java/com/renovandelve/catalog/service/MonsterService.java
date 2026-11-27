package com.renovandelve.catalog.service;

import com.renovandelve.catalog.dto.MonsterDTO;

import java.util.List;

public interface MonsterService {

    /**
     * 몬스터 이름으로 일반 몬스터 조회(LLM이 스토리와 테마에 알맞은 몬스터를 출현시키기 떄문)
     * @param name 몬스터 이름
     * @return 일반 몬스터
     */
    List<MonsterDTO> getMonsterByNameNormal(List<String> name);

    /**
     * 몬스터 이름으로 보스 몬스터 조회(LLM이 스토리와 테마에 알맞은 몬스터를 출현시키기 떄문)
     * @param name 몬스터 이름
     * @return 보스 몬스터
     */
    List<MonsterDTO> getMonsterByNameBoss(List<String> name);

    /**
     * RAG의 키워드에서 유사도가 너무 낮을경우 랜덤 일반 몬스터 출현할 메서드
     * @return 일반 몬스터
     */
    List<MonsterDTO> getRandomMonsterNormal();

    /**
     * RAG의 키워드에서 유사도가 너무 낮을경우 랜덤 보스 몬스터 출현할 메서드
     * @return 보스 몬스터
     */
    List<MonsterDTO> getRandomMonsterBoss();
}
