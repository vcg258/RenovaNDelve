package com.renovandelve.domain;

import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Data
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "game_relic_inventory", indexes = {
        @Index(name = "idx_game_relic_inventory_game_state_game_state_id", columnList = "game_state_id"),
        @Index(name = "idx_game_relic_inventory_relic_relic_id", columnList = "relic_id")
})
public class GameRelicInventoryEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long slotId; // 슬릇 고유 ID

    /* FK 자식 */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_state_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_relic_inventory_game_state_game_state_id"))
    private GameStateEntity gameState; // 게임 세션 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_relic_inventory_relic_relic_id"))
    private RelicEntity relic; // 유물 ID (FK)
}
