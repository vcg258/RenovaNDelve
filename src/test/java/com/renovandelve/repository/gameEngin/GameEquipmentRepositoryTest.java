package com.renovandelve.repository.gameEngin;

import com.renovandelve.domain.GameEquipmentEntity;
import com.renovandelve.repository.GameEquipmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
@Slf4j
class GameEquipmentRepositoryTest {
    @Autowired
    private GameEquipmentRepository gameEquipmentRepository;

    @Test
    void testFindByGameState_GameStateId() {
        long id = 1;
        List<GameEquipmentEntity> gameEquipmentEntities = gameEquipmentRepository.findByGameState_GameStateId(id);
        log.info("{}", gameEquipmentEntities);
    }
}