package com.renovandelve.game.domain;

import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
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

    /**
     * 게임이 가질 소모품 한 칸을 만든다. 게임에 붙이는 일은 {@link GameStateEntity#addConsumable(ConsumableEntity)}가 맡는다.
     *
     * @param gameState  소모품을 가질 게임
     * @param consumable 소모품
     * @return 저장되지 않은 새 인벤토리 칸
     */
    public static GameConsumableInventoryEntity of(GameStateEntity gameState, ConsumableEntity consumable) {
        return GameConsumableInventoryEntity.builder().gameState(gameState).consumable(consumable).build();
    }
}
