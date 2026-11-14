package com.renovandelve.game.controller;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.game.dto.response.WraithDTO;
import com.renovandelve.game.dto.response.ChronicleChapterDTO;
import com.renovandelve.game.service.ChronicleService;
import com.renovandelve.game.service.WraithService;
import com.renovandelve.game.dto.request.ConsumableUseRequest;
import com.renovandelve.game.dto.request.EncounterChoiceRequest;
import com.renovandelve.game.dto.request.NewGameRequest;
import com.renovandelve.game.dto.request.NextNodeRequest;
import com.renovandelve.game.dto.request.PurchaseRequest;
import com.renovandelve.game.dto.request.RestChoiceRequest;
import com.renovandelve.game.dto.request.RewardSelectRequest;
import com.renovandelve.game.dto.request.SkillDiscardRequest;
import com.renovandelve.game.dto.request.SkillUseRequest;
import com.renovandelve.game.dto.response.EncounterOutcomeDTO;
import com.renovandelve.game.dto.response.GameSummaryDTO;
import com.renovandelve.game.dto.response.GameViewDTO;
import com.renovandelve.game.dto.response.NodeDTO;
import com.renovandelve.ai.dto.response.RunReviewDTO;
import com.renovandelve.game.dto.response.SetupOptionsDTO;
import com.renovandelve.game.dto.response.ShopItemDTO;
import com.renovandelve.game.dto.response.StatusDTO;
import com.renovandelve.game.dto.response.TurnResultDTO;
import com.renovandelve.member.service.CurrentMemberService;
import com.renovandelve.game.service.GameEngineService;
import com.renovandelve.game.service.GameStateService;
import com.renovandelve.game.service.redis.GameLockService;
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
    private final WraithService wraithService;
    private final ChronicleService chronicleService;

    /**
     * 게임 상태를 바꾸는 요청은 회원당 한 번에 하나만 처리한다.
     * (동시 요청으로 보상을 두 번 받거나 같은 상품을 두 번 사는 경쟁 상태 방지)
     */
    private <T> T serialized(MemberEntity member, Supplier<T> action) {
        return gameLockService.withLock(member.getMemberId(), action);
    }

    /**
     * 원정 준비 화면에 띄울 선택지(장비·스킬·유물·서약)와 스타팅 포인트 예산.
     *
     * @return 원정 준비 옵션
     */
    @Operation(summary = "새 게임 세팅 옵션 조회 (장비/스킬/유물 + 예산)")
    @GetMapping("/options")
    public ResponseEntity<SetupOptionsDTO> getOptions() {
        return ResponseEntity.ok(gameStateService.getSetupOptions(currentMemberService.getCurrentMember()));
    }

    /**
     * 다음 원정에서 기다리는 전생의 망령. 없으면 204.
     *
     * @return 망령 안내
     */
    @Operation(summary = "기다리는 전생의 망령 조회 (원정 준비 화면 안내)")
    @GetMapping("/wraith")
    public ResponseEntity<WraithDTO> wraith() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return wraithService.current(member.getMemberId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 레노바 연대기: 끝난 원정마다 한 장씩, 1장부터.
     *
     * @return 장 목록 (아직 없으면 빈 목록)
     */
    @Operation(summary = "레노바 연대기 조회")
    @GetMapping("/chronicle")
    public ResponseEntity<List<ChronicleChapterDTO>> chronicle() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(chronicleService.chapters(member.getMemberId()));
    }

    /**
     * 새 원정을 시작한다. 진행 중인 원정이 있으면 포기 처리하고 새로 만든다.
     *
     * @param request 난이도, 장비, 스킬, 유물, 서약
     * @return 새 원정의 첫 화면 상태
     */
    @Operation(summary = "새 게임 시작")
    @PostMapping("/new")
    public ResponseEntity<GameViewDTO> newGame(@Valid @RequestBody NewGameRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> {
            gameStateService.createNewGame(request, member);
            return gameEngineService.getView(member);
        }));
    }

    /**
     * 진행 중인 원정의 현재 화면 상태. 새로고침·기기 변경 시 화면 복원에 쓴다.
     *
     * @return 현재 상태 (원정이 없으면 phase = NONE)
     */
    @Operation(summary = "현재 게임 조회 (이어하기 / 새로고침 복원)")
    @GetMapping("/current")
    public ResponseEntity<GameViewDTO> current() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(gameEngineService.getView(member));
    }

    /**
     * 진행 중인 원정을 포기한다.
     *
     * @return 204
     */
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

    /**
     * 갈림길을 골라 다음 노드로 나아간다. AI가 서사와 전투/조우를 만든다.
     * 이미 전투·조우 중이면 새로 만들지 않고 현재 노드를 돌려준다.
     *
     * @param request 고른 길 (없으면 정면)
     * @return 새 노드
     */
    @Operation(summary = "다음 노드로 전진 (AI가 전투/인카운터 생성)")
    @PostMapping("/next")
    public ResponseEntity<NodeDTO> nextNode(@Valid @RequestBody(required = false) NextNodeRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        String path = request == null ? null : request.path();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.nextNode(member, path)));
    }

    /**
     * 전투: 스킬을 써서 한 턴을 진행한다.
     *
     * @param request 슬롯 ID와 대상
     * @return 턴 결과
     */
    @Operation(summary = "전투: 스킬 사용 (1턴)")
    @PostMapping("/battle/skill")
    public ResponseEntity<TurnResultDTO> useSkill(@Valid @RequestBody SkillUseRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.useSkill(member, request)));
    }

    /**
     * 전투: 방어 태세로 한 턴을 넘긴다 (받는 피해 -60%, 쿨타임 1턴 진행).
     *
     * @return 턴 결과
     */
    @Operation(summary = "전투: 방어 태세 (1턴)", description = "공격하지 않는 대신 이번 턴 받는 피해가 60% 감소하고 스킬 쿨타임이 1턴 진행됩니다.")
    @PostMapping("/battle/guard")
    public ResponseEntity<TurnResultDTO> guard() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.guard(member)));
    }

    /**
     * 전투: 숨 고르기. 모든 스킬이 쿨타임일 때만 가능하다 (피해 그대로, 쿨타임 2턴 진행).
     *
     * @return 턴 결과
     */
    @Operation(summary = "전투: 숨 고르기 (1턴)", description = "모든 스킬이 쿨타임일 때만 가능합니다. 피해는 그대로 받고 쿨타임이 2턴 줄어듭니다.")
    @PostMapping("/battle/pass")
    public ResponseEntity<TurnResultDTO> passTurn() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.passTurn(member)));
    }

    /**
     * 전투: 도망친다. 적이 한 번 공격한 뒤 보상 없이 전투가 끝난다. 보스전에서는 불가.
     *
     * @return 턴 결과 (result = FLED 또는 LOSE)
     */
    @Operation(summary = "전투: 도망치기", description = "보상 없이 전투를 끝냅니다. 적이 한 번 공격하며, 보스전에서는 불가능합니다.")
    @PostMapping("/battle/flee")
    public ResponseEntity<TurnResultDTO> flee() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.flee(member)));
    }

    /**
     * 스킬 하나를 버린다. 전투·조우 중에는 불가하고, 기본 공격은 스킬 3개 이상일 때만 버릴 수 있다.
     *
     * @param request 버릴 슬롯
     * @return 바뀐 상태
     */
    @Operation(summary = "스킬 버리기", description = "전투·조우 중에는 불가능합니다. 기본 공격은 스킬이 3개 이상일 때만 버릴 수 있습니다.")
    @PostMapping("/skill/discard")
    public ResponseEntity<StatusDTO> discardSkill(@Valid @RequestBody SkillDiscardRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.discardSkill(member, request)));
    }

    /**
     * 소모품을 쓴다. 전투 중이면 턴을 소모한다.
     *
     * @param request 소모품 칸 ID
     * @return 턴 결과
     */
    @Operation(summary = "소모품 사용 (전투 중이면 턴 소모)")
    @PostMapping("/battle/consumable")
    public ResponseEntity<TurnResultDTO> useConsumable(@Valid @RequestBody ConsumableUseRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.useConsumable(member, request)));
    }

    /**
     * 조우에서 선택지를 고른다. 결과는 서버가 세력 성격대로 정한다.
     *
     * @param request 선택지 번호
     * @return 선택 결과
     */
    @Operation(summary = "인카운터 선택")
    @PostMapping("/encounter")
    public ResponseEntity<EncounterOutcomeDTO> chooseEncounter(@RequestBody EncounterChoiceRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.chooseEncounter(member, request)));
    }

    /**
     * 전투 보상 중 하나를 고르거나 건너뛴다. 스킬이 가득 찼으면 버릴 슬롯을 함께 보낸다.
     *
     * @param request 보상 종류, ID, 버릴 슬롯
     * @return 바뀐 상태
     */
    @Operation(summary = "전투 보상 선택 (SKIP 가능)")
    @PostMapping("/reward")
    public ResponseEntity<StatusDTO> selectReward(@RequestBody RewardSelectRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.selectReward(member, request)));
    }

    /**
     * 현재 상점의 진열품.
     *
     * @return 진열품 목록
     */
    @Operation(summary = "상점 재고 조회")
    @GetMapping("/shop")
    public ResponseEntity<List<ShopItemDTO>> getShop() {
        return ResponseEntity.ok(gameEngineService.getShop(currentMemberService.getCurrentMember()));
    }

    /**
     * 상점에서 산다. 스킬이 가득 찼으면 버릴 슬롯을 함께 보낸다.
     *
     * @param request 품목 종류, ID, 버릴 슬롯
     * @return 바뀐 상태
     */
    @Operation(summary = "상점 구매")
    @PostMapping("/shop/buy")
    public ResponseEntity<StatusDTO> buyItem(@Valid @RequestBody PurchaseRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.buyItem(member, request)));
    }

    /**
     * 보스 처치 후 모닥불에서 휴식 / 단련 / 서원 중 하나를 고른다.
     *
     * @param request 선택 (HEAL, TRAIN, HEAVEN, ABYSS, HUMAN)
     * @return 다음 화면 상태
     */
    @Operation(summary = "모닥불 선택 (보스 처치 후)", description = "HEAL(휴식) / TRAIN(단련) / HEAVEN, ABYSS, HUMAN(서원)")
    @PostMapping("/rest")
    public ResponseEntity<GameViewDTO> chooseRest(@Valid @RequestBody RestChoiceRequest request) {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.chooseRest(member, request.choice())));
    }

    /**
     * 골드를 내고 상점 진열품을 새로 뽑는다. 할수록 비용이 오른다.
     *
     * @return 바뀐 화면 상태
     */
    @Operation(summary = "상점 재입고", description = "골드를 내고 진열품을 새로 뽑습니다. 재입고할수록 비용이 오릅니다.")
    @PostMapping("/shop/reroll")
    public ResponseEntity<GameViewDTO> rerollShop() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.rerollShop(member)));
    }

    /**
     * 끝난 원정의 AI 회고 (칭호, 원인 분석, 조언).
     *
     * @return 회고, 만들 수 없으면 204
     */
    @Operation(summary = "AI 런 회고", description = "종료된 게임의 칭호·원인 분석·조언 (구조화 출력). 생성할 수 없으면 204.")
    @GetMapping("/review")
    public ResponseEntity<RunReviewDTO> review() {
        MemberEntity member = currentMemberService.getCurrentMember();
        // 동시 호출 시 LLM을 두 번 부르지 않도록 같은 락으로 직렬화한다
        RunReviewDTO review = serialized(member, () -> gameEngineService.getRunReview(member));
        return review == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(review);
    }

    /**
     * 상점을 나가 탐험을 이어 간다.
     *
     * @return 다음 화면 상태
     */
    @Operation(summary = "상점에서 나가 탐험 재개")
    @PostMapping("/shop/leave")
    public ResponseEntity<GameViewDTO> leaveShop() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.leaveShop(member)));
    }

    /**
     * 걸린 악마의 계약을 파기한다. 천상 성향이 충분할 때 골드를 바치고, 탐험 중에만 할 수 있다.
     *
     * @return 바뀐 화면 상태
     */
    @Operation(summary = "악마의 계약 파기 (천상 성향 3 이상)")
    @PostMapping("/contract/break")
    public ResponseEntity<GameViewDTO> breakContract() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(serialized(member, () -> gameEngineService.breakContract(member)));
    }

    /**
     * 끝난 원정의 요약 (AI 서사 요약 포함).
     *
     * @return 요약
     */
    @Operation(summary = "게임 종료 요약 (AI 서사 요약 포함)")
    @GetMapping("/summary")
    public ResponseEntity<GameSummaryDTO> summary() {
        return ResponseEntity.ok(gameEngineService.getSummary(currentMemberService.getCurrentMember()));
    }
}
