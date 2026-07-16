package com.renovandelve.repository.gameEngin;

import com.renovandelve.repository.GameStateRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Log4j2
class GameStateRepositoryTest {
    @Autowired
    private GameStateRepository gameStateRepository;

    @Test
    void testFindByMember_MemberIdAndIsActiveTrue() {
        log.info(gameStateRepository.findByMember_MemberIdAndIsActiveTrue(1L));
    }

    @Test
    void testFindTopByMember_MemberIdOrderByCreatedAtDesc() {
        log.info(gameStateRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(2L));
    }
}