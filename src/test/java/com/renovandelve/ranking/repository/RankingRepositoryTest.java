package com.renovandelve.ranking.repository;

import com.renovandelve.ranking.domain.RankingEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
@Slf4j
class RankingRepositoryTest {
    @Autowired
    private RankingRepository rankingRepository;

    @Test
    void testFindTop10ByDifficultyOrderByBestScoreDesc() {
        Difficulty difficulty = Difficulty.HARD;
        List<RankingEntity> rankingEntities = rankingRepository.findTop10ByDifficultyOrderByBestScoreDesc(difficulty);
        log.info("{}", rankingEntities);
    }
}