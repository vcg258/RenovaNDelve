package com.renovandelve.ai.service;

import com.renovandelve.game.domain.WraithEntity;
import com.renovandelve.game.engine.Faction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 전생의 망령이 나타날 때의 장면과 대사.
 *
 * 망령의 능력치·보상은 서버가 정하고, 이 서비스는 연출만 맡는다. 입력은 서버가 저장해 둔 사실
 * (쓰러진 자리, 쓰러뜨린 적, 그 원정의 선택)뿐이라 모델이 없는 사건을 지어낼 여지를 줄인다.
 * 대화 메모리를 쓰지 않는 1회성 호출이고, 실패하면 정해진 문장으로 대신한다.
 */
@Slf4j
@Service
public class WraithVoiceService {
    private final ChatClient chatClient;

    public WraithVoiceService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        당신은 어두운 판타지 로그라이크 게임 'Renova & Delve'의 서술자입니다.
                        세계관: 천상(질서)과 마계(욕망) 사이에 낀 인간 왕국 레노바. 왕의 계약으로 지하에 마계의 문이 열렸고,
                        한 인간이 그 문을 향해 내려갑니다. 이 땅에서 쓰러진 자는 망령이 되어 같은 자리에 남습니다.

                        지금 플레이어 앞에, 지난 원정에서 이 자리에 쓰러진 플레이어 자신의 망령이 나타났습니다.

                        ## 쓰는 법
                        - 3~4문장. 장면 묘사 1~2문장 뒤에, 망령이 하는 말을 큰따옴표로 한두 마디 넣습니다.
                        - 망령의 말은 [지난 원정의 선택] 중 하나를 구체적으로 떠올리게 합니다. 원망, 후회, 조롱 중 어울리는 감정으로.
                        - [사실]에 없는 이름·사건·아이템을 지어내지 않습니다. 수치는 쓰지 않습니다.
                        - 한국어 평서문(~다)으로, 음산하지만 담백하게. 머리말이나 제목 없이 본문만 씁니다.
                        - 플레이어는 "당신"이라고 부릅니다.
                        """)
                .build();
    }

    /**
     * 망령이 나타나는 장면을 쓴다.
     *
     * @param wraith 망령
     * @return 장면과 대사. 실패하면 정해진 문장
     */
    public String appearance(WraithEntity wraith) {
        try {
            String text = chatClient.prompt().user("[사실]\n" + facts(wraith)).call().content();
            if (text != null && !text.isBlank()) return text.trim();
        } catch (Exception e) {
            log.warn("망령 대사 생성 실패 wraithId={} 원인={}", wraith.getWraithId(), e.getMessage());
        }
        return fallback(wraith);
    }

    /** 모델에게 넘길 사실 목록 */
    static String facts(WraithEntity wraith) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 쓰러진 곳: 스테이지 ").append(wraith.getStage()).append('\n');
        sb.append("- 쓰러뜨린 것: ").append(wraith.getKillerName() == null ? "조우에서 내린 선택의 대가" : wraith.getKillerName()).append('\n');
        sb.append("- 그때의 레벨: ").append(wraith.getLevel()).append('\n');
        if (!wraith.skillNames().isEmpty()) {
            sb.append("- 주로 쓰던 기술: ").append(String.join(", ", wraith.skillNames())).append('\n');
        }
        Faction faction = Faction.parse(wraith.getFaction());
        sb.append("- 기울었던 세력: ").append(faction == null ? "어느 쪽에도 기울지 않음" : faction.label()).append('\n');
        List<String> memories = wraith.memoryLines();
        sb.append("[지난 원정의 선택]\n");
        if (memories.isEmpty()) sb.append("- (기록 없음)\n");
        memories.forEach(m -> sb.append("- ").append(m).append('\n'));
        return sb.toString();
    }

    /** AI 없이 쓰는 장면 */
    static String fallback(WraithEntity wraith) {
        String killer = wraith.getKillerName() == null ? "" : " %s에게 무너지던 그 순간의 자세 그대로다.".formatted(wraith.getKillerName());
        return "안개 속에서 낯익은 걸음걸이가 다가온다. 지난 원정에서 이곳에 쓰러진 당신이다." + killer
                + " \"또 여기까지 왔구나. 이번에도 같은 길을 고를 셈인가.\"";
    }
}
