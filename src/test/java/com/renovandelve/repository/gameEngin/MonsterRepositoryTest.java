package com.renovandelve.repository.gameEngin;

import com.renovandelve.domain.MonsterEntity;
import com.renovandelve.repository.MonsterRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Log4j2
class MonsterRepositoryTest {
    @Autowired
    private MonsterRepository monsterRepository;

    @Test
    void testMonsterRepository() {
        short id = 0;
        List<MonsterEntity> monsterEntity = monsterRepository.findByMonsterType(id);
        log.info(monsterEntity);
    }
}