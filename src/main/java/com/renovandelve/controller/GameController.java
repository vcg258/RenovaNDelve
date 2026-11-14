package com.renovandelve.controller;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.dto.request.*;
import com.renovandelve.dto.response.*;
import com.renovandelve.service.CurrentMemberService;
import com.renovandelve.service.GameEngineService;
import com.renovandelve.service.GameStateService;
import com.renovandelve.service.redis.GameLockService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * 게임 진행 API.
 * 모든 엔드포인트는 JWT 인증 기반으로 "현재 로그인한 회원의" 게임만 조작한다.
 */
@Tag(name = "게임 진행", description = "게임 생성, 노드 이동, 전투, 인카운터, 보상, 상점")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/game")
public class GameController {
    private final GameEngineService gameEngineService;
    private final GameStateService gameStateService;
    private final CurrentMemberService currentMemberService;
    private final GameLockService gameLockService;

    /**
     * 게임 상태를 바꾸는 요청은 회원당 한 번에 하나만 처리한다.
     * (동시 요청으로 보상을 두 번 받거나 같은 상품을 두 번 사는 경쟁 상태 방지)
     */
    private <T> T serialized(MemberEntity member, Supplier<T> action) {
        return gameLockService.withLock(member.getMemberId(), action);
    }

    @Operation(summary = "새 게임 세팅 옵션 조회 (장비/스킬/유물 + 예산)")
    @GetMapping("/options")
    public ResponseEntity<SetupOptionsDTO> getOptions() {
        return ResponseEntity.ok(gameStateService.getSetupOptions(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "새 게임 시작")
    @PostMapping("/new")
    public ResponseEntity<GameViewDTO> newGame(@Valid @RequestBody NewGameRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> {
            gameStateService.createNewGame(request, member);
            return gameEngineService.getView(member);
        }));
    }

    @Operation(summary = "현재 게임 조회 (이어하기 / 새로고침 복원)")
    @GetMapping("/current")
    public ResponseEntity<GameViewDTO> current() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(gameEngineService.getView(member));
    }

    @Operation(summary = "진행 중인 게임 포기")
    @PostMapping("/abandon")
    public ResponseEntity<Void> abandon() {
        MemberEntity member = currentMemberService.getCurrentMember();
        serialized(member, () -> {
            gameStateService.abandonGame(member);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "다음 노드로 전진 (AI가 전투/인카운터 생성)")
    @PostMapping("/next")
    public ResponseEntity<NodeDTO> nextNode(@Valid @RequestBody(required = false) NextNodeRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        String path = request == null ? null : request.path();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.nextNode(member, path)));
    }

    @Operation(summary = "전투: 스킬 사용 (1턴)")
    @PostMapping("/battle/skill")
    public ResponseEntity<TurnResultDTO> useSkill(@Valid @RequestBody SkillUseRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.useSkill(member, request)));
    }

    @Operation(summary = "전투: 방어 태세 (1턴)", description = "공격하지 않는 대신 이번 턴 받는 피해가 60% 감소하고 스킬 쿨타임이 1턴 진행됩니다.")
    @PostMapping("/battle/guard")
    public ResponseEntity<TurnResultDTO> guard() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.guard(member)));
    }

    @Operation(summary = "전투: 숨 고르기 (1턴)", description = "모든 스킬이 쿨타임일 때만 가능합니다. 피해는 그대로 받고 쿨타임이 2턴 줄어듭니다.")
    @PostMapping("/battle/pass")
    public ResponseEntity<TurnResultDTO> passTurn() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.passTurn(member)));
    }

    @Operation(summary = "전투: 도망치기", description = "보상 없이 전투를 끝냅니다. 적이 한 번 공격하며, 보스전에서는 불가능합니다.")
    @PostMapping("/battle/flee")
    public ResponseEntity<TurnResultDTO> flee() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.flee(member)));
    }

    @Operation(summary = "스킬 버리기", description = "전투·조우 중에는 불가능합니다. 기본 공격은 스킬이 3개 이상일 때만 버릴 수 있습니다.")
    @PostMapping("/skill/discard")
    public ResponseEntity<StatusDTO> discardSkill(@Valid @RequestBody SkillDiscardRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.discardSkill(member, request)));
    }

    @Operation(summary = "소모품 사용 (전투 중이면 턴 소모)")
    @PostMapping("/battle/consumable")
    public ResponseEntity<TurnResultDTO> useConsumable(@Valid @RequestBody ConsumableUseRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.useConsumable(member, request)));
    }

    @Operation(summary = "인카운터 선택")
    @PostMapping("/encounter")
    public ResponseEntity<EncounterOutcomeDTO> chooseEncounter(@RequestBody EncounterChoiceRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.chooseEncounter(member, request)));
    }

    @Operation(summary = "전투 보상 선택 (SKIP 가능)")
    @PostMapping("/reward")
    public ResponseEntity<StatusDTO> selectReward(@RequestBody RewardSelectRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.selectReward(member, request)));
    }

    @Operation(summary = "상점 재고 조회")
    @GetMapping("/shop")
    public ResponseEntity<List<ShopItemDTO>> getShop() {
        return ResponseEntity.ok(gameEngineService.getShop(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "상점 구매")
    @PostMapping("/shop/buy")
    public ResponseEntity<StatusDTO> buyItem(@Valid @RequestBody PurchaseRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.buyItem(member, request)));
    }

    @Operation(summary = "모닥불 선택 (보스 처치 후)", description = "HEAL(휴식) / TRAIN(단련) / HEAVEN, ABYSS, HUMAN(서원)")
    @PostMapping("/rest")
    public ResponseEntity<GameViewDTO> chooseRest(@Valid @RequestBody RestChoiceRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.chooseRest(member, request.choice())));
    }

    @Operation(summary = "상점 재입고", description = "골드를 내고 진열품을 새로 뽑습니다. 재입고할수록 비용이 오릅니다.")
    @PostMapping("/shop/reroll")
    public ResponseEntity<GameViewDTO> rerollShop() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.rerollShop(member)));
    }

    @Operation(summary = "AI 런 회고", description = "종료된 게임의 칭호·원인 분석·조언 (구조화 출력). 생성할 수 없으면 204.")
    @GetMapping("/review")
    public ResponseEntity<RunReviewDTO> review() {
        MemberEntity member = currentMemberService.getCurrentMember();
        // 동시 호출 시 LLM을 두 번 부르지 않도록 같은 락으로 직렬화한다
        RunReviewDTO review = serialized(member, () -> gameEngineService.getRunReview(member));
        return review == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(review);
    }

    @Operation(summary = "상점에서 나가 탐험 재개")
    @PostMapping("/shop/leave")
    public ResponseEntity<GameViewDTO> leaveShop() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.leaveShop(member)));
    }

    @Operation(summary = "게임 종료 요약 (AI 서사 요약 포함)")
    @GetMapping("/summary")
    public ResponseEntity<GameSummaryDTO> summary() {
        return ResponseEntity.ok(gameEngineService.getSummary(currentMemberService.getCurrentMember()));
    }
}
