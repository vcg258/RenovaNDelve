package com.renovandelve.domain;

import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Data
@Entity
@Builder
@ToString
@NoArgsConstructor
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
}
