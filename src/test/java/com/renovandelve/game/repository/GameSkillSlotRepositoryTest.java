package com.renovandelve.game.repository;

import com.renovandelve.game.domain.GameSkillSlotEntity;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

@SpringBootTest
@Slf4j
class GameSkillSlotRepositoryTest {
    @Autowired
    private GameSkillSlotRepository gameSkillSlotRepository;

    @Test
    void testFindByGameStateGameStateIdOrderBySlotIndexAsc() {
        long id = 1L;
        List<GameSkillSlotEntity> gameSkillSlotEntities = gameSkillSlotRepository.findByGameState_GameStateIdOrderBySlotIndexAsc(id);
        log.info("{}", gameSkillSlotEntities);
    }

    @Test
    void testFindByGameStateGameStateIdAndSlotIndex() {
        long id = 1L;
        int slot = 0;
        Optional<GameSkillSlotEntity> gameSkillSlotEntity = gameSkillSlotRepository.findByGameState_GameStateIdAndSlotIndex(id, slot);
        log.info("{}", gameSkillSlotEntity);
    }
}