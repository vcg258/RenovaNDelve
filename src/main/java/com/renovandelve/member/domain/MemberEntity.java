package com.renovandelve.member.domain;

import com.renovandelve.collection.domain.CollectionArmorEntity;
import com.renovandelve.collection.domain.CollectionConsumableEntity;
import com.renovandelve.collection.domain.CollectionHelmetEntity;
import com.renovandelve.collection.domain.CollectionRelicEntity;
import com.renovandelve.collection.domain.CollectionSkillEntity;
import com.renovandelve.collection.domain.CollectionWeaponEntity;
import com.renovandelve.ranking.domain.RankingEntity;

import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.member.domain.memberentity.MemberStatus;
import com.renovandelve.member.domain.memberentity.Role;
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
@Table(name = "member", indexes = {
        @Index(name = "idx_member_status", columnList = "status"),
        @Index(name = "idx_member_role", columnList = "role")
})
public class MemberEntity {
    @PositiveOrZero
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id private Long memberId; // 회원 고유 ID

    @Column(length = 50, unique = true)
    private String loginId; // 일반 로그인 아이디

    @Column(length = 60)
    private String passwordHash; // 비밀번호 해시값 (Bcrypt)

    @Column(length = 20)
    private String nickname; // 게임 내 표시 이름 (랭킹 노출용)

    @Column(length = 50)
    private String name; // 이름 (아이디 찾기 본인 확인용). 이 컬럼이 생기기 전에 가입한 계정은 NULL

    @Column(length = 254)
    private String email; // 이메일 (소문자로 정규화해 저장, 값이 있으면 중복 불가). 아이디 안내·비밀번호 재설정 링크가 가는 곳

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, columnDefinition = "varchar(20) not null default 'USER'")
    private Role role = Role.USER; // 권한

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, columnDefinition = "varchar(20) not null default 'ACTIVE'")
    private MemberStatus status = MemberStatus.ACTIVE; // 계정 상태

    @Column(length = 200)
    private String statusReason; // 정지/탈퇴 사유

    private LocalDateTime lastLoginAt; // 마지막 로그인 일시

    @Column(length = 512)
    private String socialToken; // 소셜 OAuth2 토큰

    @Column(length = 100)
    private String socialId; // 소셜 제공자 내 고유 ID

    private LocalDateTime termsAgreedAt; // 이용약관 동의 일시

    private LocalDateTime privacyAgreedAt; // 개인정보 수집·이용 동의 일시

    @Column(length = 20)
    private String policyVersion; // 동의한 약관·처리방침 버전 (개정 시 재동의 판단 근거)

    private LocalDateTime withdrawnAt; // 탈퇴 일시

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 계정 생성일시

    @Column(nullable = false)
    private LocalDateTime updatedAt; // 마지막 수정일시

    /* ===== 도메인 메서드 ===== */

    /**
     * role / status는 기존 회원(컬럼 추가 이전에 가입한 계정)에서 NULL일 수 있다.
     * Lombok @Getter 대신 명시적 getter로 기본값을 보장해 모든 호출부의 NPE를 원천 차단한다.
     */
    public Role getRole() {
        return role == null ? Role.USER : role;
    }

    /**
     * 계정 상태. 컬럼이 나중에 추가돼 NULL인 기존 계정은 정상(ACTIVE)으로 본다.
     *
     * @return 계정 상태
     */
    public MemberStatus getStatus() {
        return status == null ? MemberStatus.ACTIVE : status;
    }

    /**
     * 소셜 로그인 토큰을 바꾼다.
     *
     * @param socialToken 새 토큰
     */
    public void updateSocialToken(String socialToken) {
        this.socialToken = socialToken;
    }

    /**
     * 비밀번호를 바꾼다. 해시는 호출하는 쪽에서 만든다.
     *
     * @param encodedPassword 해시된 비밀번호
     */
    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
    }

    /**
     * 닉네임을 바꾼다. 중복·금칙어 검사는 서비스가 먼저 한다.
     *
     * @param nickname 새 닉네임
     */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 이름·이메일 등록 / 변경. 이메일은 호출하는 쪽에서 정규화해 넘긴다 */
    public void changeContact(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /**
     * 권한을 바꾼다. 자기 자신·마지막 관리자 보호는 서비스가 먼저 확인한다.
     *
     * @param role 새 권한
     */
    public void changeRole(Role role) {
        this.role = role;
    }

    /** 운영자에 의한 이용 정지 */
    public void suspend(String reason) {
        this.status = MemberStatus.SUSPENDED;
        this.statusReason = reason;
    }

    /** 정지 해제 */
    public void activate() {
        this.status = MemberStatus.ACTIVE;
        this.statusReason = null;
    }

    /**
     * 탈퇴 (소프트 삭제).
     * 랭킹·도감 기록은 남기되 로그인 아이디를 익명화하고 이름·이메일을 지워 재가입을 허용한다.
     */
    public void withdraw() {
        this.status = MemberStatus.WITHDRAWN;
        this.statusReason = "본인 탈퇴";
        this.loginId = "withdrawn_" + this.memberId;
        this.passwordHash = null;
        this.socialId = null;
        this.socialToken = null;
        this.nickname = "탈퇴한 모험가";
        // 이름과 이메일은 남길 이유가 없다. 비워야 같은 이메일로 다시 가입할 수 있다
        this.name = null;
        this.email = null;
        this.lastLoginAt = null;
        this.withdrawnAt = LocalDateTime.now();
    }

    /**
     * 마지막 로그인 시각을 지금으로 기록한다.
     */
    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /**
     * 관리자인지 확인한다.
     *
     * @return ADMIN이면 true
     */
    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    /** 랭킹 등 외부 노출용 이름 (닉네임 우선, 없으면 로그인 아이디) */
    public String displayName() {
        return nickname != null && !nickname.isBlank() ? nickname : loginId;
    }

    // 생명주기 도메인 메서드 (INSERT 직전에 값을 기록하고 넣어주는 역할)
    /**
     * 처음 저장될 때 생성 일시와 기본 권한·상태를 채운다 (JPA 생명주기 콜백).
     */
    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.role == null) this.role = Role.USER;
        if (this.status == null) this.status = MemberStatus.ACTIVE;
    }

    // 생명주기 도메인 메서드 (UPDATE 직전에 값을 기록하고 넣어주는 역할)
    /**
     * 수정될 때 수정 일시를 갱신한다 (JPA 생명주기 콜백).
     */
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
