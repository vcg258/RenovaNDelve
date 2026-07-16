package com.renovandelve.repository.gameEngin;

import com.renovandelve.domain.MonsterBaseStatusEntity;
import com.renovandelve.repository.MonsterBaseStatusRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

@SpringBootTest
@Log4j2
class MonsterBaseStatusRepositoryTest {
    @Autowired
    private MonsterBaseStatusRepository monsterBaseStatusRepository;

    @Test
    void testFindByStageAndDifficulty() {
        short stage = 1;
        short difficulty = 1;
        Optional<MonsterBaseStatusEntity> monsterBaseStatusEntities = monsterBaseStatusRepository.findByStageAndDifficulty(stage, difficulty);
        log.info(monsterBaseStatusEntities);
    }
}