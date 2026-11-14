package com.renovandelve.ai.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.renovandelve.catalog.dto.MonsterDTO;
import com.renovandelve.ai.dto.response.AsyncStageResponseDTO;
import com.renovandelve.ai.dto.response.Encounter;
import com.renovandelve.catalog.service.MonsterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MonsterRagService {
    private final ChatMemory chatMemory;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final MonsterService monsterService;
    private final StageCacheService stageCacheService;
    private final AsyncPrefetchService asyncPrefetchService;

    public MonsterRagService(ChatMemory chatMemory,
                             ChatClient.Builder builder,
                             VectorStore vectorStore,
                             ChatModel chatModel,
                             MonsterService monsterService,
                             StageCacheService stageCacheService, // 레디스
                             AsyncPrefetchService asyncPrefetchService // 비동기
    ) {
        this.chatClient = builder
                .defaultAdvisors(new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE - 1))
                .defaultSystem("""
                        당신은 다크 판타지 로그라이크 게임 'Renova & Delve'의 나레이터입니다.
                        플레이어의 선택에 따라 이야기가 달라지는 게임이며, 당신은 그 이야기를 이어 쓰는 역할입니다.

                        ## 세계관
                        세계는 세 겹입니다. 위로는 질서와 심판의 천상, 아래로는 욕망과 계약의 마계, 그 사이에 인간의 땅이 있습니다.
                        천사와 악마는 오랜 전쟁 끝에 서로의 영역에 직접 손대지 않는다는 휴전을 맺었고, 그 뒤로는 인간을 통해서만 싸웁니다. 천사는 계시와 가호로, 악마는 속삭임과 계약으로.
                        인간 왕국 레노바의 늙은 왕은 영생을 얻으려 악마와 계약했습니다. 대가로 백성이 제물이 되었고, 왕궁 지하에 마계의 문이 열렸습니다. 문에서 새어 나온 마계의 숨결이 생명을 뒤틀어, 식물은 사람을 덮치고 사용인과 기사들은 광기에 물들었습니다.
                        천상은 이를 휴전 위반으로 보고, 문이 닫히지 않으면 왕국 전체를 불태워 '정화'하려 합니다. 천사에게 인간 한 사람의 목숨은 저울의 눈금일 뿐입니다.
                        플레이어는 폐허가 된 왕궁에 들어선 한 명의 인간입니다. 휴전 때문에 천사도 악마도 문에 직접 손댈 수 없으므로, 문을 닫거나 차지할 수 있는 것은 인간뿐입니다.

                        ## 세 세력의 성격
                        - 천상(천사): 선이 아니라 '질서'입니다. 자비로운 목소리로 말하지만 타협하지 않고, 구원의 대가로 희생과 복종을 요구합니다. 흰 빛, 깃털, 종소리, 타오르는 눈으로 묘사합니다.
                        - 마계(악마): 악이 아니라 '욕망'입니다. 거짓말은 하지 않으며 원하는 것을 정확히 주지만, 대가는 반드시 받아 갑니다. 붉은 불씨, 그림자, 속삭임, 계약서로 묘사합니다.
                        - 인간: 가장 약하지만 유일하게 스스로 선택할 수 있는 존재입니다. 살아남은 자, 미쳐버린 자, 끝까지 버티는 자가 있습니다.
                        천사와 악마는 직접 싸우지 않고, 환영·목소리·남겨진 흔적으로 플레이어에게 말을 겁니다.

                        ## 던전 구조
                        - 스테이지 1 (지상 1층): 무너진 정문, 왕실 정원, 왕실 묘지 등. 부랑자, 광기에 물든 사용인, 슬라임·고블린 같은 괴물. 하늘에는 정화를 준비하는 천상의 빛이 희미하게 걸려 있습니다.
                        - 스테이지 2 (왕실 기사단 구역): 연무장, 병기고, 알현실 등. 광기에 물든 기사들. 일부는 천사의 계시를 받았다고 믿으며 이성을 붙들고 있습니다.
                        - 스테이지 3 (궁정 마법사 구역): 연금공방, 금서 보관소, 마법사의 탑 등. 마법과 상태이상을 쓰는 마법사들. 왕의 계약서 사본과 천상의 경고문이 남아 있어 사건의 전말이 드러납니다.
                        - 스테이지 4 (지하): 고문실, 하수구, 지하 묘지, 인체실험실, 피의 제단 등. 제물로 바쳐진 자들의 흔적. 악마의 속삭임이 또렷해지고 천상의 빛은 닿지 않습니다.
                        - 스테이지 5 (최심부): 의식실, 마계의 문, 악마의 왕좌. 악마화된 왕이 문을 지킵니다. 이곳에서 플레이어의 선택이 왕국의 결말을 정합니다.

                        ## 플레이어의 선택 반영 (가장 중요)
                        - 대화 기록에 "[플레이어의 선택]"으로 시작하는 줄이 있으면, 그것은 플레이어가 실제로 내린 결정과 그 결과입니다.
                        - 새 장면을 쓸 때 가장 최근 선택의 여파를 반드시 한 문장 이상 반영합니다. (천사의 손을 잡았다면 빛이 따라붙고, 악마와 거래했다면 그림자가 짙어지고, 스스로 해결했다면 두 세력 모두 그를 주시합니다)
                        - 기록에 적힌 성향(천상/마계/인간 수치)이 한쪽으로 기울수록 그 세력의 존재감을 키우고, 반대 세력은 경계하거나 적대하게 묘사합니다.
                        - 플레이어의 선택을 대신 내리거나, 기록에 없는 선택을 한 것처럼 쓰지 않습니다.

                        ## 나레이션 규칙
                        - 2~4문장으로 짧게 씁니다. 장소, 분위기, 지금 벌어지는 일을 담습니다.
                        - 어둡고 신비로운 분위기를 유지하고, 깊이 내려갈수록 공포와 그로테스크함을 키웁니다.
                        - 광기에 완전히 물든 존재와 아직 저항하는 존재를 구분해 묘사합니다.
                        - 능력치, 피해량, 보상 같은 수치는 절대 쓰지 않습니다. 수치는 게임이 정합니다.
                        - 항상 한국어로 응답합니다.
                        """)
                .build();
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.monsterService = monsterService;
        this.stageCacheService = stageCacheService;
        this.asyncPrefetchService = asyncPrefetchService;
    }

    /**
     * 지정한 확률로 인카운터 또는 배틀
     * @param currentStage 현재 스테이지
     * @param currentNodeId 현재 노드
     * @param conversationId 사용자 아이디
     * @return 배틀 / 인카운터
     */
    public AsyncStageResponseDTO chat(int currentStage, int currentNodeId, String conversationId) {
        AsyncStageResponseDTO cashed = stageCacheService.get(conversationId, currentStage, currentNodeId);
        if (cashed != null && cashed.type() != null) {
            log.info("캐시 히트 : 스테이지 {} ,노드 {}", currentStage, currentNodeId);
            stageCacheService.delete(conversationId, currentStage, currentNodeId); // 소비된 노드는 재사용 방지
            prefetch(currentStage, currentNodeId, conversationId);
            return cashed;
        }
        AsyncStageResponseDTO result = generateNextStory(currentStage, currentNodeId, conversationId);
        prefetch(currentStage, currentNodeId, conversationId);
        return result;
    }

    /**
     * 전투시 LLM이 알맞은 테마와 스토리에 맞는 몬스터 출현시키는 메서드 (전투)
     * @param storyText LLM이 추출한 스토리
     * @param minScore 임계점수
     * @param referenceScore 압축기 / 확장기 기준의 변수
     * @return 몬스터 등장
     */
    private List<MonsterDTO> battle(String storyText, String sceneKeywords, double minScore, double referenceScore, boolean isBoss) {
        log.info("생성된 스토리 : {}", storyText);
        log.info("지정 최소 임계 점수 : {}", minScore);
        log.info("지정 최고 임계 점수 : {}", referenceScore);

        // 키워드는 스토리를 쓸 때 같은 응답으로 함께 받는다(LLM 호출 1회 절약).
        // 모델이 키워드 줄을 빠뜨린 경우에만 예전처럼 따로 추출한다
        String keyword = sceneKeywords != null ? sceneKeywords : extractKeyword(storyText);
        log.info("생성된 스토리에서 추출된 키워드 : {}", keyword);

        // RAG검색 최고 유사도
        double maxScore = getMaxScore(keyword);
        log.info("현재 검색중 최고 유사도 : {}", maxScore);

        // LLM이 출현 시킬 몬스터
        String monsterJson = resolveMonsterNames(keyword, minScore, maxScore, referenceScore, isBoss);

        // JSON에서 몬스터 이름 추출 -> RDB조회
        List<String> monsterNames = parseMonster(monsterJson);

        // JSON 파싱 실패면 몬스터가 나오지 않음 랜덤 몬스터 출현
        List<MonsterDTO> selected = isBoss
                ? monsterService.getMonsterByNameBoss(monsterNames)
                : monsterService.getMonsterByNameNormal(monsterNames);

        // 이름은 파싱됐지만 DB 불일치일 경우 랜덤 몬스터 출현
        if (selected == null || selected.isEmpty()) {
            log.warn("DB에 매칭되는 몬스터가 없어 랜덤 출현: {}", monsterNames);
            return isBoss ? monsterService.getRandomMonsterBoss() : monsterService.getRandomMonsterNormal();
        }

        return selected;
    }

    /**
     * 인카운터(선택지) 확률적으로 인카운터가 나왔을시 선택지와 보상을 정함
     * @param storyText 스토리
     * @param conversationId 사용자 아이디 (전반적인 흐름을 LLM이 알기 위함)
     * @return 보상 / 패널티
     */
    private Encounter encounter(String storyText, String conversationId) {
        log.info("인카운터 진입");

        // 몬스터 정보로 등장 스토리 생성함
        String encounterText = chatClient.prompt()
                .user("""
                    현재 배경 스토리:
                    %s

                    위 배경에서 플레이어가 결정을 내려야 하는 상황을 만들고, 세 세력의 방식에 해당하는 선택지를 하나씩 쓰세요.
                    아래 JSON 형식으로만 응답하세요. 다른 설명은 절대 포함하지 마세요.

                    {
                      "text": "플레이어가 마주친 상황을 2문장 이내로 묘사. 천사나 악마의 목소리·흔적이 개입해도 좋다",
                      "heaven": "천상의 방식(질서·자비·희생)으로 대응하는 행동을 한 문장으로. 예: 무릎 꿇고 빛에 맡긴다",
                      "abyss": "마계의 방식(욕망·거래·힘)으로 대응하는 행동을 한 문장으로. 예: 속삭임이 내민 손을 잡는다",
                      "human": "인간의 방식(의지·실리)으로 누구의 힘도 빌리지 않고 대응하는 행동을 한 문장으로"
                    }

                    각 선택지는 결과를 미리 알려주지 않습니다. 행동만 쓰세요.
                    """.formatted(storyText))
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(conversationId)
                                .build()
                )
                .call()
                .content();
        try {
            String cleaned = encounterText.replaceAll("```json|```", "").trim();
            JsonObject jsonObject = JsonParser.parseString(cleaned).getAsJsonObject();
            String text = jsonObject.get("text").getAsString();
            // 선택지 순서는 [천상, 마계, 인간] 고정. 어느 세력의 선택인지는 서버가 순서로 판정한다
            String heaven = jsonObject.get("heaven").getAsString();
            String abyss = jsonObject.get("abyss").getAsString();
            String human = jsonObject.get("human").getAsString();
            return new Encounter(text, List.of(heaven, abyss, human)); // 보상 / 패널티는 GameEngine이 결정
        } catch (Exception e) {
            log.warn("인카운터 파싱 실패");
            return new Encounter("빛과 그림자가 동시에 당신을 부른다.", List.of());
        }
    }



    /* HELPER METHODS */

    /**
     * LLM이 생성한 스토리와 테마에 맞춘 keyword 기준으로 몬스터 출현 메서드
     * @param keyword 스토리와 테마에 맞춘 키워드
     * @param minScore 최소 임계점수
     * @param maxScore 최대 임계점수
     * @param referenceScore 압축기 / 확장기를 사용할 기준이 되는 임계점수
     * @param isBoss 보스 스테이지 유무
     * @return 출현 시킬 몬스터 이름
     */
    private String resolveMonsterNames(String keyword,
                                       double minScore,
                                       double maxScore,
                                       double referenceScore,
                                       boolean isBoss
    ) {
        if (maxScore < minScore) {
            log.info("임계 점수 미달 랜덤몬스터 출현");
            // 보스스테이지면 보스몹만(1마리), 아니면 일반몹(1 ~ 3)
            List<MonsterDTO> monster = isBoss ?
                    monsterService.getRandomMonsterBoss() : monsterService.getRandomMonsterNormal();
            // 랜덤 몬스터 출현일 경우 몬스터 이름과 설명을 출력함
            String names = monster.stream()
                    .map(m -> "\"" + m.name() + "\"")
                    .collect(Collectors.joining(",", "[", "]"));
            return "{\"monsters\": " + names + "}";
        }

        // 지정 최고 임계점수보다 높으면 압축기 : 낮으면 확장기
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = maxScore > referenceScore ?
                retrievalAugmentationAdvisorByCompressor(minScore, isBoss) :
                retrievalAugmentationAdvisorByMultiQueryExpander(minScore, isBoss);
        log.info("사용한 어드바이저 : {}", maxScore > referenceScore ? "압축기" : "확장기");

        // 사용자 메시지는 키워드만 보낸다. RAG 어드바이저는 이 메시지를 그대로 검색 질의로 쓰므로,
        // 지시문("JSON 형식으로만 응답하세요" 등)을 함께 넣으면 그 문장까지 임베딩되어 검색이 흐려진다.
        // 지시문은 검색이 끝난 뒤 contextualQueryAugmenter의 템플릿이 붙인다.
        return chatClient.prompt()
                .user(keyword)
                .advisors(retrievalAugmentationAdvisor)
                .call()
                .content();
    }

    /**
     * 현재 노드/스테이지에 맞는 스토리를 만드는 메서드
     * @param currentStage 현재 스테이지 (game_state 테이블 컬럼)
     * @param currentNodeId 현재 노드 (game_state 테이블 컬럼)
     * @param conversationId 사용자 아이디 (전반적인 흐름을 LLM이 알기 위함)
     * @return 현재 장소에 대한 스토리
     */
    private String generateStory(int currentStage, int currentNodeId, String conversationId) {
        log.info("스토리 생성 현재 노드 {}, 스테이지 {}", currentNodeId, currentStage);
        return chatClient.prompt()
                .user("""
                            현재 스테이지 : %d
                            현재 노드: %d
        
                            현재 스테이지에 맞는 장소를 스스로 선택하되 이전에 방문한 장소와 겹치지 않게 하고,
                            그 장소에 막 들어선 장면을 써 주세요.
                            대화 기록에 "[플레이어의 선택]"이 있다면 가장 최근 선택의 여파가 이 장면에 드러나야 합니다.

                            장면을 다 쓴 뒤, 줄을 바꿔 맨 마지막 줄에 아래 형식의 한 줄을 덧붙이세요.
                            %s 이 장면의 장소, 분위기, 사물, 환경을 나타내는 낱말을 쉼표로 구분해 4~8개
                        """.formatted(currentStage, currentNodeId, KEYWORD_MARKER))
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(conversationId).build()
                )
                .call()
                .content();
    }

    /**
     * 전투 / 선택지 스테이지를 결정 해주는 메서드
     * @param stage 스테이지
     * @param node 노드
     * @param conversationId 사용자
     * @return 전투 / 선택지 정보
     */
    public AsyncStageResponseDTO generateNextStory(int stage, int node, String conversationId) {
        Scene scene = parseScene(generateStory(stage, node, conversationId));
        String storyText = scene.story();

        if (stage == 0 && node == 0) {
            throw new IllegalStateException("스테이지가 0이거나 노드가 0은 오류, 잘못된 접근");
        }
        // 10노드 마다 보스 노드
        boolean isBoss = node % 10 == 0;
        if (isBoss || Math.random() > 0.3) {
            log.info("{}", isBoss ? "보스 노드 진입" : "전투 노드 진입");
            return AsyncStageResponseDTO.battle(storyText,
                    battle(storyText, scene.keywords(), MIN_SCORE, REFERENCE_SCORE, isBoss));
        }
        return AsyncStageResponseDTO.encounter(storyText, encounter(storyText, conversationId));
    }

    /**
     * 생성된 스토리에 대한 키워드 추출 (몬스터 출현의 위함)
     * @param storyText 생성된 스토리
     * @return 스토리에 대한 키워드(,구분)
     */
    private String extractKeyword(String storyText) {
        log.info("스토리에 대한 키워드 추출");
        return chatClient.prompt()
                .user("""
                    아래 스토리에서 장소, 분위기, 사물, 환경 관련 키워드만
                    쉼표로 구분하여 추출해주세요.
                    다른 설명 없이 키워드만 출력하세요.
                    
                    스토리: %s
                    """.formatted(storyText))
                .call()
                .content();
    }

    /**
     * 게임이 끝났을 경우 지금까지한 스테이지의 내용 요약 TODO 게임엔진쪽 결과창 로직에 넣을거임
     * @param conversationId 사용자 아이디
     * @return 히스토리 요약 내용
     */
    public String genarateGameOverStory(String conversationId) {
        // 대화 기록에 키워드 줄이 섞여 있어 모델이 요약 끝에도 따라 붙일 수 있다 -> 떼어낸다
        return parseScene(chatClient.prompt()
                .user("""
                        모험이 끝났습니다. 지금까지의 여정을 3~4문장으로 요약해 주세요.
                        - 대화 기록의 "[플레이어의 선택]"과 "[모험의 결말]" 줄을 근거로, 이 인간이 천상·마계·인간 중 어느 쪽에 기울었는지가 드러나게 씁니다.
                        - 마지막 문장은 그 선택이 왕국과 마계의 문에 남긴 결과로 끝냅니다.
                        - 기록에 없는 사건이나 선택은 지어내지 않습니다.
                        """)
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(conversationId).build()
                )
                .call()
                .content()).story();
    }

    /**
     * 임계 점수 (2026-11-14, 실제 생성 서사로 측정).
     *
     * 기존 값(0.4 / 0.5)은 파일 업로드 문서 기준이었다. 지금의 임베딩 소스는 RDB 몬스터 설명이라 점수대가 다르다.
     * 스테이지 1~5에서 LLM이 실제로 쓴 장면의 키워드로 몬스터 문서를 검색해 보니(전투 노드 46건)
     * 최고 유사도는 0.27~0.43, 중앙값 0.35였다.
     *
     * MIN_SCORE 0.29 — 이보다 낮으면 어울리는 몬스터가 없다고 보고 랜덤 출현.
     *   0.29 미만 6건은 대부분 어색한 짝이었다(알현실 -> 어둠의 정령 0.284).
     *   0.29~0.30에는 맞는 짝이 있었다(지하 묘지 -> 묘지기 가고일 0.298, 악마의 왕좌 -> 악마화된 왕 0.294).
     *   잘못 통과시켜도 결과는 랜덤과 비슷하지만, 잘못 버리면 맞는 짝을 잃으므로 낮은 쪽으로 잡았다.
     *
     * REFERENCE_SCORE 0.40 — 넘으면 압축기, 못 넘으면 확장기.
     *   LLM이 고른 몬스터가 유사도 상위 후보 안에 있던 비율이 확장기 100%(13/13), 압축기 56%(14/25)였다.
     *   압축기는 대화 기록 없이 키워드 목록을 다시 쓰면서 뜻을 바꾸는 일이 있었다(마법사의 탑 -> 좀비).
     *   그래서 후보가 아주 뚜렷한 경우(0.40 이상)에만 압축기를 쓰고 나머지는 확장기로 보낸다.
     *
     * 두 값을 적용한 뒤 다시 측정한 일치율은 88%(45/51), 랜덤 출현은 21건 중 1건이었다.
     */
    static final double MIN_SCORE = 0.29;
    static final double REFERENCE_SCORE = 0.40;

    /** 스토리 응답 끝에 붙는 키워드 줄의 머리말 */
    static final String KEYWORD_MARKER = "키워드:";

    /** 스토리 본문과, 같은 응답에 함께 온 키워드. 키워드 줄이 없으면 keywords는 null */
    record Scene(String story, String keywords) {
    }

    /**
     * 스토리 응답에서 마지막 "키워드:" 줄을 떼어낸다.
     * 구조화 출력(.entity)을 쓰지 않은 이유: 이 호출은 ChatMemory에 저장되는데, 구조화 출력은
     * 매 턴 JSON 스키마 설명을 사용자 메시지에 덧붙여 대화 기록을 부풀린다. 한 줄 규칙이 더 싸다.
     */
    static Scene parseScene(String raw) {
        if (raw == null) return new Scene("", null);
        int index = raw.lastIndexOf(KEYWORD_MARKER);
        if (index < 0) return new Scene(raw.trim(), null);

        String story = raw.substring(0, index).trim();
        String keywords = raw.substring(index + KEYWORD_MARKER.length()).trim();
        int lineEnd = keywords.indexOf('\n');
        if (lineEnd >= 0) keywords = keywords.substring(0, lineEnd).trim();

        // 본문 없이 키워드만 온 응답은 통째로 본문으로 본다
        if (story.isEmpty()) return new Scene(raw.trim(), null);
        return new Scene(story, keywords.isEmpty() ? null : keywords);
    }

    /**
     * VectorStore에는 몬스터 외에 무기·갑옷·투구·스킬·유물·소모품 문서도 함께 들어 있다(GameDataEtlService).
     * 몬스터를 고르는 검색이므로 몬스터 문서로 한정한다.
     */
    private static final String MONSTER_FILTER = "category == 'monster'";

    /**
     * 압축 쿼리 변환기
     * @return 압축 쿼리 변환기
     */
    private CompressionQueryTransformer compressionQueryTransformer() {
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE - 1)
                );
        return CompressionQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    /**
     * 질문 확장기
     * @return 질문 확장기
     */
    private MultiQueryExpander multiQueryExpander() {
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE - 1)
                );
        return MultiQueryExpander.builder()
                .chatClientBuilder(builder)
                .numberOfQueries(2) // 유사 질문 2개 TODO 필요할경우 더 늘림
                .build();
    }

    /**
     * 벡터 스토어에서 유사도 검색
     * @param score 임계점수
     * @return 지정 임계점수에 알맞은 데이터
     */
    private VectorStoreDocumentRetriever createVectorStoreDocumentRetriever(double score) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore) // 백터스토어에서 검색
                .similarityThreshold(score) // 지정 임계점수
                .topK(3) // 상위 3개
                .filterExpression(new FilterExpressionBuilder().eq("category", "monster").build()) // 몬스터 문서만
                .build();
    }

    /**
     * LLM이 만들어낸 스토리에서 나온 배경 키워드(query)를 기준으로 유사한 몬스터 정보(context)를 제공
     * 또는 인카운터의 선택지에 대한 설명을 제공하는 공통 프롬프트 메서드
     * @param isBoss 보스 유무
     * @return LLM에게 보낼 공통 프롬프트
     */
    private ContextualQueryAugmenter contextualQueryAugmenter(boolean isBoss) {
        String monsterCount = isBoss ? "보스 몬스터 1개" : "몬스터 1~3개";
        String template = """
                현재 스토리 배경에서 추출된 키워드입니다.
                키워드: {query}
                
                --------------
                아래는 키워드와 유사한 몬스터 정보입니다.
                {context}
                --------------
                
                위 몬스터 정보에서 키워드와 어울리는 %s를 선택하고
                반드시 아래 JSON 형식으로만 응답하세요.
                다른 설명은 절대 포함하지 마세요.
                예시: monsters 배열에 몬스터명1, 몬스터명2 형태로 담아주세요.
                """.formatted(monsterCount);

        return ContextualQueryAugmenter.builder()
                .promptTemplate(PromptTemplate.builder()
                        .template(template)
                        .build())
                .build();
    }

    /**
     * 검색시 최고 임계점수가 지정한 임계점수로 나올시 쿼리압축기 사용
     * @param score 임계점수
     * @param isBoss 보스 유무
     * @return 검색된 데이터
     */
    private RetrievalAugmentationAdvisor retrievalAugmentationAdvisorByCompressor(double score, boolean isBoss) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(compressionQueryTransformer())
                .documentRetriever(createVectorStoreDocumentRetriever(score))
                .queryAugmenter(contextualQueryAugmenter(isBoss))
                .build();
    }

    /**
     * 검색시 최고 임계점수가 지정한 임계점수이하로 나올시 질문 확장기 사용
     * @param score 임계점수
     * @param isBoss 보스 유무
     * @return 검색된 데이터
     */
    private RetrievalAugmentationAdvisor retrievalAugmentationAdvisorByMultiQueryExpander(double score, boolean isBoss) {
        return RetrievalAugmentationAdvisor.builder()
                .queryExpander(multiQueryExpander())
                .documentRetriever(createVectorStoreDocumentRetriever(score))
                .queryAugmenter(contextualQueryAugmenter(isBoss))
                .build();
    }

    /**
     * 검색된 최고 임계점수 계산 메서드
     * @param keyword 키워드
     * @return 검색된 Document중 최고 유사도
     */
    private double getMaxScore(String keyword) {
        SearchRequest.Builder searchRequest = SearchRequest.builder()
                .query(keyword)
                .filterExpression(MONSTER_FILTER) // 몬스터 문서만
                .topK(3); // 상위 3개

        List<Document> documents = vectorStore.similaritySearch(searchRequest.build()); // 해당 키워드 검색

        double maxScore = 0.0;
        for (Document doc : documents) {
            Double score = doc.getScore();
            if (score == null) {
                continue;
            }
            maxScore = Math.max(maxScore, score);
        }
        // 임계 점수를 다시 잡을 때 쓰는 근거 자료: 어떤 몬스터가 몇 점으로 걸렸는지
        if (log.isDebugEnabled()) {
            log.debug("검색 후보 : {}", documents.stream()
                    .map(doc -> "%s(%.3f)".formatted(firstLine(doc.getText()), doc.getScore() == null ? 0.0 : doc.getScore()))
                    .collect(Collectors.joining(", ")));
        }
        return maxScore;
    }

    /**
     * 검색된 몬스터 문서의 첫 줄에서 이름만 꺼낸다 (디버그 로그용).
     *
     * @param text 문서 내용
     * @return 몬스터 이름
     */
    private static String firstLine(String text) {
        if (text == null) return "";
        String line = text.strip().lines().findFirst().orElse("");
        return line.replace("[몬스터]", "").strip();
    }

    // 노드/스테이지 계산을 한 곳에서 처리
    /**
     * 다음 노드를 미리 만들어 둔다. 10번째 노드 다음은 다음 스테이지 1번 노드다.
     *
     * @param currentStage   현재 스테이지
     * @param currentNodeId  현재 노드
     * @param conversationId 대화 ID
     */
    private void prefetch(int currentStage, int currentNodeId, String conversationId) {
        if (currentNodeId % 10 == 0) {
            // 10노드면 다음 스테이지 1노드
            asyncPrefetchService.prefetchNextStage(currentStage + 1, 1, conversationId);
        } else {
            // 현재 스테이지 다음 노드
            asyncPrefetchService.prefetchNextStage(currentStage, currentNodeId + 1, conversationId);
        }
    }

    /**
     * Gson을 이용한 JSON 변환 (몬스터 이름만 추출을 위함)
     * @param json 몬스터 정보 json
     * @return 추출된 몬스터 List
     */
    private List<String> parseMonster(String json) {
        try {
            // LLM이 json형식으로 달라하면 구분선을 지어서 줄 확률이 있음 혹시 모르니 제거
            String cleaned = json.replaceAll("```json|```", "").trim();
            JsonObject jsonObject = JsonParser.parseString(cleaned).getAsJsonObject(); // 직렬화
            List<String> name = new ArrayList<>();
            jsonObject.getAsJsonArray("monsters")
                    .forEach(element -> name.add(element.getAsString()));
            return name;
        } catch (Exception e) {
            log.warn("몬스터 이름 파싱 실패 랜덤 몬스터 출현 {}", json);
            return List.of();
        }
    }
}
