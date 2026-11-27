package com.renovandelve.collection.domain;

import com.renovandelve.member.domain.MemberEntity;

import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;


@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "collection_weapon",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "weapon_id"}))
public class CollectionWeaponEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long collectionId; // 도감 항목 고유 ID

    @Column(nullable = false, updatable = false)
    private LocalDateTime acquiredAt; // 최초 흭득 일시

    /* FK (자식) */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collection_weapon_member_memberId"))
    private MemberEntity member; // 회원 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weapon_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collection_weapon_weapon_weapon_id"))
    private WeaponEntity weapon; // 무기 고유 ID (FK)

    /**
     * 회원 도감에 무기를 처음 등록한다.
     *
     * @param member 회원
     * @param weapon 손에 넣은 무기
     * @return 새 도감 항목
     */
    public static CollectionWeaponEntity of(MemberEntity member, WeaponEntity weapon) {
        return CollectionWeaponEntity.builder().member(member).weapon(weapon).build();
    }

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    /**
     * 처음 저장될 때 획득 일시를 기록한다 (JPA 생명주기 콜백).
     */
    @PrePersist
    private void onCreate() {
        this.acquiredAt = LocalDateTime.now();
    }
}
