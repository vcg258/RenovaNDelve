package com.renovandelve.domain;

import com.renovandelve.domain.skillentity.SkillEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;


@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "collection_skill",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "skill_id"}))
public class CollectionSkillEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long collectionId; // 도감 항목 고유 ID

    @Column(nullable = false, updatable = false)
    private LocalDateTime acquiredAt; // 최초 흭득 일시

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    /**
     * 처음 저장될 때 획득 일시를 기록한다 (JPA 생명주기 콜백).
     */
    @PrePersist
    private void onCreate() {
        this.acquiredAt = LocalDateTime.now();
    }

    /* FK (자식) */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collection_skill_member_memberId"))
    private MemberEntity member; // 회원 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collection_skill_skill_skill_id"))
    private SkillEntity skill; // 기술 ID (FK)
}
