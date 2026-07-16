package com.renovandelve.domain;

import com.renovandelve.domain.gamestateentity.GameStateEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "game_character", indexes = {
        @Index(name = "idx_game_character_game_state_game_state_id", columnList = "game_state_id")
})
public class GameCharacterEntity { //제외
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

    public void applyDefenseDelta(int delta) {
        this.defense = Math.max(0, this.defense + delta);
    }

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

    /* FK 자식 */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_state_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_character_game_state_game_state_id"))
    private GameStateEntity gameState; // 게임 세션 ID (FK)
}
