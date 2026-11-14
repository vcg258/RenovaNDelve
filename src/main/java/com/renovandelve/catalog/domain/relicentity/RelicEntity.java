package com.renovandelve.catalog.domain.relicentity;

import com.renovandelve.collection.domain.CollectionRelicEntity;
import com.renovandelve.game.domain.GameRelicInventoryEntity;
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
@Table(name = "relic")
public class RelicEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long relicId; // 유물 고유 ID

    @Column(length = 50, nullable = false)
    private String name; // 유물 명

//    @Column(length = 10, nullable = false)
//    private int price;  // 가격

    // TEXT 타입 대체자
    //@Lob
    @Column(nullable = false, length = Integer.MAX_VALUE)
    private String effectDesc; // 유물 효과 설명

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Rarity rarity = Rarity.COMMON; // 희귀도 (COMMON / RARE / EPIC)

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int scoreCost = 0; // 스타팅 포인트 비용

    /* FK */
    @ToString.Exclude
    @OneToMany(mappedBy = "relic")
    private List<GameRelicInventoryEntity> gameRelicInventory;

    @ToString.Exclude
    @OneToMany(mappedBy = "relic")
    private List<CollectionRelicEntity> collectionRelic;
}
