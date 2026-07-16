package com.renovandelve.domain;

import com.renovandelve.domain.gamestateentity.GameStateEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "member")
public class MemberEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long memberId; // 회원 고유 ID

    @Column(length = 50, unique = true)
    private String loginId; // 일반 로그인 아이디

    @Column(length = 60)
    private String passwordHash; // 비밀번호 해시값 (Bcrypt)

    @Column(length = 512)
    private String socialToken; // 소셜 OAuth2 토큰

    @Column(length = 100)
    private String socialId; // 소셜 제공자 내 고유 ID

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 계정 생성일시

    @Column(nullable = false)
    private LocalDateTime updatedAt; // 마지막 수정일시

    public void updateSocialToken(String socialToken) {
        this.socialToken = socialToken;
    }

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 생명주기 도메인 메서드 (UPDATE 직전에 값을 기록하고 넣어주는 역할)
    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /* FK */
    @ToString.Exclude
    @OneToMany(mappedBy = "member")
    private List<GameStateEntity> gameState;

    @ToString.Exclude
    @OneToMany(mappedBy = "member")
    private List<RankingEntity> ranking;

    @ToString.Exclude
    @OneToMany(mappedBy = "member")
    private List<CollectionHelmetEntity> collectionHelmet;

    @ToString.Exclude
    @OneToMany(mappedBy = "member")
    private List<CollectionRelicEntity> collectionRelic;

    @ToString.Exclude
    @OneToMany(mappedBy = "member")
    private List<CollectionConsumableEntity> collectionConsumable;

    @ToString.Exclude
    @OneToMany(mappedBy = "member")
    private List<CollectionArmorEntity> collectionArmor;

    @ToString.Exclude
    @OneToMany(mappedBy = "member")
    private List<CollectionWeaponEntity> collectionWeapon;

    @ToString.Exclude
    @OneToMany(mappedBy = "member")
    private List<CollectionSkillEntity> collectionSkill;
}
