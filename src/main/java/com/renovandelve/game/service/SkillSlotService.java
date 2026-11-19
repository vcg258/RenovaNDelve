package com.renovandelve.game.service;

import com.renovandelve.member.domain.MemberEntity;

import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.game.repository.GameSkillSlotRepository;
import com.renovandelve.catalog.repository.SkillRepository;
import com.renovandelve.collection.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * 스킬 슬롯 응용 서비스 (전투 보상, 상점 구매, 스킬 정리가 함께 쓴다).
 *
 * <p>슬롯 규칙(최대 8개, 중복 불가, 기본 공격 보호, 마지막 한 개 보호)은 애그리거트 루트인
 * {@link GameStateEntity}가 가진다. 이 서비스는 스킬을 찾아 루트에 넘기고, 새 슬롯의 ID가 바로
 * 필요한 경우에만 저장소를 부른다.</p>
 */
@Service
@RequiredArgsConstructor
public class SkillSlotService {
    public static final int MAX_SLOTS = GameStateEntity.MAX_SKILL_SLOTS;
    public static final String FULL_MESSAGE = GameStateEntity.SKILL_FULL_MESSAGE;
    public static final int MIN_SLOTS_TO_DROP_BASIC = GameStateEntity.MIN_SLOTS_TO_DROP_BASIC;
    public static final String BASIC_LOCKED_MESSAGE = GameStateEntity.BASIC_LOCKED_MESSAGE;
    public static final String LAST_SKILL_MESSAGE = GameStateEntity.LAST_SKILL_MESSAGE;

    private final CollectionService collectionService;
    private final GameSkillSlotRepository gameSkillSlotRepository;
    private final SkillRepository skillRepository;

    /**
     * 기본 공격인지 판정한다.
     *
     * @param skill 스킬 (null이면 false)
     * @return 기본 공격이면 true
     */
    public static boolean isBasic(SkillEntity skill) {
        return skill != null && skill.isBasic();
    }

    /**
     * 스킬을 배운다.
     *
     * @param gameState     스킬을 배울 게임
     * @param skillId       배울 스킬
     * @param replaceSlotId 버릴 슬롯의 ID. null이면 빈 슬롯에 추가한다
     * @return 새로 배운 스킬
     * @throws NoSuchElementException 스킬이 없으면
     * @throws IllegalStateException  이미 가진 스킬, 가득 참, 기본 공격 보호에 걸리면
     */
    public SkillEntity learn(GameStateEntity gameState, Long skillId, Long replaceSlotId) {
        if (gameState.hasSkill(skillId)) {
            throw new IllegalStateException("이미 보유 중인 스킬입니다.");
        }
        SkillEntity skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new NoSuchElementException("스킬을 찾을 수 없습니다."));

        // 배우는 순간 도감에 남긴다. 원정 끝에만 등록하면 도중에 교체한 스킬이 빠진다
        registerInCollection(gameState, skill);

        if (replaceSlotId != null) {
            gameState.replaceSkill(replaceSlotId, skill);
            return skill;
        }
        GameSkillSlotEntity slot = gameState.learnSkill(skill);
        // cascade로도 저장되지만 커밋 시점이라, 같은 응답에 담을 슬롯 ID를 지금 받기 위해 바로 저장한다
        gameSkillSlotRepository.save(slot);
        return skill;
    }

    /**
     * 스킬을 도감에 등록한다. 이미 있으면 그대로 둔다.
     *
     * @param gameState 게임 (회원)
     * @param skill     배운 스킬
     */
    private void registerInCollection(GameStateEntity gameState, SkillEntity skill) {
        collectionService.registerSkill(gameState.getMember(), skill);
    }

    /**
     * 스킬 하나를 버린다 (새 스킬을 얻지 않고 슬롯만 비운다).
     *
     * @param gameState 스킬을 버릴 게임
     * @param slotId    버릴 스킬이 든 슬롯
     * @return 버린 스킬
     * @throws IllegalStateException 마지막 한 개이거나 기본 공격 보호에 걸리면
     */
    public SkillEntity discard(GameStateEntity gameState, Long slotId) {
        return gameState.forgetSkill(slotId);
    }
}
