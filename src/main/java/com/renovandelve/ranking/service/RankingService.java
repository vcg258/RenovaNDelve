package com.renovandelve.ranking.service;

import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.exception.ErrorCode;
import com.renovandelve.ranking.dto.response.RankingViewDTO;
import com.renovandelve.ranking.mapper.RankingMapper;
import com.renovandelve.ranking.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 난이도별 랭킹 조회.
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private final RankingRepository rankingRepository;
    private final RankingMapper rankingMapper;

    /**
     * 난이도별 TOP 10. 순위는 1부터 매긴다.
     *
     * @param difficulty EASY / NORMAL / HARD / HELL (대소문자 무관)
     * @return 랭킹 목록
     * @throws BusinessException 알 수 없는 난이도면 (UNKNOWN_DIFFICULTY)
     */
    @Transactional(readOnly = true)
    public List<RankingViewDTO> top10(String difficulty) {
        Difficulty parsed;
        try {
            parsed = Difficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNKNOWN_DIFFICULTY, difficulty);
        }

        List<RankingViewDTO> result = new ArrayList<>();
        int rank = 1;
        for (var entity : rankingRepository.findTop10ByDifficultyOrderByBestScoreDesc(parsed)) {
            result.add(rankingMapper.toViewDTO(entity, rank++));
        }
        return result;
    }
}
