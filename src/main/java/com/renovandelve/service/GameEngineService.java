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

    /** 다음 노드로 전진. AI가 전투 또는 인카운터를 생성한다. */
    NodeDTO nextNode(MemberEntity member);

    /** 전투: 스킬 사용 (1턴 진행) */
    TurnResultDTO useSkill(MemberEntity member, SkillUseRequest request);

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

    /** 상점에서 나가 탐험 재개 */
    GameViewDTO leaveShop(MemberEntity member);

    /** 게임 종료(클리어/게임오버) 요약 - AI 서사 요약 포함 */
    GameSummaryDTO getSummary(MemberEntity member);
}
