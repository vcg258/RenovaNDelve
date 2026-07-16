package com.renovandelve.domain.buffentity;

import com.renovandelve.domain.consumableentity.Type;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "monster_buff")
public class MonsterBuffEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long keywordId; // 키워드 고유 ID

    @Column(length = 50, nullable = false)
    private String keyword; // 키워드 명

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private Type buffType; // 회복 / 공격력 / 방어력 / 해제 / 부여 / 데미지 / 부활

    // TEXT 타입 대체자
    @Column(nullable = false, length = Integer.MAX_VALUE)
    private String effectDesc; // 키워드 효과 설명
}
