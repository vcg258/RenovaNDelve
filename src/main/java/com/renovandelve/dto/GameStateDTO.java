package com.renovandelve.dto;

import com.renovandelve.domain.gamestateentity.Difficulty;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class GameStateDTO {
    private Long gameStateId; // 게임 세션 고유 ID (PK)
    private Difficulty difficulty; // 난이도 (EASY / NORMAL / HARD / HELL)
    private Boolean isActive; // 진행 중 여부 (종료 시 FALSE)
    private int currentStage; // 현재 스테이지 번호
    private Long currentNodeId; // 현재 위치 노드
    private int score; // 누적 점수
    private int gold; // 보유 골드
    private int startingPoints; // 사용한 스타팅 포인트
    private Boolean hasPhoenix; // 불사조의 깃털 흭득 여부 (게임 내 1회)
    private LocalDateTime createdAt; // 세션 시작 일시
    private LocalDateTime updatedAt; // 마지막 저장 일시
    private MemberDTO member; // 회원 ID (FK)
}
