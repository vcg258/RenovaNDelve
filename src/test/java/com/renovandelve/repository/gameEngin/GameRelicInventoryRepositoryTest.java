package com.renovandelve.repository.gameEngin;

import com.renovandelve.domain.GameRelicInventoryEntity;
import com.renovandelve.repository.GameRelicInventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
@Slf4j
class GameRelicInventoryRepositoryTest {
    @Autowired
    private GameRelicInventoryRepository gameRelicInventoryRepository;

    @Test
    void testFindByGameStateGameStateId() {
        Long id = 2L;
        List<GameRelicInventoryEntity> gameRelicInventoryEntities = gameRelicInventoryRepository.findByGameState_GameStateId(id);
        log.info("{}", gameRelicInventoryEntities);
    }
}