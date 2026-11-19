package com.renovandelve.member.service;

import com.renovandelve.game.mapper.GameMapper;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.ranking.domain.RankingEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.response.GameHistoryDTO;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.game.domain.WraithEntity;
import com.renovandelve.game.repository.WraithRepository;
import com.renovandelve.game.repository.ChronicleChapterRepository;
import com.renovandelve.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 개인정보 생명주기 처리 (열람 / 삭제 / 보관 기간 경과 파기).
 *
 * 이 서비스가 다루는 개인정보성 데이터:
 * - 회원 테이블의 식별 정보 (아이디, 닉네임, 이름, 이메일, 비밀번호 해시)
 * - AI 대화 기록 (SPRING_AI_CHAT_MEMORY): 가이드 챗봇에 입력한 자유 텍스트가 그대로 남는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalDataService {
    /** 현재 시행 중인 약관·개인정보 처리방침 버전 (프론트 legal 문서의 시행일과 맞춘다) */
    public static final String POLICY_VERSION = "2026-11-14";

    private static final int HISTORY_LIMIT = 30;

    private final MemberRepository memberRepository;
    private final GameStateRepository gameStateRepository;
    private final ChatMemory chatMemory;
    private final JdbcTemplate jdbcTemplate;
    private final WraithRepository wraithRepository;
    private final ChronicleChapterRepository chronicleChapterRepository;
    private final GameMapper gameMapper;

    @Value("${renovaNDelve.privacy.chat-retention-days:30}")
    private int chatRetentionDays;

    /**
     * 가이드 챗봇 대화 기록 ID.
     *
     * @param loginId 로그인 아이디
     * @return guide-{아이디}
     */
    public static String guideConversationId(String loginId) {
        return "guide-" + loginId;
    }

    /**
     * 게임 서사 대화 기록 ID.
     *
     * @param gameStateId 게임 ID
     * @return game-{게임 ID}
     */
    public static String gameConversationId(Long gameStateId) {
        return "game-" + gameStateId;
    }

    /** 가이드 챗봇 대화 기록 삭제 (정보주체의 삭제 요청) */
    public void eraseGuideConversation(String loginId) {
        chatMemory.clear(guideConversationId(loginId));
    }

    /**
     * 회원의 모든 AI 대화 기록 삭제. 탈퇴 시 호출한다.
     * loginId는 탈퇴 처리로 익명화되기 "전"의 값을 넘겨야 한다.
     */
    public void eraseAllConversations(Long memberId, String loginId) {
        chatMemory.clear(guideConversationId(loginId));
        for (GameStateEntity game : gameStateRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)) {
            chatMemory.clear(gameConversationId(game.getGameStateId()));
        }
        // 망령에는 대화 기록에서 옮겨 둔 선택 문장이 들어 있다
        wraithRepository.deleteAllByMemberId(memberId);
        // 연대기 본문은 그 원정의 선택을 바탕으로 AI가 쓴 글이다
        chronicleChapterRepository.deleteAllByMemberId(memberId);
    }

    /** 최근 모험 기록 */
    @Transactional(readOnly = true)
    public List<GameHistoryDTO> getHistory(MemberEntity member) {
        return gameStateRepository.findByMember_MemberIdOrderByCreatedAtDesc(member.getMemberId()).stream()
                .limit(HISTORY_LIMIT)
                .map(GameHistoryDTO::from)
                .toList();
    }

    /**
     * 내 정보 내려받기 (개인정보 열람권).
     * 서비스가 이 회원에 대해 보관 중인 정보를 한 번에 확인할 수 있게 한다.
     * 비밀번호 해시는 본인에게도 내려주지 않는다.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> export(MemberEntity member) {
        MemberEntity managed = memberRepository.findById(member.getMemberId())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("loginId", managed.getLoginId());
        account.put("nickname", managed.getNickname());
        account.put("name", managed.getName());
        account.put("email", managed.getEmail());
        account.put("role", managed.getRole().name());
        account.put("status", managed.getStatus().name());
        account.put("createdAt", managed.getCreatedAt());
        account.put("lastLoginAt", managed.getLastLoginAt());
        account.put("socialLoginLinked", managed.getSocialId() != null);

        Map<String, Object> consent = new LinkedHashMap<>();
        consent.put("termsAgreedAt", managed.getTermsAgreedAt());
        consent.put("privacyAgreedAt", managed.getPrivacyAgreedAt());
        consent.put("policyVersion", managed.getPolicyVersion());

        List<Map<String, Object>> rankings = managed.getRanking().stream()
                .map(this::toRankingMap)
                .toList();

        List<GameHistoryDTO> games = gameStateRepository
                .findByMember_MemberIdOrderByCreatedAtDesc(managed.getMemberId()).stream()
                .map(GameHistoryDTO::from)
                .toList();

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("weapons", managed.getCollectionWeapon().size());
        collection.put("armors", managed.getCollectionArmor().size());
        collection.put("helmets", managed.getCollectionHelmet().size());
        collection.put("skills", managed.getCollectionSkill().size());
        collection.put("relics", managed.getCollectionRelic().size());
        collection.put("consumables", managed.getCollectionConsumable().size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportedAt", LocalDateTime.now());
        result.put("account", account);
        result.put("consent", consent);
        result.put("rankings", rankings);
        result.put("games", games);
        result.put("collectionCounts", collection);
        result.put("wraiths", wraithRepository.findByMemberIdOrderByCreatedAtDesc(managed.getMemberId()).stream()
                .map(this::toWraithMap)
                .toList());
        result.put("chronicle", chronicleChapterRepository.findByMemberIdOrderByChapterNoAsc(managed.getMemberId()).stream()
                .map(gameMapper::toChronicleChapterDTO)
                .toList());
        result.put("notice", "AI 대화 기록은 마지막 이용 후 %d일 뒤 자동 삭제되며, 내 정보 화면에서 즉시 삭제할 수 있습니다."
                .formatted(chatRetentionDays));
        return result;
    }

    /**
     * 보관 기간이 지난 AI 대화 기록 파기 (매일 04:00).
     *
     * Spring AI의 JDBC 대화 저장소는 메시지가 추가될 때마다 해당 대화 전체를 다시 기록하므로
     * timestamp는 "그 대화가 마지막으로 이어진 시각"에 가깝다.
     * -> 기준일 이전 행을 지우면 "마지막 이용 후 N일이 지난 대화"가 정리된다.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpiredConversations() {
        try {
            Timestamp threshold = Timestamp.valueOf(LocalDateTime.now().minusDays(chatRetentionDays));
            int deleted = jdbcTemplate.update(
                    "DELETE FROM spring_ai_chat_memory WHERE \"timestamp\" < ?", threshold);
            log.info("[개인정보 파기] 보관 기간({}일) 경과 AI 대화 기록 {}건 삭제", chatRetentionDays, deleted);
        } catch (Exception e) {
            log.error("[개인정보 파기] AI 대화 기록 정리 실패: {}", e.getMessage());
        }
    }

    /**
     * 망령 기록을 내려받기용 맵으로 옮긴다.
     *
     * @param wraith 망령
     * @return 쓰러진 자리, 레벨, 상대, 기술, 세력, 선택 기록, 상태, 기록 일시
     */
    private Map<String, Object> toWraithMap(WraithEntity wraith) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stage", wraith.getStage());
        map.put("node", wraith.getNode());
        map.put("level", wraith.getLevel());
        map.put("killerName", wraith.getKillerName());
        map.put("skills", wraith.skillNames());
        map.put("faction", wraith.getFaction());
        map.put("memories", wraith.memoryLines());
        map.put("status", wraith.getStatus().name());
        map.put("createdAt", wraith.getCreatedAt());
        return map;
    }

    /**
     * 랭킹 기록을 내려받기용 맵으로 옮긴다.
     *
     * @param ranking 랭킹 기록
     * @return 난이도, 최고 점수, 클리어 시간, 달성 일시
     */
    private Map<String, Object> toRankingMap(RankingEntity ranking) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("difficulty", ranking.getDifficulty());
        map.put("bestScore", ranking.getBestScore());
        map.put("clearTime", ranking.getClearTime());
        map.put("achievedAt", ranking.getAchievedAt());
        return map;
    }
}
