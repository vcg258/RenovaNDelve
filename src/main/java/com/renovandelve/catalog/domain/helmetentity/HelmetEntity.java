package com.renovandelve.catalog.domain.helmetentity;

import com.renovandelve.collection.domain.CollectionHelmetEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.catalog.domain.Rarity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.util.List;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "helmet")
public class HelmetEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long helmetId; // 투구 고유 ID

    @Column(length = 50, nullable = false)
    private String name; // 투구 명

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Type type; // 가죽모자 / 천 / 판금 / 가면

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int hpBonus = 0; // 추가 체력

    @PositiveOrZero
    private short specialEffect; // 투구 특수 효과 설명

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int scoreCost = 0; // 스타팅 포인트 비용

//    @Column(length = 10, nullable = false)

    // TEXT 타입 대체자
    @Column(length = Integer.MAX_VALUE)
    private String description; // 투구 설명

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Rarity rarity = Rarity.COMMON; // 등급 (COMMON / RARE / EPIC). 상점·전리품의 등장 확률을 정한다 (V8)

    /* FK */
    @ToString.Exclude
    @OneToMany(mappedBy = "helmet")
    private List<GameEquipmentEntity> gameEquipmentEntity;

    @ToString.Exclude
    @OneToMany(mappedBy = "helmet")
    private List<CollectionHelmetEntity> collectionHelmet;
}
