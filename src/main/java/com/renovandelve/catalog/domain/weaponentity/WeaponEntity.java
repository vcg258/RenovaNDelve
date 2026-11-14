package com.renovandelve.catalog.domain.weaponentity;

import com.renovandelve.collection.domain.CollectionWeaponEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.util.List;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "weapon")
public class WeaponEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long weaponId; // 무기 고유 ID (PK)

    @Column(length = 50, nullable = false)
    private String name; // 무기명

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Type type; // 검 / 단검 / 둔기 / 스태프

    @Builder.Default
    @Column(nullable = false)
    private int atkBonus = 0; // 추가 공격력 (단검 : 데미지 - , 둔기 : 데미지 +)

    @PositiveOrZero
    private short specialEffect; // 무기 특수 효과

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int scoreCost = 0; // 스타딩 포인트 비용

    // TEXT 타입 대체자
    @Column(length = Integer.MAX_VALUE)
    private String description; // 무기 설명

    /* FK */
    @ToString.Exclude
    @OneToMany(mappedBy = "weapon")
    private List<GameEquipmentEntity> gameEquipment;

    @ToString.Exclude
    @OneToMany(mappedBy = "weapon")
    private List<CollectionWeaponEntity> collectionWeapon;
}
