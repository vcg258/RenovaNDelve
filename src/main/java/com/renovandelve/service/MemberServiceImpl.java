package com.renovandelve.service;

import com.renovandelve.domain.*;
import com.renovandelve.domain.armorentity.ArmorEntity;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import com.renovandelve.dto.*;
import com.renovandelve.dto.request.NicknameChangeRequest;
import com.renovandelve.dto.request.PasswordChangeRequest;
import com.renovandelve.dto.request.SignupRequest;
import com.renovandelve.dto.response.IllustratedEncyclopediaDTO;
import com.renovandelve.dto.response.MemberProfileDTO;
import com.renovandelve.repository.GameStateRepository;
import com.renovandelve.repository.MemberRepository;
import com.renovandelve.repository.RankingRepository;
import com.renovandelve.service.redis.GameSessionService;
import com.renovandelve.service.redis.SecurityTokenService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Log4j2
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final RankingRepository rankingRepository;
    private final GameStateRepository gameStateRepository;
    private final GameSessionService gameSessionService;
    private final SecurityTokenService securityTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PersonalDataService personalDataService;

    private static final List<String> FORBIDDEN_NICKNAMES = List.of("관리자", "운영자", "admin", "administrator", "gm");

    @Override
    @Transactional
    public void signup(SignupRequest request) {
        memberRepository.findByLoginId(request.loginId()).ifPresent(member -> {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        });
        validateNickname(request.nickname());

        MemberEntity member = memberRepository.save(MemberEntity.builder()
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .termsAgreedAt(LocalDateTime.now())
                .privacyAgreedAt(LocalDateTime.now())
                .policyVersion(PersonalDataService.POLICY_VERSION)
                .build());
        log.info("회원가입 완료 memberId={} loginId={}", member.getMemberId(), member.getLoginId());
    }

    @Override
    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null || loginId.isBlank()) return false;
        return memberRepository.findByLoginId(loginId).isEmpty();
    }

    @Override
    public boolean isNicknameAvailable(String nickname) {
        if (nickname == null || nickname.isBlank()) return false;
        if (isForbidden(nickname)) return false;
        return !memberRepository.existsByNickname(nickname);
    }

    @Override
    @Transactional
    public MemberProfileDTO getProfile(MemberEntity member) {
        MemberEntity managed = managed(member);

        int collectionCount = managed.getCollectionWeapon().size() + managed.getCollectionArmor().size()
                + managed.getCollectionHelmet().size() + managed.getCollectionSkill().size()
                + managed.getCollectionRelic().size() + managed.getCollectionConsumable().size();

        int bestScore = managed.getRanking().stream()
                .mapToInt(RankingEntity::getBestScore)
                .max().orElse(0);

        return MemberProfileDTO.of(managed, collectionCount, bestScore);
    }

    @Override
    @Transactional
    public void changePassword(MemberEntity member, PasswordChangeRequest request) {
        MemberEntity managed = managed(member);

        if (managed.getPasswordHash() == null) {
            throw new IllegalStateException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), managed.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(request.newPassword(), managed.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호와 다른 비밀번호를 사용해주세요.");
        }

        managed.changePassword(passwordEncoder.encode(request.newPassword()));
        memberRepository.save(managed);

        // 비밀번호 변경 시 기존 세션 무효화 (다른 기기 로그아웃)
        securityTokenService.logout(managed.getLoginId());
        log.info("비밀번호 변경 memberId={}", managed.getMemberId());
    }

    @Override
    @Transactional
    public void changeNickname(MemberEntity member, NicknameChangeRequest request) {
        MemberEntity managed = managed(member);
        if (request.nickname().equals(managed.getNickname())) {
            throw new IllegalArgumentException("현재 닉네임과 동일합니다.");
        }
        validateNickname(request.nickname());

        managed.changeNickname(request.nickname());
        memberRepository.save(managed);
        log.info("닉네임 변경 memberId={} -> {}", managed.getMemberId(), request.nickname());
    }

    @Override
    @Transactional
    public void withdraw(MemberEntity member) {
        MemberEntity managed = managed(member);

        // 진행 중인 게임 정리
        gameStateRepository.findByMember_MemberIdAndIsActiveTrue(managed.getMemberId())
                .ifPresent(game -> {
                    game.deactivate();
                    gameStateRepository.save(game);
                    gameSessionService.delete(game.getGameStateId());
                });

        String loginId = managed.getLoginId();

        // AI 대화 기록(가이드 챗봇 입력 내용 포함)은 탈퇴 즉시 파기. 익명화 전의 loginId로 찾아야 한다.
        personalDataService.eraseAllConversations(managed.getMemberId(), loginId);

        managed.withdraw();
        memberRepository.save(managed);
        securityTokenService.logout(loginId);

        log.info("회원 탈퇴 처리 memberId={}", managed.getMemberId());
    }

    @Override
    @Transactional
    public IllustratedEncyclopediaDTO getEncyclopedia(MemberEntity member) {
        MemberEntity managed = managed(member);
        MemberDTO memberDTO = MemberDTO.builder()
                .memberId(managed.getMemberId()).loginId(managed.displayName()).build();

        List<CollectionArmorDTO> armors = managed.getCollectionArmor().stream()
                .filter(c -> c.getArmor() != null)
                .map(c -> new CollectionArmorDTO(c.getCollectionId(), c.getAcquiredAt(), memberDTO, toArmorDTO(c.getArmor())))
                .toList();
        List<CollectionConsumableDTO> consumables = managed.getCollectionConsumable().stream()
                .filter(c -> c.getConsumable() != null)
                .map(c -> new CollectionConsumableDTO(c.getCollectionId(), c.getAcquiredAt(), memberDTO, toConsumableDTO(c.getConsumable())))
                .toList();
        List<CollectionHelmetDTO> helmets = managed.getCollectionHelmet().stream()
                .filter(c -> c.getHelmet() != null)
                .map(c -> new CollectionHelmetDTO(c.getCollectionId(), c.getAcquiredAt(), memberDTO, toHelmetDTO(c.getHelmet())))
                .toList();
        List<CollectionRelicDTO> relics = managed.getCollectionRelic().stream()
                .filter(c -> c.getRelic() != null)
                .map(c -> new CollectionRelicDTO(c.getCollectionId(), c.getAcquiredAt(), memberDTO, toRelicDTO(c.getRelic())))
                .toList();
        List<CollectionSkillDTO> skills = managed.getCollectionSkill().stream()
                .filter(c -> c.getSkill() != null)
                .map(c -> new CollectionSkillDTO(c.getCollectionId(), c.getAcquiredAt(), memberDTO, toSkillDTO(c.getSkill())))
                .toList();
        List<CollectionWeaponDTO> weapons = managed.getCollectionWeapon().stream()
                .filter(c -> c.getWeapon() != null)
                .map(c -> new CollectionWeaponDTO(c.getCollectionId(), c.getAcquiredAt(), memberDTO, toWeaponDTO(c.getWeapon())))
                .toList();

        return new IllustratedEncyclopediaDTO(armors, consumables, helmets, relics, skills, weapons);
    }

    /* HELPER METHODS */

    /** Lazy 컬렉션 접근을 위해 영속 상태로 재조회 */
    private MemberEntity managed(MemberEntity member) {
        return memberRepository.findById(member.getMemberId())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
    }

    private void validateNickname(String nickname) {
        if (isForbidden(nickname)) {
            throw new IllegalArgumentException("사용할 수 없는 닉네임입니다.");
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
    }

    private boolean isForbidden(String nickname) {
        String normalized = nickname.replaceAll("\\s", "").toLowerCase();
        return FORBIDDEN_NICKNAMES.stream().anyMatch(normalized::contains);
    }

    private SkillDTO toSkillDTO(SkillEntity s) {
        return new SkillDTO(s.getName(), s.getTarget(), s.getAttackCount(), s.getCoefficient(), s.getCost(),
                s.getCooldown(), s.getStatusEffect(), s.getEffectStack(), s.getDescription());
    }

    private RelicDTO toRelicDTO(RelicEntity r) {
        return new RelicDTO(r.getRelicId(), r.getName(), r.getEffectDesc(), r.getRarity(), r.getScoreCost());
    }

    private ConsumableDTO toConsumableDTO(ConsumableEntity c) {
        return new ConsumableDTO(c.getConsumableId(), c.getName(), c.getEffectType(), c.getEffectValue(), c.getScoreCost(), c.getDescription());
    }

    private WeaponDTO toWeaponDTO(WeaponEntity w) {
        return new WeaponDTO(w.getWeaponId(), w.getName(), w.getType(), w.getAtkBonus(), w.getSpecialEffect(), w.getScoreCost(), w.getDescription());
    }

    private ArmorDTO toArmorDTO(ArmorEntity a) {
        return new ArmorDTO(a.getArmorId(), a.getName(), a.getType(), a.getDefBonus(), a.getSpecialEffect(), a.getScoreCost(), a.getDescription());
    }

    private HelmetDTO toHelmetDTO(HelmetEntity h) {
        return new HelmetDTO(h.getHelmetId(), h.getName(), h.getType(), h.getHpBonus(), h.getSpecialEffect(), h.getScoreCost(), h.getDescription());
    }
}
