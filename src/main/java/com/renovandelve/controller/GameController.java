package com.renovandelve.controller;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.dto.request.*;
import com.renovandelve.dto.response.*;
import com.renovandelve.service.CurrentMemberService;
import com.renovandelve.service.GameEngineService;
import com.renovandelve.service.GameStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "새 게임 세팅 옵션 조회 (장비/스킬/유물 + 예산)")
    @GetMapping("/options")
    public ResponseEntity<SetupOptionsDTO> getOptions() {
        return ResponseEntity.ok(gameStateService.getSetupOptions());
    }

    @Operation(summary = "새 게임 시작")
    @PostMapping("/new")
    public ResponseEntity<GameViewDTO> newGame(@RequestBody NewGameRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        gameStateService.createNewGame(request, member);
        return ResponseEntity.ok(gameEngineService.getView(member));
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
        gameStateService.abandonGame(currentMemberService.getCurrentMember());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "다음 노드로 전진 (AI가 전투/인카운터 생성)")
    @PostMapping("/next")
    public ResponseEntity<NodeDTO> nextNode() {
        return ResponseEntity.ok(gameEngineService.nextNode(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "전투: 스킬 사용 (1턴)")
    @PostMapping("/battle/skill")
    public ResponseEntity<TurnResultDTO> useSkill(@RequestBody SkillUseRequest request) {
        return ResponseEntity.ok(gameEngineService.useSkill(currentMemberService.getCurrentMember(), request));
    }

    @Operation(summary = "소모품 사용 (전투 중이면 턴 소모)")
    @PostMapping("/battle/consumable")
    public ResponseEntity<TurnResultDTO> useConsumable(@RequestBody ConsumableUseRequest request) {
        return ResponseEntity.ok(gameEngineService.useConsumable(currentMemberService.getCurrentMember(), request));
    }

    @Operation(summary = "인카운터 선택")
    @PostMapping("/encounter")
    public ResponseEntity<EncounterOutcomeDTO> chooseEncounter(@RequestBody EncounterChoiceRequest request) {
        return ResponseEntity.ok(gameEngineService.chooseEncounter(currentMemberService.getCurrentMember(), request));
    }

    @Operation(summary = "전투 보상 선택 (SKIP 가능)")
    @PostMapping("/reward")
    public ResponseEntity<StatusDTO> selectReward(@RequestBody RewardSelectRequest request) {
        return ResponseEntity.ok(gameEngineService.selectReward(currentMemberService.getCurrentMember(), request));
    }

    @Operation(summary = "상점 재고 조회")
    @GetMapping("/shop")
    public ResponseEntity<List<ShopItemDTO>> getShop() {
        return ResponseEntity.ok(gameEngineService.getShop(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "상점 구매")
    @PostMapping("/shop/buy")
    public ResponseEntity<StatusDTO> buyItem(@RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(gameEngineService.buyItem(currentMemberService.getCurrentMember(), request));
    }

    @Operation(summary = "상점에서 나가 탐험 재개")
    @PostMapping("/shop/leave")
    public ResponseEntity<GameViewDTO> leaveShop() {
        return ResponseEntity.ok(gameEngineService.leaveShop(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "게임 종료 요약 (AI 서사 요약 포함)")
    @GetMapping("/summary")
    public ResponseEntity<GameSummaryDTO> summary() {
        return ResponseEntity.ok(gameEngineService.getSummary(currentMemberService.getCurrentMember()));
    }
}
