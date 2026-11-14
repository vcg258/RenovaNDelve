package com.renovandelve.repository.gameEngin;

import com.renovandelve.domain.GameConsumableInventoryEntity;
import com.renovandelve.repository.GameConsumableInventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
@Slf4j
class GameConsumableInventoryRepositoryTest {
    @Autowired
    private GameConsumableInventoryRepository gameConsumableInventoryRepository;

    @Test
    void testFindByGameState_GameStateId() {
        Long id = 1L;
        List<GameConsumableInventoryEntity> gameConsumableInventoryEntities = gameConsumableInventoryRepository.findByGameState_GameStateId(id);
        log.info("{}", gameConsumableInventoryEntities);
    }
}