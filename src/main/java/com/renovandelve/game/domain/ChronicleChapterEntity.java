package com.renovandelve.game.domain;

import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.engine.Faction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 레노바 연대기의 한 장. 끝난 원정 하나가 한 장이 된다.
 * 본문은 AI가 앞선 장들을 이어 쓰고, 결과·도달 지점·세력 같은 사실은 서버가 채운다.
 */
@Getter
@Entity
@Table(name = "chronicle_chapter",
        uniqueConstraints = @UniqueConstraint(name = "uk_chronicle_game_state", columnNames = "game_state_id"),
        indexes = @Index(name = "idx_chronicle_member", columnList = "member_id, chapter_no"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChronicleChapterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chapterId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "game_state_id", nullable = false)
    private Long gameStateId;

    @Column(nullable = false)
    private int chapterNo;

    @Column(length = 40, nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean cleared;

    @Column(nullable = false)
    private short stage;

    @Column(nullable = false)
    private short node;

    @Column(length = 10)
    private String faction;

    @Column(length = 20, nullable = false)
    private String difficulty;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChronicleChapterEntity(Long memberId, Long gameStateId, int chapterNo, String title, String body,
                                   boolean cleared, int stage, int node, String faction, String difficulty) {
        this.memberId = memberId;
        this.gameStateId = gameStateId;
        this.chapterNo = chapterNo;
        this.title = title;
        this.body = body;
        this.cleared = cleared;
        this.stage = (short) stage;
        this.node = (short) node;
        this.faction = faction;
        this.difficulty = difficulty;
    }

    /**
     * 끝난 원정으로 연대기의 한 장을 쓴다. 스테이지·노드·세력·난이도는 원정에서 읽는다.
     *
     * @param gameState 끝난 원정
     * @param chapterNo 몇 번째 장인지 (1부터)
     * @param cleared   클리어했으면 true
     * @param title     장 제목
     * @param body      본문
     * @return 새 장
     */
    public static ChronicleChapterEntity write(GameStateEntity gameState, int chapterNo, boolean cleared,
                                               String title, String body) {
        Faction faction = gameState.alignment().dominant();
        return ChronicleChapterEntity.builder()
                .memberId(gameState.getMember().getMemberId())
                .gameStateId(gameState.getGameStateId())
                .chapterNo(chapterNo)
                .title(title)
                .body(body)
                .cleared(cleared)
                .stage(gameState.getCurrentStage())
                .node(gameState.getCurrentNodeId() == null ? 0 : gameState.getCurrentNodeId().intValue())
                .faction(faction == null ? null : faction.name())
                .difficulty(gameState.getDifficulty().name())
                .build();
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
