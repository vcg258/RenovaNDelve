package com.renovandelve.member.service;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.domain.memberentity.MemberStatus;
import com.renovandelve.member.domain.memberentity.Role;
import com.renovandelve.member.dto.request.MemberStatusChangeRequest;
import com.renovandelve.member.dto.request.RoleChangeRequest;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.ranking.repository.RankingRepository;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자가 자기 자신이나 마지막 관리자를 무력화하지 못하게 하는 규칙.
 * 관리자가 한 명뿐일 때 스스로 권한을 내려놓으면 아무도 관리 화면에 들어올 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceImplTest {

    @Mock private MemberRepository memberRepository;
    @Mock private GameStateRepository gameStateRepository;
    @Mock private RankingRepository rankingRepository;
    @Mock private GameSessionService gameSessionService;
    @Mock private SecurityTokenService securityTokenService;
    @Mock private TokenRevocationService tokenRevocationService;
    @InjectMocks private AdminServiceImpl adminService;

    private MemberEntity member(long id, Role role) {
        MemberEntity member = mock(MemberEntity.class);
        when(member.getMemberId()).thenReturn(id);
        when(member.getLoginId()).thenReturn("member" + id);
        when(member.getRole()).thenReturn(role);
        when(member.isAdmin()).thenReturn(role == Role.ADMIN);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findById(id)).thenReturn(Optional.of(member));
        return member;
    }

    @Test
    @DisplayName("관리자는 자기 자신의 권한을 회수할 수 없다")
    void cannotRevokeOwnRole() {
        MemberEntity admin = member(1L, Role.ADMIN);

        assertThatThrownBy(() -> adminService.changeRole(1L, new RoleChangeRequest("USER"), admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자기 자신");
        verify(admin, never()).changeRole(any());
        verify(tokenRevocationService, never()).revoke(any());
    }

    @Test
    @DisplayName("관리자는 자기 자신을 정지할 수 없다")
    void cannotSuspendSelf() {
        MemberEntity admin = member(1L, Role.ADMIN);

        assertThatThrownBy(() -> adminService.changeStatus(1L, new MemberStatusChangeRequest("SUSPENDED", "x"), admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자기 자신");
        verify(admin, never()).suspend(any());
    }

    @Test
    @DisplayName("다른 관리자가 있어도 마지막 남은 관리자는 강등할 수 없다")
    void cannotDemoteLastAdmin() {
        MemberEntity actor = member(1L, Role.ADMIN);
        MemberEntity target = member(2L, Role.ADMIN);
        when(memberRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminService.changeRole(2L, new RoleChangeRequest("USER"), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("마지막 관리자");
        verify(target, never()).changeRole(any());
    }

    @Test
    @DisplayName("관리자가 둘 이상이면 다른 관리자의 권한을 회수할 수 있다")
    void canDemoteAnotherAdminWhenNotLast() {
        MemberEntity actor = member(1L, Role.ADMIN);
        MemberEntity target = member(2L, Role.ADMIN);
        when(memberRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        adminService.changeRole(2L, new RoleChangeRequest("USER"), actor);

        verify(target).changeRole(Role.USER);
        verify(tokenRevocationService).revoke("member2");
    }
}
