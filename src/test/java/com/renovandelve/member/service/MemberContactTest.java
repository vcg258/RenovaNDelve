package com.renovandelve.member.service;

import com.renovandelve.collection.mapper.CollectionMapper;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.dto.request.ContactChangeRequest;
import com.renovandelve.member.dto.request.SignupRequest;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.ranking.repository.RankingRepository;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회원의 이름·이메일: 가입 시 수집, 중복 불가, 내 정보에서 등록·변경, 탈퇴 시 삭제.
 */
class MemberContactTest {

    private MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private MemberServiceImpl memberService;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        memberService = new MemberServiceImpl(memberRepository, mock(RankingRepository.class),
                mock(GameStateRepository.class), mock(GameSessionService.class), mock(SecurityTokenService.class),
                mock(TokenRevocationService.class), passwordEncoder, mock(PersonalDataService.class),
                mock(CollectionMapper.class));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(memberRepository.saveAndFlush(any(MemberEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private SignupRequest signup(String name, String email) {
        return new SignupRequest("hero01", "password123", "용사", name, email, true, true, true);
    }

    /* ==================== 가입 ==================== */

    @Test
    @DisplayName("가입: 확인과 저장 사이에 같은 값으로 먼저 가입돼 DB 제약에 걸리면 400용 예외로 바꾼다")
    void signupRaceBecomesBadRequest() {
        when(memberRepository.saveAndFlush(any(MemberEntity.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_member_email"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> memberService.signup(signup("김용사", "hero@example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중");
    }

    @Test
    @DisplayName("가입: 이름과 이메일을 저장하고, 이메일은 소문자로 정규화한다")
    void signupStoresNormalizedEmail() {
        memberService.signup(signup(" 김용사 ", "  Hero@Example.COM "));

        ArgumentCaptor<MemberEntity> saved = ArgumentCaptor.forClass(MemberEntity.class);
        verify(memberRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("김용사");
        assertThat(saved.getValue().getEmail()).isEqualTo("hero@example.com");
        assertThat(saved.getValue().getPolicyVersion()).isEqualTo(PersonalDataService.POLICY_VERSION);
    }

    @Test
    @DisplayName("가입: 이미 쓰이는 이메일이면 거부한다 (대소문자만 달라도)")
    void signupRejectsDuplicateEmail() {
        when(memberRepository.existsByEmail("hero@example.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signup(signup("김용사", "HERO@example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일");
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 요청 검증: 이메일 형식과 이름이 올바르지 않으면 걸린다")
    void signupRequestValidation() {
        assertThat(validator.validate(signup("김용사", "hero@example.com"))).isEmpty();
        assertThat(validator.validate(signup("김용사", "not-an-email"))).isNotEmpty();
        assertThat(validator.validate(signup("김용사", ""))).isNotEmpty();
        assertThat(validator.validate(signup("", "hero@example.com"))).isNotEmpty();
        assertThat(validator.validate(signup("<script>", "hero@example.com"))).isNotEmpty();
        assertThat(validator.validate(signup("Kim Yongsa", "hero@example.com"))).isEmpty();
    }

    /* ==================== 내 정보에서 등록 / 변경 ==================== */

    private MemberEntity existingMember(String email) {
        MemberEntity member = MemberEntity.builder().memberId(7L).loginId("hero01")
                .passwordHash(passwordEncoder.encode("password123")).email(email).build();
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        return member;
    }

    @Test
    @DisplayName("이름·이메일이 없던 기존 계정이 현재 비밀번호를 확인하고 등록한다")
    void legacyMemberRegistersContact() {
        MemberEntity member = existingMember(null);

        memberService.changeContact(member, new ContactChangeRequest("김용사", "Hero@Example.com", "password123"));

        assertThat(member.getName()).isEqualTo("김용사");
        assertThat(member.getEmail()).isEqualTo("hero@example.com");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리거나 없으면 이메일을 바꿀 수 없다 (복구 수단 가로채기 방지)")
    void contactChangeRequiresPassword() {
        MemberEntity member = existingMember("old@example.com");

        assertThatThrownBy(() -> memberService.changeContact(member,
                new ContactChangeRequest("김용사", "attacker@example.com", "wrong-password")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> memberService.changeContact(member,
                new ContactChangeRequest("김용사", "attacker@example.com", null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(member.getEmail()).isEqualTo("old@example.com");
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 회원이 쓰는 이메일로는 바꿀 수 없지만, 자기 이메일 그대로 이름만 바꾸는 것은 된다")
    void contactChangeChecksDuplicateEmail() {
        MemberEntity member = existingMember("hero@example.com");
        when(memberRepository.existsByEmail("taken@example.com")).thenReturn(true);
        when(memberRepository.existsByEmail("hero@example.com")).thenReturn(true); // 자기 자신

        assertThatThrownBy(() -> memberService.changeContact(member,
                new ContactChangeRequest("김용사", "taken@example.com", "password123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일");

        memberService.changeContact(member, new ContactChangeRequest("김전사", "hero@example.com", "password123"));
        assertThat(member.getName()).isEqualTo("김전사");
    }

    /* ==================== 탈퇴 ==================== */

    @Test
    @DisplayName("탈퇴하면 이름과 이메일이 지워진다 (같은 이메일로 재가입 가능)")
    void withdrawErasesNameAndEmail() {
        MemberEntity member = MemberEntity.builder().memberId(7L).loginId("hero01")
                .name("김용사").email("hero@example.com").nickname("용사").build();

        member.withdraw();

        assertThat(member.getName()).isNull();
        assertThat(member.getEmail()).isNull();
        assertThat(member.getLoginId()).isEqualTo("withdrawn_7");
    }
}
