package com.renovandelve.domain;

import com.renovandelve.domain.relicentity.RelicEntity;
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
@Table(name = "collection_relic",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "relic_id"}))
public class CollectionRelicEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long collectionId; // 도감 항목 고유 ID

    @Column(nullable = false, updatable = false)
    private LocalDateTime acquiredAt; // 최초 흭득 일시

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    @PrePersist
    private void onCreate() {
        this.acquiredAt = LocalDateTime.now();
    }

    /* FK (자식) */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collection_relic_member_member_id"))
    private MemberEntity member; // 회원 ID (FK)

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collection_relic_relic_relic_id"))
    private RelicEntity relic; // 유물 고유 ID (FK)
}
