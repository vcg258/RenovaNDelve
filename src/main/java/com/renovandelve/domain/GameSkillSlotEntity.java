package com.renovandelve.domain;

import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "game_skill_slot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"game_state_id", "slot_index"}), // TODO 동일슬롯이 동일 슬롯을 못덮게 함 스킬이나 그런 것이 바뀐다면 INSERT X UPDATE O 의논
        indexes = {
        @Index(name = "idx_game_skill_slot_game_state_game_state_id", columnList = "game_state_id"), // TODO 복합 유니크는 어차피 인덱스 생성해줌 위 의논사항이 확정난다면 제거
        @Index(name = "idx_game_skill_slot_skill_skill_id", columnList = "skill_id")
})
public class GameSkillSlotEntity { //제외
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long slotId; // 슬릇 고유 ID (PK)

    @PositiveOrZero
    @Column(nullable = false)
    private int slotIndex; // 슬릇 순번 (0 ~ 7)

    @PositiveOrZero
    @Column(nullable = false)
    private int coolTime; // 잔여 쿨타임 0이상 값

    /* FK 자식 */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_state_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_skill_slot_game_state_game_state_id"))
    private GameStateEntity gameState; // 게임 세션 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_skill_slot_skill_skill_id"))
    private SkillEntity skill; // 기술 ID (FK)

    /**
     * 새 스킬 슬롯을 만든다. 쿨타임은 0(바로 사용 가능)으로 시작한다.
     * 게임에 붙이는 일은 {@link GameStateEntity#learnSkill(SkillEntity)}가 맡는다.
     *
     * @param gameState 슬롯을 가질 게임
     * @param skill     넣을 스킬
     * @param slotIndex 슬롯 순번 (0부터)
     * @return 저장되지 않은 새 슬롯
     */
    public static GameSkillSlotEntity of(GameStateEntity gameState, SkillEntity skill, int slotIndex) {
        return GameSkillSlotEntity.builder()
                .gameState(gameState).skill(skill).slotIndex(slotIndex).coolTime(0)
                .build();
    }

    /**
     * 이번 턴에 쓸 수 있는지 확인한다.
     *
     * @return 남은 쿨타임이 없으면 true
     */
    public boolean isReady() {
        return this.coolTime <= 0;
    }

    /**
     * 슬롯의 스킬을 다른 스킬로 바꾼다. 새 스킬은 바로 쓸 수 있도록 쿨타임을 비운다.
     *
     * @param newSkill 새로 넣을 스킬
     */
    public void changeSkill(SkillEntity newSkill) {
        this.skill = newSkill;
        this.coolTime = 0;
    }

    /**
     * 방금 사용한 스킬의 쿨타임을 건다.
     * 쿨타임이 있는 스킬은 감소 효과를 받아도 최소 1턴은 기다린다.
     *
     * @param cooldownReduction 유물 등으로 줄어드는 턴 수
     */
    public void startCooldown(int cooldownReduction) {
        int cooldown = this.skill.getCooldown();
        this.coolTime = cooldown > 0 ? Math.max(1, cooldown - cooldownReduction) : 0;
    }

    /**
     * 한 턴이 지나 쿨타임을 1 줄인다. 0 아래로는 내려가지 않는다.
     */
    public void tickCooldown() {
        this.coolTime = Math.max(0, this.coolTime - 1);
    }

    /**
     * 쿨타임을 모두 비운다 (전투가 끝났을 때).
     */
    public void resetCooldown() {
        this.coolTime = 0;
    }
}
