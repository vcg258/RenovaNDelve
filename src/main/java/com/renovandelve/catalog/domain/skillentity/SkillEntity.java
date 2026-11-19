package com.renovandelve.catalog.domain.skillentity;

import com.renovandelve.collection.domain.CollectionSkillEntity;
import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.catalog.domain.helmetentity.Type;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Entity
@Builder
@ToString(exclude = {"gameSkillSlot", "collectionSkill"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "skill")
public class SkillEntity {

    @PositiveOrZero // Unsigned 대체자
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long skillId; // 기술 고유 ID (PK)

    @Column(length = 50, nullable = false)
    private String name; // 기술명 (출혈, 화상 등)

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private Target target; // 자신, 적 1체, 적 전체

    @PositiveOrZero
    @Column(nullable = false)
    private int attackCount; // 공격 횟수

    @Builder.Default
    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal coefficient = BigDecimal.valueOf(1.00); // 기본 계수 (공격력 배율)

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int cost = 0; // 기술 사용 비용 (AP 등)

    /**
     * 기본 공격인지 (V7). 예전에는 "비용 0"으로 판정했는데, 비용 0짜리 스킬이 생기면 그것도 기본 공격으로 취급되어 컬럼으로 바꿨다.
     * 기본 공격은 스킬이 적을 때 버릴 수 없고, 도감 집계와 망령의 기술 목록에서 빠진다.
     */
    @Builder.Default
    @Column(name = "is_basic", nullable = false)
    private boolean basic = false;

    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private int cooldown = 0; // 쿨타임 (턴 수)

    @PositiveOrZero
    private short statusEffect; // 부여 상태이상 (출혈 / 화상 / 독 / 기절)

    @PositiveOrZero
    private int effectStack; // 상태이상 스택 수

    // TEXT 타입 대체자
    @Column(length = Integer.MAX_VALUE)
    private String description; // 기술 설명

    /* FK */
    @OneToMany(mappedBy = "skill")
    private List<GameSkillSlotEntity> gameSkillSlot;

    @OneToMany(mappedBy = "skill")
    private List<CollectionSkillEntity> collectionSkill;
}
