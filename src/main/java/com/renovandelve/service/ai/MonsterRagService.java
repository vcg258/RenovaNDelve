package com.renovandelve.service.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.renovandelve.dto.MonsterDTO;
import com.renovandelve.dto.response.AsyncStageResponseDTO;
import com.renovandelve.dto.response.Encounter;
import com.renovandelve.service.MonsterService;
import com.renovandelve.service.redis.StageCacheService;
import lombok.extern.log4j.Log4j2;
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
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
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
                        당신은 어두운 판타지 로그라이크 게임의 나레이터입니다.
                        
                        ## 세계관
                        음모로 멸망한 왕국의 황폐화된 왕궁. 식물은 기괴하게 변형되어 생명체를 공격하고, 사용인들은 광기에 물들어 좀비가 되었습니다.
                        진실: 늙은 왕이 힘과 영생을 위해 악마와 계약. 왕국 구성원을 도구와 제물로 사용했고, 지하에 마계 포탈이 열려 몬스터가 쏟아지고 마계의 공기가 생명체를 변형시킵니다. 강한 정신력을 가진 자만 저항 가능.
                        
                        ## 던전 구조
                        - 지상 1층: 무너진 정문, 왕실 정원, 왕실 묘지 등. 부랑자, 광기 사용인, 슬라임·고블린 등 괴물형 몬스터 출현.
                        - 왕실 기사단 구역: 연무장, 병기고, 알현실 등. 광기에 물든 기사형 몬스터. 보스는 일부 이성 유지.
                        - 지상 2층(궁정 마법사 구역): 연금공방, 금서 보관소, 마법사의 탑 등. 마법과 상태이상을 쓰는 마법사형 몬스터. 보스 처치 시 전말 단서 획득 후 지하로 이동.
                        - 지하 1층: 고문실, 하수구, 지하 묘지 등. 깊어질수록 괴물 출현 증가.
                        - 지하 2층: 인체실험실, 피의 제단, 식인 식물 정원 등. 인간 제물·피 사용 하이리스크 하이리턴 아이템 등장. 그로테스크한 공간.
                        - 지하 3층: 의식실, 악마의 왕좌 등. 필드 자체 상태이상 부여. 최종보스 악마화된 왕 등장. 포탈 봉인 시 엔딩.
                        
                        ## 나레이션 규칙
                        - 항상 어둡고 음산한 분위기 유지
                        - 광기에 완전히 물든 몬스터와 저항하는 몬스터를 구분하여 묘사
                        - 지하로 깊어질수록 공포와 그로테스크함 증가
                        - 항상 한국어로 응답
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
    private List<MonsterDTO> battle(String storyText, double minScore, double referenceScore, boolean isBoss) {
        log.info("생성된 스토리 : {}", storyText);
        log.info("지정 최소 임계 점수 : {}", minScore);
        log.info("지정 최고 임계 점수 : {}", referenceScore);

        // LLM이 만들어준 스토리를 키워드로 추출
        String keyword = extractKeyword(storyText);
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
                    
                    위 배경을 바탕으로 아래 JSON 형식으로만 응답하세요.
                    다른 설명은 절대 포함하지 마세요.
                    
                    {
                      "text": "플레이어가 마주치는 상황을 2문장 이내로 묘사",
                      "flee": "이 상황에서 도망가야 하는 이유를 한 문장으로",
                      "negotiate": "이 상황에서 협상해야 하는 이유를 한 문장으로"
                    }
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
            String flee = jsonObject.get("flee").getAsString();
            String negotiate = jsonObject.get("negotiate").getAsString();
            return new Encounter(text, List.of(flee, negotiate)); // 보상 / 패널티는 GameEngine이 결정
        } catch (Exception e) {
            log.warn("인카운터 파싱 실패");
            return new Encounter(encounterText, List.of("도망", "협상"));
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

        return chatClient.prompt()
                .user("""
                    키워드: %s
                    
                    위 키워드와 어울리는 %s를 선택하고
                    반드시 아래 JSON 형식으로만 응답하세요.
                    다른 설명은 절대 포함하지 마세요.
                    
                    예시: monsters 배열에 몬스터명1, 몬스터명2 형태로 담아주세요.
                    """.formatted(keyword, isBoss ? "보스 몬스터 1개" : "몬스터 1~3개"))
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
        
                            현재 스테이지에 맞는 장소를 스스로 선택하고
                            이전에 방문한 장소와 겹치지 않는 새로운 장소를 선택하고
                            그에 맞는 스토리를 생성해주세요.
                        """.formatted(currentStage, currentNodeId))
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
        String storyText = generateStory(stage, node, conversationId);

        if (stage == 0 && node == 0) {
            throw new IllegalStateException("스테이지가 0이거나 노드가 0은 오류, 잘못된 접근");
        }
        // 10노드 마다 보스 노드
        boolean isBoss = node % 10 == 0;
        if (isBoss || Math.random() > 0.3) {
            log.info(isBoss ? "보스 노드 진입" : "전투 노드 진입");
            return AsyncStageResponseDTO.battle(storyText, battle(storyText, 0.4, 0.5, isBoss));
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
        return chatClient.prompt()
                .user("지금까지 한 모험 3줄로 요약해주세요.")
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(conversationId).build()
                )
                .call()
                .content();
    }

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
        return maxScore;
    }

    // 노드/스테이지 계산을 한 곳에서 처리
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
