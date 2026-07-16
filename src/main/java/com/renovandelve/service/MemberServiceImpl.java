package com.renovandelve.service;

import com.renovandelve.domain.*;
import com.renovandelve.domain.armorentity.ArmorEntity;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import com.renovandelve.dto.*;
import com.renovandelve.dto.request.SignupRequest;
import com.renovandelve.dto.response.IllustratedEncyclopediaDTO;
import com.renovandelve.repository.MemberRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Log4j2
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void signup(SignupRequest request) {
        if (request.loginId() == null || request.loginId().isBlank()) {
            throw new IllegalArgumentException("아이디를 입력해주세요.");
        }
        if (request.password() == null || request.password().length() < 4) {
            throw new IllegalArgumentException("비밀번호는 4자 이상이어야 합니다.");
        }
        memberRepository.findByLoginId(request.loginId()).ifPresent(member -> {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        });

        MemberEntity member = memberRepository.save(MemberEntity.builder()
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build());
        log.info("회원가입 완료 memberId={}", member.getMemberId());
    }

    @Override
    @Transactional
    public IllustratedEncyclopediaDTO getEncyclopedia(MemberEntity member) {
        // 컬렉션 Lazy 로딩을 위해 영속 상태로 재조회
        MemberEntity managed = memberRepository.findById(member.getMemberId())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        MemberDTO memberDTO = MemberDTO.builder().memberId(managed.getMemberId()).loginId(managed.getLoginId()).build();

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
