package com.renovandelve.catalog.domain.consumableentity;

import com.renovandelve.collection.domain.CollectionConsumableEntity;
import com.renovandelve.game.domain.GameConsumableInventoryEntity;
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
@Table(name = "consumable")
public class ConsumableEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long consumableId; // 소모품 고유 ID

    @Column(length = 50, nullable = false)
    private String name; // 소모품 명

//    @Enumerated(EnumType.STRING)
//    @Column(length = 15, nullable = false)
//    private Target target; // 자신, 적 1체, 적 전체

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private Type effectType; // 회복 / 공격력 / 방어력 / 해제 / 부여 / 데미지 / 부활

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int effectValue = 0; // 효과 수치

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int scoreCost = 0; // 스타팅 포인트 비용

//    @Column(length = 10, nullable = false)
//    private int price;  // 가격

    // TEXT 타입 대체자
    @Column(length = Integer.MAX_VALUE)
    private String description; // 소모품 설명

    @ToString.Exclude
    @OneToMany(mappedBy = "consumable")
    private List<GameConsumableInventoryEntity> gameConsumableInventory;

    @ToString.Exclude
    @OneToMany(mappedBy = "consumable")
    private List<CollectionConsumableEntity> collectionConsumables;
}
