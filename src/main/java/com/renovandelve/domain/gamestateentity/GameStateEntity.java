package com.renovandelve.domain.gamestateentity;

import com.renovandelve.domain.*;
import com.renovandelve.engine.Alignment;
import com.renovandelve.engine.Faction;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "game_state", indexes = {
        @Index(name = "idx_game_state_member_member_id", columnList = "member_id")
})
public class GameStateEntity { //제외
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long gameStateId; // 게임 세션 고유 ID (PK)

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Difficulty difficulty = Difficulty.NORMAL; // 난이도 (EASY / NORMAL / HARD / HELL)

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true; // 진행 중 여부 (종료 시 FALSE)

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int currentStage = 1; // 현재 스테이지 번호

    @PositiveOrZero
    private Long currentNodeId; // 현재 위치 노드

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int score = 0; // 누적 점수

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int gold = 0; // 보유 골드

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int startingPoints = 0; // 사용한 스타팅 포인트

    @Builder.Default
    @Column(nullable = false)
    private Boolean hasPhoenix = false; // 불사조의 깃털 흭득 여부 (게임 내 1회)

    // 성향: 조우 이벤트에서 각 세력의 방식을 택한 횟수 (기존 행을 위해 DB 기본값 0)
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int heavenAffinity = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int abyssAffinity = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int humanAffinity = 0;

    @Column(length = 120)
    private String pacts; // 이번 원정에 건 서약 (Pact 이름을 쉼표로 구분). 없으면 NULL

    @Column(length = 20)
    private String result; // 종료 결과 (CLEAR / DEFEAT / ABANDON). 진행 중이거나 과거 기록이면 NULL

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 세션 시작 일시

    @Column(nullable = false)
    private LocalDateTime updatedAt; // 마지막 저장 일시

    /**
     * 골드 차감 도메인 메서드
     * @param amount 차감할 골드량
     */
    public void deductGold(int amount) {
        if (this.gold < amount) {
            throw new IllegalArgumentException("골드가 부족합니다. 보유: " + this.gold + ", 필요: " + amount);
        }
        this.gold -= amount;
    }

    /**
     * 골드 / 점수 획득 도메인 메서드
     */
    public void gainGold(int amount) {
        this.gold = Math.max(0, this.gold + amount);
    }

    public void gainScore(int amount) {
        this.score = Math.max(0, this.score + amount);
    }

    /**
     * 다음 노드로 이동. 10노드를 넘기면 다음 스테이지 1노드로 이동한다.
     */
    public void moveToNextNode() {
        long node = this.currentNodeId == null ? 0L : this.currentNodeId;
        if (node >= 10) {
            this.currentStage += 1;
            this.currentNodeId = 1L;
        } else {
            this.currentNodeId = node + 1;
        }
    }

    /**
     * 세션 종료 (게임 오버 / 클리어 공통)
     */
    public void deactivate() {
        this.isActive = false;
        // 클리어/패배로 끝난 게임은 finish()가 먼저 결과를 기록한다. 그 외(포기, 새 게임 시작, 강제 종료)는 ABANDON
        if (this.result == null) {
            this.result = "ABANDON";
        }
    }

    /**
     * 성향 누적: 조우 이벤트에서 해당 세력의 방식을 택했다
     */
    public void gainAffinity(Faction faction) {
        if (faction == null) return;
        switch (faction) {
            case HEAVEN -> this.heavenAffinity++;
            case ABYSS -> this.abyssAffinity++;
            case HUMAN -> this.humanAffinity++;
        }
    }

    public Alignment alignment() {
        return new Alignment(heavenAffinity, abyssAffinity, humanAffinity);
    }

    /**
     * 클리어 / 패배 결과 기록 (모험 기록 화면용)
     */
    public void finish(boolean cleared) {
        this.result = cleared ? "CLEAR" : "DEFEAT";
        this.isActive = false;
    }

    /**
     * 불사조의 깃털 획득 / 소모
     */
    public void obtainPhoenix() {
        this.hasPhoenix = true;
    }

    public void consumePhoenix() {
        this.hasPhoenix = false;
    }

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 생명주기 도메인 메서드 (UPDATE 직전에 값을 기록하고 넣어주는 역할)
    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /* FK (부모) */
    @ToString.Exclude
    @OneToMany(mappedBy = "gameState")
    private List<RankingEntity> ranking;

    @ToString.Exclude
    @OneToMany(mappedBy = "gameState")
    private List<GameSkillSlotEntity> gameSkillSlot;

    @ToString.Exclude
    @OneToMany(mappedBy = "gameState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameCharacterEntity> gameCharacter;

    @ToString.Exclude
    @OneToOne(mappedBy = "gameState")
    private GameEquipmentEntity gameEquipment;

    @ToString.Exclude
    @OneToMany(mappedBy = "gameState")
    private List<GameRelicInventoryEntity> gameRelicInventory;

    @ToString.Exclude
    @OneToMany(mappedBy = "gameState")
    private List<GameConsumableInventoryEntity> gameConsumableInventory;

    /* FK (자식) */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_state_member_memberId"))
    private MemberEntity member; // 회원 ID (FK)
}
