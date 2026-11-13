package com.renovandelve.service;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.dto.request.*;
import com.renovandelve.dto.response.*;

import java.util.List;

/**
 * 게임 진행 오케스트레이터.
 * 모든 진행 상태(노드/전투/보상/상점)는 서버가 소유하며,
 * 클라이언트는 "행동"만 보낼 수 있다.
 */
public interface GameEngineService {

    /** 현재 게임 전체 뷰 (이어하기 / 새로고침 복원) */
    GameViewDTO getView(MemberEntity member);

    /**
     * 다음 노드로 전진. AI가 전투 또는 인카운터를 생성한다.
     * @param path 갈림길 선택 (SAFE / DANGER / AMBUSH). null이면 SAFE
     */
    NodeDTO nextNode(MemberEntity member, String path);

    /** 전투: 스킬 사용 (1턴 진행) */
    TurnResultDTO useSkill(MemberEntity member, SkillUseRequest request);

    /** 전투: 방어 태세 (공격하지 않고 이번 턴 받는 피해를 크게 줄인다) */
    TurnResultDTO guard(MemberEntity member);

    /** 소모품 사용 (전투 중이면 턴 소모) */
    TurnResultDTO useConsumable(MemberEntity member, ConsumableUseRequest request);

    /** 인카운터 선택지 결정 */
    EncounterOutcomeDTO chooseEncounter(MemberEntity member, EncounterChoiceRequest request);

    /** 전투 보상 선택 (SKIP 가능) -> 상점 단계로 이동 */
    StatusDTO selectReward(MemberEntity member, RewardSelectRequest request);

    /** 상점 재고 조회 */
    List<ShopItemDTO> getShop(MemberEntity member);

    /** 상점 구매 */
    StatusDTO buyItem(MemberEntity member, PurchaseRequest request);

    /** 상점 재입고: 골드를 내고 진열품을 새로 뽑는다 (횟수마다 비용 상승) */
    GameViewDTO rerollShop(MemberEntity member);

    /** AI 런 회고 (게임 종료 후). 생성할 수 없으면 null */
    RunReviewDTO getRunReview(MemberEntity member);

    /** 모닥불 선택 (보스 처치 후): HEAL / TRAIN / HEAVEN / ABYSS / HUMAN */
    GameViewDTO chooseRest(MemberEntity member, String choice);

    /** 상점에서 나가 탐험 재개 */
    GameViewDTO leaveShop(MemberEntity member);

    /** 게임 종료(클리어/게임오버) 요약 - AI 서사 요약 포함 */
    GameSummaryDTO getSummary(MemberEntity member);
}
