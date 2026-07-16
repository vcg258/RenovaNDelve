package com.renovandelve.repository.gameEngin;

import com.renovandelve.domain.RankingEntity;
import com.renovandelve.domain.gamestateentity.Difficulty;
import com.renovandelve.repository.RankingRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
@Log4j2
class RankingRepositoryTest {
    @Autowired
    private RankingRepository rankingRepository;

    @Test
    void testFindTop10ByDifficultyOrderByBestScoreDesc() {
        Difficulty difficulty = Difficulty.HARD;
        List<RankingEntity> rankingEntities = rankingRepository.findTop10ByDifficultyOrderByBestScoreDesc(difficulty);
        log.info(rankingEntities);
    }
}