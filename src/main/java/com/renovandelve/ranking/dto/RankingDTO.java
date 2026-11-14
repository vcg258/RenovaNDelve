package com.renovandelve.ranking.dto;

import com.renovandelve.game.dto.GameStateDTO;
import com.renovandelve.member.dto.MemberDTO;

import com.renovandelve.game.domain.gamestateentity.Difficulty;
import java.time.LocalDateTime;

public record RankingDTO (
    Long rankingId, // 랭킹 레코드 고유 ID
    Difficulty difficulty, // 게임 난이도 (NORMAL / HARD / HELL)
    int bestScore, // 해당 난이도 최고 점수 (배율 적용 후 점수)
    Integer clearTime, // 클리어 시간 (초 단위) 클리어 실패 NULL
    LocalDateTime achievedAt, // 기록 달성 일시
    MemberDTO member, // 회원 ID (FK)
    GameStateDTO gameState // 게임 세션 ID (FK)
) {}
