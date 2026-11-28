package com.renovandelve.sim;

import com.renovandelve.ai.service.AsyncPrefetchService;
import com.renovandelve.ai.service.GameDataEtlService;
import com.renovandelve.ai.service.MonsterRagService;
import com.renovandelve.ai.service.RunReviewService;
import com.renovandelve.ai.service.WraithVoiceService;
import com.renovandelve.game.engine.KingdomState;
import com.renovandelve.game.service.GameEngineService;
import com.renovandelve.game.service.GameStateService;
import com.renovandelve.game.service.KingdomService;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 밸런스 시뮬레이션. 실제 게임 엔진(서비스·저장소·Redis 세션)을 그대로 쓰고, AI만 서버 폴백으로 대체한다.
 *
 * - 노드 생성: MonsterRagService가 실패하면 엔진이 쓰는 폴백(랜덤 몬스터 / 기본 조우)을 그대로 쓴다
 * - 회고·망령 대사·프리페치·대화 기록·임베딩 동기화는 목 (LLM·임베딩 비용 없음)
 * - 왕국 정세는 균형으로 고정 (시뮬레이션 자체의 선택이 정세를 바꾸지 않게)
 *
 * 별도 DB와 Redis를 쓴다. 실행:
 * <pre>
 * RD_SIM=true RD_SIM_DB_URL=jdbc:postgresql://localhost:5433/renova_sim RD_SIM_DB_PASSWORD=... \
 *   RD_SIM_REDIS_PORT=6380 RD_SIM_RUNS=100 ./gradlew test --tests '*BalanceSimulation'
 * </pre>
 * 결과는 RD_SIM_OUT(기본 build/balance-report.md)에 표로 남는다.
 */
@EnabledIfEnvironmentVariable(named = "RD_SIM", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${RD_SIM_DB_URL}",
        "spring.datasource.password=${RD_SIM_DB_PASSWORD}",
        "spring.data.redis.port=${RD_SIM_REDIS_PORT:6380}",
        "spring.datasource.hikari.maximum-pool-size=16",
        "logging.level.root=WARN",
        "logging.level.com.renovandelve=ERROR",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.jpa.show-sql=false",
})
class BalanceSimulation {

    @MockitoBean private MonsterRagService monsterRagService;
    @MockitoBean private RunReviewService runReviewService;
    @MockitoBean private WraithVoiceService wraithVoiceService;
    @MockitoBean private AsyncPrefetchService asyncPrefetchService;
    @MockitoBean private ChatMemory chatMemory;
    @MockitoBean private GameDataEtlService gameDataEtlService;
    @MockitoBean private KingdomService kingdomService;

    @Autowired private GameEngineService engine;
    @Autowired private GameStateService gameStateService;
    @Autowired private MemberRepository memberRepository;

    @Test
    void simulate() throws Exception {
        when(monsterRagService.chat(anyInt(), anyInt(), anyString())).thenThrow(new IllegalStateException("simulation"));
        when(wraithVoiceService.appearance(any())).thenReturn("망령");
        when(kingdomService.currentState()).thenReturn(KingdomState.BALANCE);

        int runs = Integer.parseInt(System.getenv().getOrDefault("RD_SIM_RUNS", "100"));
        int threads = Integer.parseInt(System.getenv().getOrDefault("RD_SIM_THREADS", "8"));
        List<String> difficulties = List.of(System.getenv().getOrDefault("RD_SIM_DIFFICULTIES", "EASY,NORMAL,HARD,HELL").split(","));
        List<BotPolicy> policies = new ArrayList<>();
        for (String p : System.getenv().getOrDefault("RD_SIM_POLICIES", "CAUTIOUS,BALANCED,GREEDY").split(",")) {
            policies.add(BotPolicy.valueOf(p));
        }
        String tag = String.valueOf(System.currentTimeMillis() % 100000);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Map<String, List<Future<List<RunResult>>>> futures = new TreeMap<>();
        for (String difficulty : difficulties) {
            for (BotPolicy policy : policies) {
                List<Future<List<RunResult>>> list = new ArrayList<>();
                // 망령은 회원별로 이어지므로 봇 하나가 여러 판을 연달아 플레이한다. 한 회원의 판은 한 작업 안에서 차례로 돈다
                int members = Math.min(threads, runs);
                for (int m = 0; m < members; m++) {
                    MemberEntity member = memberRepository.save(MemberEntity.builder()
                            .loginId("sim_%s_%s_%s_%d".formatted(tag, difficulty, policy, m).toLowerCase())
                            .passwordHash("x").nickname("sim").build());
                    int count = runs / members + (m < runs % members ? 1 : 0);
                    list.add(pool.submit(() -> {
                        List<RunResult> results = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            results.add(new BotRunner(engine, gameStateService, member, policy, difficulty, i + 1).play());
                        }
                        return results;
                    }));
                }
                futures.put(difficulty + "/" + policy, list);
            }
        }

        StringBuilder report = new StringBuilder();
        StringBuilder extra = new StringBuilder();
        StringBuilder growth = new StringBuilder();
        report.append(Summary.HEADER);
        extra.append(System.lineSeparator()).append(Summary.EXTRA_HEADER);
        growth.append(System.lineSeparator()).append(Summary.GROWTH_HEADER);
        for (var entry : futures.entrySet()) {
            List<RunResult> results = new ArrayList<>();
            for (Future<List<RunResult>> f : entry.getValue()) results.addAll(f.get());
            Summary summary = Summary.of(entry.getKey(), results);
            report.append(summary.row()).append(System.lineSeparator());
            extra.append(summary.extraRow()).append(System.lineSeparator());
            growth.append(summary.growthRow()).append(System.lineSeparator());
        }
        report.append(extra).append(growth);
        pool.shutdown();

        String out = System.getenv().getOrDefault("RD_SIM_OUT", "build/balance-report.md");
        Files.writeString(Path.of(out), report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
    }
}
