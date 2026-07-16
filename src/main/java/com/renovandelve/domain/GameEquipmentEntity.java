package com.renovandelve.domain;

import com.renovandelve.domain.armorentity.ArmorEntity;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "game_equipment", indexes = {
        @Index(name = "idx_game_equipment_game_state_game_state_id", columnList = "game_state_id"),
        @Index(name = "idx_game_equipment_weapon_weapon_id", columnList = "weapon_id"),
        @Index(name = "idx_game_equipment_armor_armor_id", columnList = "armor_id"),
        @Index(name = "idx_game_equipment_helmet_helmet_id", columnList = "helmet_id")
})
public class GameEquipmentEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long equipId; // 슬릇 고유 ID

    /**
     * 무기 교체 도메인 메서드
     */
    public void equipWeapon(WeaponEntity weapon) {
        this.weapon = weapon;
    }

    /**
     * 갑옷 교체 도메인 메서드
     */
    public void equipArmor(ArmorEntity armor) {
        this.armor = armor;
    }

    /**
     * 투구 교체 도메인 메서드
     */
    public void equipHelmet(HelmetEntity helmet) {
        this.helmet = helmet;
    }

    /* FK 자식 */
    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_state_id", nullable = false, foreignKey = @ForeignKey(name = "fk_game_equipment_game_state_game_state_id"))
    private GameStateEntity gameState; // 게임 세션 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weapon_id", foreignKey = @ForeignKey(name = "fk_game_equipment_weapon_weapon_id"))
    private WeaponEntity weapon; // 장착 중인 무기 (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "armor_id", foreignKey = @ForeignKey(name = "fk_game_equipment_armor_armor_id"))
    private ArmorEntity armor; // 장착 중인 갑옷 (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helmet_id", foreignKey = @ForeignKey(name = "fk_game_equipment_helmet_helmet_id"))
    private HelmetEntity helmet; // 장착 중인 투구 (FK)
}
