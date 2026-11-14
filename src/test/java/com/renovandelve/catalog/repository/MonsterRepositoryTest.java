package com.renovandelve.catalog.repository;

import com.renovandelve.catalog.domain.MonsterEntity;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class MonsterRepositoryTest {
    @Autowired
    private MonsterRepository monsterRepository;

    @Test
    void testMonsterRepository() {
        short id = 0;
        List<MonsterEntity> monsterEntity = monsterRepository.findByMonsterType(id);
        log.info("{}", monsterEntity);
    }
}