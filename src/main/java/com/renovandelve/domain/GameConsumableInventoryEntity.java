package com.renovandelve.domain;

import com.renovandelve.domain.consumableentity.ConsumableEntity;
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
@Table(name = "game_consumable_inventory", indexes = {
        @Index(name = "idx_game_consumable_inventory_game_state_game_state_id", columnList = "game_state_id"),
        @Index(name = "idx_game_consumable_inventory_consumable_consumable_id", columnList = "consumable_id")
})
public class GameConsumableInventoryEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long slotId; // 슬롯 고유 ID

    /* FK 자식 */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_state_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_consumable_inventory_game_state_game_state_id"))
    private GameStateEntity gameState; // 게임 세션 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumable_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_consumable_inventory_consumable_consumable_id"))
    private ConsumableEntity consumable; // 소모품 ID (FK)
}
