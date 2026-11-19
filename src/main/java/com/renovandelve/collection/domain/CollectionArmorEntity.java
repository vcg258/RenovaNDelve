package com.renovandelve.collection.domain;

import com.renovandelve.member.domain.MemberEntity;

import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
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
@Table(name = "collection_armor",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "armor_id"}))
public class CollectionArmorEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long collectionId; // 도감 항목 고유 ID

    @Column(nullable = false, updatable = false)
    private LocalDateTime acquiredAt; // 최초 흭득 일시

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    /**
     * 처음 저장될 때 획득 일시를 기록한다 (JPA 생명주기 콜백).
     */
    /**
     * 회원 도감에 갑옷를 처음 등록한다.
     *
     * @param member 회원
     * @param armor 손에 넣은 갑옷
     * @return 새 도감 항목
     */
    public static CollectionArmorEntity of(MemberEntity member, ArmorEntity armor) {
        return CollectionArmorEntity.builder().member(member).armor(armor).build();
    }

    @PrePersist
    private void onCreate() {
        this.acquiredAt = LocalDateTime.now();
    }

    /* FK (자식) */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collection_armor_member_memberId"))
    private MemberEntity member; // 회원 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "armor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collection_armor_armor_armor_id"))
    private ArmorEntity armor; // 갑옷 고유 ID (FK)
}
