package com.renovandelve.domain;

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
@Table(name = "monster", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "monsterType"}))
public class MonsterEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long monsterId; // 몬스터 고유 ID

    @Column(length = 50, nullable = false)
    private String name; // 몬스터 명

    @PositiveOrZero
    @Column(nullable = false)
    private short monsterType; // 몬스터 타입 NORMAL 0 / ELITE 1 / BOSS 2

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int scoreReward = 0; // 처치 시 기본 점수

    // TEXT 타입 대체자
    @Column(length = Integer.MAX_VALUE)
    private String description; // 몬스터 설명

    /* FK */
//    @ToString.Exclude
//    @OneToMany(mappedBy = "monster")
//    private List<MonsterCharacteristicEntity> monsterCharacteristic;
}
