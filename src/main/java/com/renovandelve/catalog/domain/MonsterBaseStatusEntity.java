package com.renovandelve.catalog.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "monster_base_status")
public class MonsterBaseStatusEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long statusId; // 스텟 고유 ID

    @PositiveOrZero
    @Column(nullable = false)
    private short stage; // 등장 스테이지 정보

    @PositiveOrZero
    @Column(nullable = false)
    private short difficulty; // 난이도 설정

    @PositiveOrZero
    @Column(nullable = false)
    private int baseHp; // 기본 체력

    @PositiveOrZero
    @Column(nullable = false)
    private int baseAtk; // 기본 공격력

    @PositiveOrZero
    @Column(nullable = false)
    private int baseDef; // 기본 방어력
}
