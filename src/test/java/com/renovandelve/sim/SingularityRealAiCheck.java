package com.renovandelve.sim;

import com.renovandelve.ai.dto.response.AsyncStageResponseDTO;
import com.renovandelve.ai.dto.response.RunReviewDTO;
import com.renovandelve.ai.service.AsyncPrefetchService;
import com.renovandelve.ai.service.GameDataEtlService;
import com.renovandelve.ai.service.MonsterRagService;
import com.renovandelve.catalog.service.MonsterService;
import com.renovandelve.game.domain.ChronicleChapterEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.request.NewGameRequest;
import com.renovandelve.game.dto.response.NodeDTO;
import com.renovandelve.game.engine.GameSession;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.repository.ChronicleChapterRepository;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.game.repository.WraithRepository;
import com.renovandelve.game.domain.WraithEntity;
import com.renovandelve.game.service.GameEngineService;
import com.renovandelve.game.service.GameStateService;
import com.renovandelve.game.service.WraithService;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 특이점의 AI 부분을 실제 모델로 한 번 돌려 보는 점검 (OpenAI 호출 비용이 든다).
 * 노드 생성만 목으로 두고, 망령의 등장 장면과 런 회고(연대기의 장)는 실제 모델이 쓴다. 결과 문장은 표준 출력에 남긴다.
 * <pre>
 * RD_SIM=true RD_SIM_REAL_AI=true RD_SIM_DB_URL=... RD_SIM_DB_PASSWORD=... RD_SIM_REDIS_PORT=6380 \
 *   ./gradlew test --tests '*SingularityRealAiCheck'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "RD_SIM_REAL_AI", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${RD_SIM_DB_URL}",
        "spring.datasource.password=${RD_SIM_DB_PASSWORD}",
        "spring.data.redis.port=${RD_SIM_REDIS_PORT:6380}",
        "logging.level.root=WARN",
        "spring.jpa.show-sql=false",
})
class SingularityRealAiCheck {

    @MockitoBean private MonsterRagService monsterRagService;
    @MockitoBean private AsyncPrefetchService asyncPrefetchService;
    @MockitoBean private GameDataEtlService gameDataEtlService;

    @Autowired private GameEngineService engine;
    @Autowired private GameStateService gameStateService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private GameStateRepository gameStateRepository;
    @Autowired private WraithRepository wraithRepository;
    @Autowired private ChronicleChapterRepository chronicleChapterRepository;
    @Autowired private GameSessionService gameSessionService;
    @Autowired private MonsterService monsterService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void wraithVoiceAndChronicleWithRealModel() {
        when(monsterRagService.chat(anyInt(), anyInt(), anyString()))
                .thenAnswer(call -> AsyncStageResponseDTO.battle("무너진 정문 앞에서 그림자가 길을 막는다", monsterService.getRandomMonsterNormal()));
        String id = "ai" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MemberEntity member = memberRepository.save(MemberEntity.register(id, "x", id.substring(0, 10), "테스터", id + "@example.com", "test"));

        // 1판: 노드 5에서 쓰러진다 -> 회고와 연대기 1장
        GameStateEntity first = start(member);
        jdbc.update("update game_state set current_node_id = 4, abyss_affinity = 3 where game_state_id = ?", first.getGameStateId());
        engine.nextNode(member, "SAFE");
        die(member, first);
        RunReviewDTO review1 = engine.getRunReview(member);

        // 2판: 같은 자리에서 망령을 만난다 -> 실제 모델이 쓴 등장 장면. 그다음 다시 쓰러져 연대기 2장
        WraithEntity wraith = wraithRepository.findByMemberIdAndStatus(member.getMemberId(), WraithEntity.Status.ACTIVE).get(0);
        GameStateEntity second = start(member);
        jdbc.update("update game_state set current_node_id = ? where game_state_id = ?", wraith.getNode() - 1, second.getGameStateId());
        NodeDTO node = engine.nextNode(member, "SAFE");
        die(member, second);
        RunReviewDTO review2 = engine.getRunReview(member);

        List<ChronicleChapterEntity> chapters = chronicleChapterRepository.findByMemberIdOrderByChapterNoAsc(member.getMemberId());
        System.out.println("=== 망령 등장 장면 (" + node.nodeType() + ")\n" + node.story());
        System.out.println("=== 회고 1: " + review1);
        System.out.println("=== 회고 2: " + review2);
        chapters.forEach(c -> System.out.println("=== 연대기 제" + c.getChapterNo() + "장 「" + c.getTitle() + "」\n" + c.getBody()));

        assertThat(node.nodeType()).isEqualTo("WRAITH");
        assertThat(node.story()).isNotBlank();
        assertThat(chapters).hasSize(2);
    }

    private GameStateEntity start(MemberEntity member) {
        return gameStateService.createNewGame(new NewGameRequest("HARD", null, null, null, List.of(), List.of(), List.of()), member);
    }

    private void die(MemberEntity member, GameStateEntity game) {
        GameSession s = gameSessionService.get(game.getGameStateId());
        for (SessionMonster m : s.getMonsters()) {
            m.setAtk(100_000);
            m.setIntent("ATTACK");
        }
        gameSessionService.save(s);
        jdbc.update("update game_character set current_hp = 1 where game_state_id = ?", game.getGameStateId());
        engine.guard(member);
    }
}
