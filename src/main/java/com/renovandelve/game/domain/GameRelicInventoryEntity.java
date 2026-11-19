package com.renovandelve.game.domain;

import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    /**
     * 게임이 가질 유물 한 칸을 만든다. 게임에 붙이는 일은 {@link GameStateEntity#addRelic(RelicEntity)}가 맡는다.
     *
     * @param gameState 유물을 가질 게임
     * @param relic     유물
     * @return 저장되지 않은 새 인벤토리 칸
     */
    public static GameRelicInventoryEntity of(GameStateEntity gameState, RelicEntity relic) {
        return GameRelicInventoryEntity.builder().gameState(gameState).relic(relic).build();
    }
}
