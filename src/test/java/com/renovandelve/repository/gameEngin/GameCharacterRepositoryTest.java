package com.renovandelve.repository.gameEngin;

import com.renovandelve.domain.GameCharacterEntity;
import com.renovandelve.repository.GameCharacterRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

@SpringBootTest
@Log4j2
class GameCharacterRepositoryTest {
    @Autowired
    private GameCharacterRepository gameCharacterRepository;

    @Test
    void testFindByGameState_GameStateId() {
        Long id = 1L;
        Optional<GameCharacterEntity> gameCharacterEntities =  gameCharacterRepository.findByGameState_GameStateId(id);
        log.info(gameCharacterEntities);
    }
}