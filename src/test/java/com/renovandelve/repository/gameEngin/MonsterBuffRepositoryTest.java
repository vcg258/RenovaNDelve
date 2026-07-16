package com.renovandelve.repository.gameEngin;

import com.renovandelve.domain.buffentity.MonsterBuffEntity;
import com.renovandelve.repository.MonsterBuffRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

@SpringBootTest
@Log4j2
class MonsterBuffRepositoryTest {
    @Autowired
    private MonsterBuffRepository monsterBuffRepository;

    @Test
    void testFindByKeyword() {
        String keyword = "재생";

        Optional<MonsterBuffEntity> monsterBuffEntity = monsterBuffRepository.findByKeyword(keyword);
        log.info(monsterBuffEntity);

    }
}