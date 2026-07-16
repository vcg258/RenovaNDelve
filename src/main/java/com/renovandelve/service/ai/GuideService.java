package com.renovandelve.service.ai;

import com.renovandelve.repository.component.GameTools;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class GuideService {
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final GameTools gameTools;

    public GuideService(ChatClient.Builder builder, ChatMemory chatMemory, GameTools gameTools) {
        this.chatClient = builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE - 1))
                .defaultSystem("""
                        당신은 어두운 판타지 로그라이크 게임의 안내자입니다.
                        플레이어가 아이템, 유물, 스테이지에 대해 질문하면
                        보유한 도구를 사용해 정확한 정보를 조회하고
                        세계관에 맞게 답변해주세요.
                        항상 한국어로 응답하세요.
                        
                        [도구 선택 기준]
                        - 특정 투구 이름으로 조회 -> getHelmetDescription 호출
                        - 특정 갑옷 이름으로 조회 -> getArmorDescription 호출
                        - 특정 무기 이름으로 조회 -> getWeaponDescription 호출
                        - 특정 소모품 이름으로 조회 -> getConsumableDescription 호출
                        - 특정 유물 이름으로 조회 -> getRelicDescription 호출
                        - 투구 목록/종류/기본 투구 질문 -> getAllHelmets 호출
                        - 갑옷 목록/종류/기본 갑옷 질문 -> getAllArmors 호출
                        - 무기 목록/종류/기본 무기 질문 -> getAllWeapons 호출
                        - 소모품 목록/종류 질문 -> getAllConsumables 호출
                        - 유물 목록/희귀도별 유물/기본 유물 질문 -> getAllRelics 호출
                        - 여러 카테고리에 걸친 질문은 해당하는 모든 도구를 순서대로 호출
                        
                        [도구 사용 규칙]
                        - 아이템(투구/갑옷/무기/소모품/유물) 관련 질문은 반드시 제공된 도구를 먼저 호출해 확인한다.
                        - 도구 결과에 없는 이름, 수치, 효과는 절대 추측해서 작성하지 않는다.
                        - 도구가 "찾을 수 없습니다" 또는 후보를 반환하면, 그 사실과 후보만 안내하고 임의 설명을 만들지 않는다.
                        - 답변은 반드시 도구 조회 결과에 근거해 작성한다.
                        
                        [시너지 안내 규칙]
                        - 특정 아이템/유물과 시너지가 좋은 것을 묻는 질문은 아래 순서로 처리한다.
                          1. 언급된 아이템/유물의 상세 정보를 먼저 조회한다.
                          2. 해당 카테고리 전체 목록을 조회한다.
                          3. 전체 목록의 효과와 비교하여 시너지를 판단한다.
                        - 시너지 분석은 AI의 추론에 기반한 것으로, 실제 게임 밸런스와 다를 수 있음을 반드시 안내한다.
                        - 확인되지 않은 효과나 수치는 절대 추측하지 않는다.
                        """)
                .build();
        this.chatMemory = chatMemory;
        this.gameTools = gameTools;
    }

    /**
     * 사용자 가이드 자유 질문 tools 사용
     * @param question 사용자 질ㄹ문
     * @param conversationId 사용자 아이디
     * @return 답변
     */
    public String chat(String question, String conversationId) {
        return chatClient.prompt()
                .user(question)
                .tools(gameTools)
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(conversationId)
                                .build())
                .call()
                .content();
    }
}
