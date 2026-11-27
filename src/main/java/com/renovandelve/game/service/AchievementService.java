package com.renovandelve.game.service;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.response.AchievementDTO;
import com.renovandelve.game.engine.Faction;
import com.renovandelve.game.engine.Pact;
import com.renovandelve.collection.repository.CollectionArmorRepository;
import com.renovandelve.collection.repository.CollectionConsumableRepository;
import com.renovandelve.collection.repository.CollectionHelmetRepository;
import com.renovandelve.collection.repository.CollectionRelicRepository;
import com.renovandelve.collection.repository.CollectionSkillRepository;
import com.renovandelve.collection.repository.CollectionWeaponRepository;
import com.renovandelve.game.repository.GameStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 업적: 한 판이 끝나도 남는 장기 목표.
 *
 * 별도 테이블에 달성 기록을 쌓지 않고, 이미 저장돼 있는 모험 기록(game_state)과 도감에서 매번 계산한다.
 * - 저장 시점의 누락·중복을 걱정할 필요가 없고
 * - 업적을 추가하면 과거 기록에도 소급 적용된다
 * 회원당 게임 수가 수천 건이 되면 집계 쿼리나 달성 테이블로 옮겨야 한다.
 */
@Service
@RequiredArgsConstructor
public class AchievementService {
    private final GameStateRepository gameStateRepository;
    private final CollectionWeaponRepository collectionWeaponRepository;
    private final CollectionArmorRepository collectionArmorRepository;
    private final CollectionHelmetRepository collectionHelmetRepository;
    private final CollectionSkillRepository collectionSkillRepository;
    private final CollectionRelicRepository collectionRelicRepository;
    private final CollectionConsumableRepository collectionConsumableRepository;

    /**
     * 업적 12종의 달성 여부를 계산한다. 별도 테이블 없이 모험 기록과 도감에서 계산한다.
     *
     * @param member 회원
     * @return 업적 목록
     */
    @Transactional(readOnly = true)
    public List<AchievementDTO> list(MemberEntity member) {
        List<GameStateEntity> games = gameStateRepository.findByMember_MemberIdOrderByCreatedAtDesc(member.getMemberId());
        long collected = collectionWeaponRepository.countByMember(member)
                + collectionArmorRepository.countByMember(member)
                + collectionHelmetRepository.countByMember(member)
                + collectionSkillRepository.countByMember(member)
                + collectionRelicRepository.countByMember(member)
                + collectionConsumableRepository.countByMember(member);

        Predicate<GameStateEntity> cleared = game -> "CLEAR".equals(game.getResult());

        List<AchievementDTO> result = new ArrayList<>();
        result.add(achievement("FIRST_STEP", "첫 발걸음", "왕궁에 처음 발을 들였다",
                !games.isEmpty()));
        result.add(achievement("SECOND_GATE", "두 번째 문", "첫 보스를 넘어 스테이지 2에 도달했다",
                games.stream().anyMatch(game -> game.getCurrentStage() >= 2)));
        result.add(achievement("UNDERGROUND", "빛이 닿지 않는 곳", "지하(스테이지 4)에 도달했다",
                games.stream().anyMatch(game -> game.getCurrentStage() >= 4)));
        result.add(achievement("CLEAR", "문 앞에 선 자", "마계의 문에 도달해 모험을 끝냈다",
                games.stream().anyMatch(cleared)));
        result.add(achievement("END_HEAVEN", "하늘에 진 빚", "천상에 기운 채 결말을 맞았다",
                games.stream().anyMatch(cleared.and(game -> game.alignment().dominant() == Faction.HEAVEN))));
        result.add(achievement("END_ABYSS", "문의 새 주인", "마계에 기운 채 결말을 맞았다",
                games.stream().anyMatch(cleared.and(game -> game.alignment().dominant() == Faction.ABYSS))));
        result.add(achievement("END_HUMAN", "누구의 가호도 없이", "인간의 방식만으로 결말을 맞았다",
                games.stream().anyMatch(cleared.and(game -> game.alignment().dominant() == Faction.HUMAN))));
        result.add(achievement("END_NONE", "주인 없는 새벽", "어느 쪽에도 기울지 않은 채 결말을 맞았다",
                games.stream().anyMatch(cleared.and(game -> game.alignment().dominant() == null))));
        result.add(achievement("HELL_CLEAR", "지옥을 걸어서", "HELL 난이도를 클리어했다",
                games.stream().anyMatch(cleared.and(game -> game.getDifficulty() == Difficulty.HELL))));
        result.add(achievement("OATHBOUND", "스스로 채운 족쇄", "서약 3개 이상을 건 채 클리어했다",
                games.stream().anyMatch(cleared.and(game -> Pact.parse(game.getPacts()).size() >= 3))));
        result.add(achievement("SCORE_5000", "이름을 남긴 자", "한 판에서 5000점 이상을 기록했다",
                games.stream().anyMatch(game -> game.getScore() >= 5000)));
        result.add(achievement("COLLECTOR", "수집가", "도감에 20종 이상을 등록했다",
                collected >= 20));
        return result;
    }

    /**
     * 업적 한 항목을 만든다.
     *
     * @param code        업적 코드
     * @param title       이름
     * @param description 조건 설명
     * @param achieved    달성 여부
     * @return 업적
     */
    private AchievementDTO achievement(String code, String title, String description, boolean achieved) {
        return new AchievementDTO(code, title, description, achieved);
    }
}
