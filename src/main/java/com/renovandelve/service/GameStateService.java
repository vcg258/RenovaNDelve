package com.renovandelve.service;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.dto.request.NewGameRequest;
import com.renovandelve.dto.response.SetupOptionsDTO;

import java.util.Optional;

public interface GameStateService {

    /**
     * 새 게임 세팅 화면용 선택지 목록 (장비 / 스킬 / 유물 + 예산)
     */
    SetupOptionsDTO getSetupOptions(MemberEntity member);

    /**
     * 새 게임 시작. 기존 진행 중 게임은 자동 종료 처리.
     * 스타팅 포인트 예산 검증은 서버에서 수행한다.
     */
    GameStateEntity createNewGame(NewGameRequest request, MemberEntity member);

    /**
     * 진행 중인 게임 조회 (이어하기)
     */
    Optional<GameStateEntity> findActiveGame(MemberEntity member);

    /**
     * 진행 중인 게임 포기 (isActive = false)
     */
    void abandonGame(MemberEntity member);
}
