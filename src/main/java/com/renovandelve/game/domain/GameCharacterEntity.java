package com.renovandelve.game.domain;

import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "game_character", indexes = {
        @Index(name = "idx_game_character_game_state_game_state_id", columnList = "game_state_id")
})
public class GameCharacterEntity { //제외
    /** 장비 보너스를 더하기 전의 기본 최대 HP */
    public static final int BASE_MAX_HP = 100;

    /** 장비 보너스를 더하기 전의 기본 공격력 */
    public static final int BASE_ATTACK = 10;

    /** 장비 보너스를 더하기 전의 기본 방어력 */
    public static final int BASE_DEFENSE = 5;

    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long characterId; // 슬릇 고유 ID

    @PositiveOrZero
    @Column(nullable = false)
    @Builder.Default
    private int level = 1; // 캐릭터 레벨

    @PositiveOrZero
    @Column(nullable = false)
    private int currentHp; // 현재 HP

    @PositiveOrZero
    @Column(nullable = false)
    private int maxHp; // 최대 HP (레벨당 10 + 보너스)

    @PositiveOrZero
    @Column(nullable = false)
    private int attack; // 캐릭터 공격력 (레벨당 1 + 보너스)

    @PositiveOrZero
    @Column(nullable = false)
    private int defense; // 캐릭터 방어력 (레벨당 1 + 보너스)

    @PositiveOrZero
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    @Builder.Default
    private int exp = 0; // 누적 경험치 (레벨업 임계값 = level * 100)

    /* FK 자식 */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_state_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_character_game_state_game_state_id"))
    private GameStateEntity gameState; // 게임 세션 ID (FK)

    /**
     * 원정을 시작할 때의 캐릭터. 기본 능력치에 장비 보너스를 더하고 HP는 가득 찬 상태로 시작한다.
     *
     * @param gameState 게임
     * @param equipment 시작 장비
     * @return 레벨 1 캐릭터
     */
    public static GameCharacterEntity start(GameStateEntity gameState, GameEquipmentEntity equipment) {
        int maxHp = BASE_MAX_HP + equipment.helmetBonus();
        return GameCharacterEntity.builder()
                .gameState(gameState)
                .maxHp(maxHp)
                .currentHp(maxHp)
                .attack(BASE_ATTACK + equipment.weaponBonus())
                .defense(BASE_DEFENSE + equipment.armorBonus())
                .build();
    }

    /**
     * 피해 적용 (0 미만 방지)
     */
    public void takeDamage(int damage) {
        this.currentHp = Math.max(0, this.currentHp - Math.max(0, damage));
    }

    /**
     * 회복 (최대 HP 초과 방지)
     */
    public void heal(int amount) {
        this.currentHp = Math.min(this.maxHp, this.currentHp + Math.max(0, amount));
    }

    /**
     * 부활 (불사조의 깃털): 최대 HP의 50%로 복귀
     */
    public void revive() {
        this.currentHp = Math.max(1, this.maxHp / 2);
    }

    /**
     * 장비 교체에 따른 스탯 보정 (이전 보너스 제거 + 새 보너스 적용)
     */
    public void applyAttackDelta(int delta) {
        this.attack = Math.max(1, this.attack + delta);
    }

    /**
     * 방어력을 올리거나 내린다 (장비 교체, 소모품, 모닥불 단련). 0 아래로는 내려가지 않는다.
     *
     * @param delta 바꿀 양 (음수면 감소)
     */
    public void applyDefenseDelta(int delta) {
        this.defense = Math.max(0, this.defense + delta);
    }

    /**
     * 최대 HP를 올리거나 내린다. 줄어들면 현재 HP도 새 최대치에 맞춰 깎는다. 최소 1.
     *
     * @param delta 바꿀 양 (음수면 감소)
     */
    public void applyMaxHpDelta(int delta) {
        this.maxHp = Math.max(1, this.maxHp + delta);
        this.currentHp = Math.min(this.currentHp, this.maxHp);
    }

    /**
     * 경험치 획득 + 레벨업 처리
     * @return 상승한 레벨 수
     */
    public int gainExp(int amount) {
        this.exp += Math.max(0, amount);
        int levelUps = 0;
        while (this.exp >= this.level * 100) {
            this.exp -= this.level * 100;
            this.level += 1;
            this.maxHp += 15;
            this.attack += 3;
            this.defense += 2;
            heal((int) (this.maxHp * 0.3));
            levelUps++;
        }
        return levelUps;
    }
}
