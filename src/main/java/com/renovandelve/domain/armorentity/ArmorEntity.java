package com.renovandelve.domain.armorentity;

import com.renovandelve.domain.CollectionArmorEntity;
import com.renovandelve.domain.GameEquipmentEntity;
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
@Table(name = "armor")
public class ArmorEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long armorId; // 갑옷 고유 ID

    @Column(length = 50, nullable = false)
    private String name; // 갑옷 명

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Type type; // 가죽 / 천 / 체인 / 판금

    @Builder.Default // 디폴트를 걸어줌으로써 필드에 선언해준 기본값 0은 빌더에서 값을 제외하더라도 0으로 들어옴
    @Column(nullable = false)
    private int defBonus = 0; // 추가 방어력 (단검 -, 둔기 +)

    @PositiveOrZero
    private short specialEffect; // 갑옷 특수 효과

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int scoreCost = 0; // 스타팅 포인트 비용

//    @Column(length = 10, nullable = false)
//    private int price;  // 가격

    // TEXT 타입 대체자
    @Column(length = Integer.MAX_VALUE)
    private String description; // 갑옷 설명

    /* FK (부모) */
    @ToString.Exclude
    @OneToMany(mappedBy = "armor")
    private List<GameEquipmentEntity> gameEquipment;

    @ToString.Exclude
    @OneToMany(mappedBy = "armor")
    private List<CollectionArmorEntity> collectionArmor;
}
