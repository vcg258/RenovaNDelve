package com.renovandelve.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@Slf4j
@SpringBootTest
class MonsterServiceImplTest {
    @Autowired private MonsterService monsterService;

    @Test
    void getMonsterByNameNormal() {
        monsterService.getMonsterByNameNormal(List.of("슬라임", "해골 병사")).forEach(monster -> log.info("{}", monster));
    }

    @Test
    void getRandomMonsterNormal() {
        log.info("{}", monsterService.getRandomMonsterNormal());
    }

    @Test
    void getMonsterByNameBoss() {
        log.info("{}", monsterService.getMonsterByNameBoss(List.of("기사단장", "악마화된 왕")));
    }

    @Test
    void getRandomMonsterBoss() {
        log.info("{}", monsterService.getRandomMonsterBoss());
    }
}