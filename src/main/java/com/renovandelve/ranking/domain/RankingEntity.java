package com.renovandelve.ranking.domain;

import com.renovandelve.member.domain.MemberEntity;

import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ranking",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "difficulty"}), // TODO 같은 회원 동일 난이도 중복 삽입 방지로 넣음 의논 필요
        indexes = {
        @Index(name = "idx_ranking_member_member_id", columnList = "member_id"), // TODO 위 의논 거리 확정나면 제거
        @Index(name = "idx_ranking_game_state_game_state_id", columnList = "game_state_id")
})
public class RankingEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long rankingId; // 랭킹 레코드 고유 ID

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Difficulty difficulty; // 게임 난이도 (NORMAL / HARD / HELL)

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int bestScore = 0; // 해당 난이도 최고 점수 (배율 적용 후 점수)

    @PositiveOrZero
    private Integer clearTime; // 클리어 시간 (초 단위) 클리어 실패 NULL

    @Column(nullable = false, updatable = false)
    private LocalDateTime achievedAt; // 기록 달성 일시

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    /**
     * 처음 저장될 때 기록 달성 일시를 남긴다 (JPA 생명주기 콜백).
     */
    @PrePersist
    private void onCreate() {
        this.achievedAt = LocalDateTime.now();
    }

    /* FK (자식) */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ranking_member_memberId"))
    private MemberEntity member; // 회원 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_state_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ranking_game_state_game_state_id"))
    private GameStateEntity gameState; // 게임 세션 ID (FK)
}
