package com.renovandelve.domain.gamestateentity;

import com.renovandelve.domain.*;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.engine.Alignment;
import com.renovandelve.engine.Faction;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "game_state", indexes = {
        @Index(name = "idx_game_state_member_member_id", columnList = "member_id")
})
public class GameStateEntity { //제외

    /*
     * 애그리거트 루트.
     * 한 판의 스킬 슬롯·유물·소모품은 이 게임에 딸린 것이므로, 넣고 빼는 일은 모두 이 엔티티의 메서드로만 한다.
     * 컬렉션은 cascade + orphanRemoval로 매핑해, 목록에서 빼면 DB에서도 지워지고 목록에 넣으면 함께 저장된다.
     * (예전에는 저장소와 목록을 따로 바꿔, 사용한 포션이 응답에 남는 버그가 있었다)
     */

    /** 스킬 슬롯 최대 개수 */
    public static final int MAX_SKILL_SLOTS = 8;
    /** 기본 공격은 스킬을 이만큼(기본 공격 포함) 가졌을 때만 버리거나 바꿀 수 있다 */
    public static final int MIN_SLOTS_TO_DROP_BASIC = 3;
    public static final String SKILL_FULL_MESSAGE =
            "스킬 슬롯이 가득 찼습니다. 버릴 스킬을 골라주세요. (최대 " + MAX_SKILL_SLOTS + "개)";
    public static final String BASIC_LOCKED_MESSAGE =
            "기본 공격은 스킬이 " + MIN_SLOTS_TO_DROP_BASIC + "개 이상일 때만 버릴 수 있습니다.";
    public static final String LAST_SKILL_MESSAGE = "마지막 남은 스킬은 버릴 수 없습니다.";
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

    /**
     * 점수를 더한다. 0 아래로는 내려가지 않는다.
     *
     * @param amount 더할 점수 (음수면 감소)
     */
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

    /**
     * 지금까지 쌓은 성향으로 가호 상태를 계산한다.
     *
     * @return 성향과 가호
     */
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

    /**
     * 불사조의 깃털을 써서 없앤다 (쓰러졌을 때 되살아나며 소모).
     */
    public void consumePhoenix() {
        this.hasPhoenix = false;
    }

    /* ==================== 스킬 슬롯 ==================== */

    /**
     * 이 스킬을 이미 가지고 있는지 확인한다.
     *
     * @param skillId 스킬 ID
     * @return 슬롯 중 하나에 있으면 true
     */
    public boolean hasSkill(Long skillId) {
        return gameSkillSlot.stream()
                .anyMatch(slot -> slot.getSkill() != null && slot.getSkill().getSkillId().equals(skillId));
    }

    /**
     * 이 게임의 슬롯 중에서 찾는다. 남의 게임 슬롯 ID를 넘겨도 찾지 못한다.
     *
     * @param slotId 슬롯 ID
     * @return 슬롯
     * @throws IllegalArgumentException 이 게임의 슬롯이 아니면
     */
    public GameSkillSlotEntity findSkillSlot(Long slotId) {
        return gameSkillSlot.stream()
                .filter(slot -> slotId.equals(slot.getSlotId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 스킬 슬롯입니다. slotId=" + slotId));
    }

    /**
     * 빈 슬롯에 새 스킬을 넣는다. 슬롯 순번은 비어 있는 가장 앞 번호를 쓴다.
     *
     * @param skill 배울 스킬
     * @return 새로 만든 슬롯 (저장은 cascade로 함께 된다)
     * @throws IllegalStateException 이미 가진 스킬이거나 슬롯이 가득 찼으면
     */
    public GameSkillSlotEntity learnSkill(SkillEntity skill) {
        requireNotOwned(skill);
        if (gameSkillSlot.size() >= MAX_SKILL_SLOTS) {
            throw new IllegalStateException(SKILL_FULL_MESSAGE);
        }
        int nextIndex = 0;
        while (slotIndexUsed(nextIndex)) nextIndex++;

        GameSkillSlotEntity slot = GameSkillSlotEntity.of(this, skill, nextIndex);
        gameSkillSlot.add(slot);
        return slot;
    }

    /**
     * 기존 슬롯의 스킬을 새 스킬로 바꾼다 (가득 찼을 때 버릴 스킬을 골라 배우기).
     *
     * @param slotId 버릴 스킬이 든 슬롯
     * @param skill  새로 배울 스킬
     * @return 바뀐 슬롯
     * @throws IllegalStateException 이미 가진 스킬이거나 기본 공격을 버릴 수 없는 상태면
     */
    public GameSkillSlotEntity replaceSkill(Long slotId, SkillEntity skill) {
        requireNotOwned(skill);
        GameSkillSlotEntity slot = findSkillSlot(slotId);
        requireDroppable(slot);
        slot.changeSkill(skill);
        return slot;
    }

    /**
     * 스킬 하나를 버린다. 목록에서 빠진 슬롯은 orphanRemoval로 DB에서도 지워진다.
     *
     * @param slotId 버릴 스킬이 든 슬롯
     * @return 버린 스킬
     * @throws IllegalStateException 마지막 한 개이거나 기본 공격을 버릴 수 없는 상태면
     */
    public SkillEntity forgetSkill(Long slotId) {
        GameSkillSlotEntity slot = findSkillSlot(slotId);
        if (gameSkillSlot.size() <= 1) {
            throw new IllegalStateException(LAST_SKILL_MESSAGE);
        }
        requireDroppable(slot);
        gameSkillSlot.remove(slot);
        return slot.getSkill();
    }

    /**
     * 모든 스킬의 쿨타임을 비운다 (전투가 끝났을 때).
     */
    public void resetSkillCooldowns() {
        gameSkillSlot.forEach(GameSkillSlotEntity::resetCooldown);
    }

    /**
     * 이번 턴에 쓸 수 있는 스킬이 하나라도 있는지 확인한다.
     *
     * @return 쿨타임이 없는 스킬이 있으면 true
     */
    public boolean hasReadySkill() {
        return gameSkillSlot.stream().anyMatch(GameSkillSlotEntity::isReady);
    }

    /** 기본 공격은 쿨타임이 없는 유일한 스킬이라, 스킬이 적을 때 버리면 매 턴 쓸 스킬이 없어진다 */
    private void requireDroppable(GameSkillSlotEntity slot) {
        if (slot.getSkill() != null && slot.getSkill().isBasic() && gameSkillSlot.size() < MIN_SLOTS_TO_DROP_BASIC) {
            throw new IllegalStateException(BASIC_LOCKED_MESSAGE);
        }
    }

    /**
     * 이미 가진 스킬이면 거부한다.
     *
     * @param skill 배우려는 스킬
     * @throws IllegalStateException 이미 가진 스킬이면
     */
    private void requireNotOwned(SkillEntity skill) {
        if (hasSkill(skill.getSkillId())) {
            throw new IllegalStateException("이미 보유 중인 스킬입니다.");
        }
    }

    /**
     * 그 슬롯 순번을 이미 쓰고 있는지 확인한다.
     *
     * @param index 슬롯 순번
     * @return 쓰고 있으면 true
     */
    private boolean slotIndexUsed(int index) {
        return gameSkillSlot.stream().anyMatch(slot -> slot.getSlotIndex() == index);
    }

    /* ==================== 유물 ==================== */

    /**
     * 이 유물을 이미 가지고 있는지 확인한다.
     *
     * @param relicId 유물 ID
     * @return 가지고 있으면 true
     */
    public boolean ownsRelic(Long relicId) {
        return gameRelicInventory.stream().anyMatch(inventory -> inventory.getRelic().getRelicId().equals(relicId));
    }

    /**
     * 유물을 얻는다. 같은 유물은 두 번 가질 수 없다.
     *
     * @param relic 얻을 유물
     * @return 새 인벤토리 칸 (저장은 cascade로 함께 된다)
     * @throws IllegalStateException 이미 가진 유물이면
     */
    public GameRelicInventoryEntity addRelic(RelicEntity relic) {
        if (ownsRelic(relic.getRelicId())) {
            throw new IllegalStateException("이미 보유 중인 유물입니다.");
        }
        GameRelicInventoryEntity inventory = GameRelicInventoryEntity.of(this, relic);
        gameRelicInventory.add(inventory);
        return inventory;
    }

    /* ==================== 소모품 ==================== */

    /**
     * 소모품을 얻는다. 같은 소모품도 여러 개 가질 수 있다.
     *
     * @param consumable 얻을 소모품
     * @return 새 인벤토리 칸 (저장은 cascade로 함께 된다)
     */
    public GameConsumableInventoryEntity addConsumable(ConsumableEntity consumable) {
        GameConsumableInventoryEntity inventory = GameConsumableInventoryEntity.of(this, consumable);
        gameConsumableInventory.add(inventory);
        return inventory;
    }

    /**
     * 이 게임의 소모품 칸 중에서 찾는다.
     *
     * @param slotId 인벤토리 칸 ID
     * @return 소모품 칸
     * @throws IllegalArgumentException 이 게임의 소모품이 아니거나 이미 쓴 것이면
     */
    public GameConsumableInventoryEntity findConsumable(Long slotId) {
        return gameConsumableInventory.stream()
                .filter(inventory -> inventory.getSlotId().equals(slotId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 소모품입니다."));
    }

    /**
     * 효과 종류로 소모품 하나를 찾는다 (예: 쓰러졌을 때 불사조의 깃털).
     *
     * @param effectType 효과 종류
     * @return 가장 먼저 얻은 해당 소모품, 없으면 빈 값
     */
    public Optional<GameConsumableInventoryEntity> findConsumableByEffect(
            com.renovandelve.domain.consumableentity.Type effectType) {
        return gameConsumableInventory.stream()
                .filter(inventory -> inventory.getConsumable().getEffectType() == effectType)
                .findFirst();
    }

    /**
     * 소모품을 써서 없앤다. 목록에서 빠진 칸은 orphanRemoval로 DB에서도 지워진다.
     *
     * @param inventory 쓸 소모품 칸
     * @throws NoSuchElementException 이 게임의 소모품이 아니면
     */
    public void consume(GameConsumableInventoryEntity inventory) {
        if (!gameConsumableInventory.remove(inventory)) {
            throw new NoSuchElementException("보유하지 않은 소모품입니다.");
        }
    }

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    /**
     * 처음 저장될 때 생성·수정 일시를 기록한다 (JPA 생명주기 콜백).
     */
    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 생명주기 도메인 메서드 (UPDATE 직전에 값을 기록하고 넣어주는 역할)
    /**
     * 수정될 때 수정 일시를 갱신한다 (JPA 생명주기 콜백).
     */
    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /* FK (부모) */
    @ToString.Exclude
    @OneToMany(mappedBy = "gameState")
    private List<RankingEntity> ranking;

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "gameState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameSkillSlotEntity> gameSkillSlot = new ArrayList<>();

    @ToString.Exclude
    @OneToMany(mappedBy = "gameState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameCharacterEntity> gameCharacter;

    @ToString.Exclude
    @OneToOne(mappedBy = "gameState")
    private GameEquipmentEntity gameEquipment;

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "gameState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameRelicInventoryEntity> gameRelicInventory = new ArrayList<>();

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "gameState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameConsumableInventoryEntity> gameConsumableInventory = new ArrayList<>();

    /* FK (자식) */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_state_member_memberId"))
    private MemberEntity member; // 회원 ID (FK)
}
