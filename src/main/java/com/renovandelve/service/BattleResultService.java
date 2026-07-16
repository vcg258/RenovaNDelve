package com.renovandelve.service;

import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.dto.RankingDTO;
import com.renovandelve.dto.response.IllustratedEncyclopediaDTO;

public interface BattleResultService {

    /**
     * 이번 게임에서 사용/획득한 아이템을 회원 도감에 등록
     */
    IllustratedEncyclopediaDTO registerCollection(GameStateEntity gameState);

    /**
     * 랭킹 갱신 (EASY 난이도는 등록하지 않음)
     * @param clearTime 클리어 시간(초). 클리어 실패면 null
     */
    RankingDTO updateRanking(GameStateEntity gameState, Integer clearTime);

    /**
     * 게임 종료 공통 처리: 도감 등록 + 랭킹 갱신 + 세션 비활성화
     * @param cleared 클리어 여부
     */
    IllustratedEncyclopediaDTO terminateGame(GameStateEntity gameState, boolean cleared);
}
