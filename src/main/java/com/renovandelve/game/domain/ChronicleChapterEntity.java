package com.renovandelve.game.domain;

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

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
