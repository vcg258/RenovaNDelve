package com.renovandelve.game.domain;

import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
     * 원정을 시작할 때 고른 장비로 슬롯을 만든다.
     *
     * @param gameState 게임
     * @param weapon    무기
     * @param armor     갑옷
     * @param helmet    투구
     * @return 장비 슬롯 (저장은 게임과 함께 cascade로 된다)
     */
    public static GameEquipmentEntity of(GameStateEntity gameState, WeaponEntity weapon, ArmorEntity armor, HelmetEntity helmet) {
        return GameEquipmentEntity.builder().gameState(gameState).weapon(weapon).armor(armor).helmet(helmet).build();
    }

    /** @return 착용 중인 무기의 공격 보너스, 맨손이면 0 */
    public int weaponBonus() {
        return weapon == null ? 0 : weapon.getAtkBonus();
    }

    /** @return 착용 중인 갑옷의 방어 보너스, 없으면 0 */
    public int armorBonus() {
        return armor == null ? 0 : armor.getDefBonus();
    }

    /** @return 착용 중인 투구의 HP 보너스, 없으면 0 */
    public int helmetBonus() {
        return helmet == null ? 0 : helmet.getHpBonus();
    }

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
