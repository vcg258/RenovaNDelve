package com.renovandelve.member.service;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.ranking.domain.RankingEntity;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.collection.dto.CollectionArmorDTO;
import com.renovandelve.collection.dto.CollectionConsumableDTO;
import com.renovandelve.collection.dto.CollectionHelmetDTO;
import com.renovandelve.collection.dto.CollectionRelicDTO;
import com.renovandelve.collection.dto.CollectionSkillDTO;
import com.renovandelve.collection.dto.CollectionWeaponDTO;
import com.renovandelve.catalog.dto.ConsumableDTO;
import com.renovandelve.catalog.dto.HelmetDTO;
import com.renovandelve.member.dto.MemberDTO;
import com.renovandelve.catalog.dto.RelicDTO;
import com.renovandelve.catalog.dto.SkillDTO;
import com.renovandelve.catalog.dto.WeaponDTO;
import com.renovandelve.member.dto.request.ContactChangeRequest;
import com.renovandelve.member.dto.request.NicknameChangeRequest;
import com.renovandelve.member.dto.request.PasswordChangeRequest;
import com.renovandelve.member.dto.request.SignupRequest;
import com.renovandelve.collection.dto.response.IllustratedEncyclopediaDTO;
import com.renovandelve.member.dto.response.MemberProfileDTO;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.ranking.repository.RankingRepository;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.member.util.EmailNormalizer;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final RankingRepository rankingRepository;
    private final GameStateRepository gameStateRepository;
    private final GameSessionService gameSessionService;
    private final SecurityTokenService securityTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final PasswordEncoder passwordEncoder;
    private final PersonalDataService personalDataService;

    private static final List<String> FORBIDDEN_NICKNAMES = List.of("관리자", "운영자", "admin", "administrator", "gm");

    /**
     * 회원가입. 아이디·닉네임·이메일 중복과 금칙어를 검사하고, 동의 일시와 약관 버전을 함께 저장한다.
     *
     * @param request 가입 정보
     * @throws IllegalArgumentException 중복이거나 쓸 수 없는 값이면
     */
    @Override
    @Transactional
    public void signup(SignupRequest request) {
        memberRepository.findByLoginId(request.loginId()).ifPresent(member -> {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        });
        validateNickname(request.nickname());
        String email = EmailNormalizer.normalize(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        MemberEntity member = memberRepository.save(MemberEntity.builder()
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .name(request.name().trim())
                .email(email)
                .termsAgreedAt(LocalDateTime.now())
                .privacyAgreedAt(LocalDateTime.now())
                .policyVersion(PersonalDataService.POLICY_VERSION)
                .build());
        log.info("회원가입 완료 memberId={} loginId={}", member.getMemberId(), member.getLoginId());
    }

    /**
     * 아이디를 쓸 수 있는지 확인한다.
     *
     * @param loginId 아이디
     * @return 비어 있지 않고 아직 없으면 true
     */
    @Override
    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null || loginId.isBlank()) return false;
        return memberRepository.findByLoginId(loginId).isEmpty();
    }

    /**
     * 닉네임을 쓸 수 있는지 확인한다 (금칙어 포함).
     *
     * @param nickname 닉네임
     * @return 쓸 수 있으면 true
     */
    @Override
    public boolean isNicknameAvailable(String nickname) {
        if (nickname == null || nickname.isBlank()) return false;
        if (isForbidden(nickname)) return false;
        return !memberRepository.existsByNickname(nickname);
    }

    /**
     * 내 정보: 도감 수집 수와 최고 점수를 함께 계산한다.
     *
     * @param member 회원
     * @return 프로필
     */
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

    /**
     * 비밀번호를 바꾼다. 현재 비밀번호를 확인하고, 같은 비밀번호는 거부하며, 다른 기기의 로그인을 끊는다.
     *
     * @param member  회원
     * @param request 현재 비밀번호, 새 비밀번호
     * @throws IllegalArgumentException 현재 비밀번호가 틀리거나 새 비밀번호가 같으면
     * @throws IllegalStateException    비밀번호가 없는 소셜 계정이면
     */
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

    /**
     * 닉네임을 바꾼다.
     *
     * @param member  회원
     * @param request 새 닉네임
     * @throws IllegalArgumentException 같거나 중복이거나 금칙어면
     */
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

    /**
     * 이름과 이메일을 등록하거나 바꾼다. 이메일은 계정 복구 수단이라 비밀번호가 있는 계정은 현재 비밀번호를 확인한다.
     *
     * @param member  회원
     * @param request 이름, 이메일, 현재 비밀번호
     * @throws IllegalArgumentException 비밀번호가 틀리거나 이메일이 중복이면
     */
    @Override
    @Transactional
    public void changeContact(MemberEntity member, ContactChangeRequest request) {
        MemberEntity managed = managed(member);

        // 이메일은 계정 복구 수단이다. 비밀번호가 있는 계정은 본인임을 다시 확인한다
        if (managed.getPasswordHash() != null
                && (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), managed.getPasswordHash()))) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        String email = EmailNormalizer.normalize(request.email());
        if (!email.equals(managed.getEmail()) && memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        managed.changeContact(request.name().trim(), email);
        memberRepository.save(managed);
        log.info("이름·이메일 변경 memberId={}", managed.getMemberId());
    }

    /**
     * 회원 탈퇴 (소프트 삭제). 진행 중인 게임을 끝내고, AI 대화 기록을 지우고, 개인정보를 익명화한 뒤 토큰을 폐기한다.
     *
     * @param member 회원
     */
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
        tokenRevocationService.revoke(loginId); // 발급된 AccessToken도 즉시 무효

        log.info("회원 탈퇴 처리 memberId={}", managed.getMemberId());
    }

    /**
     * 도감: 회원이 지금까지 손에 넣은 장비·스킬·유물·소모품. 참조가 끊긴 행은 건너뛴다.
     *
     * @param member 회원
     * @return 도감
     */
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

    /**
     * 닉네임을 검사한다.
     *
     * @param nickname 닉네임
     * @throws IllegalArgumentException 금칙어이거나 중복이면
     */
    private void validateNickname(String nickname) {
        if (isForbidden(nickname)) {
            throw new IllegalArgumentException("사용할 수 없는 닉네임입니다.");
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
    }

    /**
     * 운영자·관리자 같은 사칭용 닉네임인지 확인한다 (공백 무시, 대소문자 무시).
     *
     * @param nickname 닉네임
     * @return 금칙어를 포함하면 true
     */
    private boolean isForbidden(String nickname) {
        String normalized = nickname.replaceAll("\\s", "").toLowerCase();
        return FORBIDDEN_NICKNAMES.stream().anyMatch(normalized::contains);
    }

    /**
     * 스킬 엔티티를 응답용 DTO로 옮긴다.
     *
     * @param s 스킬
     * @return DTO
     */
    private SkillDTO toSkillDTO(SkillEntity s) {
        return new SkillDTO(s.getName(), s.getTarget(), s.getAttackCount(), s.getCoefficient(), s.getCost(),
                s.getCooldown(), s.getStatusEffect(), s.getEffectStack(), s.getDescription());
    }

    /**
     * 유물 엔티티를 응답용 DTO로 옮긴다.
     *
     * @param r 유물
     * @return DTO
     */
    private RelicDTO toRelicDTO(RelicEntity r) {
        return new RelicDTO(r.getRelicId(), r.getName(), r.getEffectDesc(), r.getRarity(), r.getScoreCost());
    }

    /**
     * 소모품 엔티티를 응답용 DTO로 옮긴다.
     *
     * @param c 소모품
     * @return DTO
     */
    private ConsumableDTO toConsumableDTO(ConsumableEntity c) {
        return new ConsumableDTO(c.getConsumableId(), c.getName(), c.getEffectType(), c.getEffectValue(), c.getScoreCost(), c.getDescription());
    }

    /**
     * 무기 엔티티를 응답용 DTO로 옮긴다.
     *
     * @param w 무기
     * @return DTO
     */
    private WeaponDTO toWeaponDTO(WeaponEntity w) {
        return new WeaponDTO(w.getWeaponId(), w.getName(), w.getType(), w.getAtkBonus(), w.getSpecialEffect(), w.getScoreCost(), w.getDescription());
    }

    /**
     * 갑옷 엔티티를 응답용 DTO로 옮긴다.
     *
     * @param a 갑옷
     * @return DTO
     */
    private ArmorDTO toArmorDTO(ArmorEntity a) {
        return new ArmorDTO(a.getArmorId(), a.getName(), a.getType(), a.getDefBonus(), a.getSpecialEffect(), a.getScoreCost(), a.getDescription());
    }

    /**
     * 투구 엔티티를 응답용 DTO로 옮긴다.
     *
     * @param h 투구
     * @return DTO
     */
    private HelmetDTO toHelmetDTO(HelmetEntity h) {
        return new HelmetDTO(h.getHelmetId(), h.getName(), h.getType(), h.getHpBonus(), h.getSpecialEffect(), h.getScoreCost(), h.getDescription());
    }
}
