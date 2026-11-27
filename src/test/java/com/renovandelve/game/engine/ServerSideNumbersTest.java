package com.renovandelve.game.engine;

import com.renovandelve.catalog.mapper.CatalogMapperImpl;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.mapper.GameMapper;
import com.renovandelve.game.mapper.GameMapperImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화면에 보이는 수치(강공 예고, 다음 레벨 경험치)를 서버가 계산해 내려주는지 확인한다.
 * 화면은 이 값을 그대로 보여 주기만 한다.
 */
class ServerSideNumbersTest {

    private final GameMapper gameMapper = new GameMapperImpl(new CatalogMapperImpl());

    @Test
    @DisplayName("예고 공격력: 공격은 그대로, 강공은 1.6배 반올림, 방어 태세는 0, 예고가 없으면 공격")
    void expectedAttack() {
        assertThat(MonsterIntent.expectedAttack(MonsterIntent.ATTACK, 25)).isEqualTo(25);
        assertThat(MonsterIntent.expectedAttack(MonsterIntent.HEAVY, 25)).isEqualTo(40);
        assertThat(MonsterIntent.expectedAttack(MonsterIntent.HEAVY, 13)).isEqualTo(21);
        assertThat(MonsterIntent.expectedAttack(MonsterIntent.GUARD, 25)).isZero();
        assertThat(MonsterIntent.expectedAttack(null, 25)).isEqualTo(25);
    }

    @Test
    @DisplayName("전투 몬스터 응답에 예고 공격력이 담긴다")
    void battleMonsterCarriesIntentAtk() {
        SessionMonster monster = new SessionMonster();
        monster.setAtk(30);
        monster.setIntent(MonsterIntent.HEAVY);

        assertThat(gameMapper.toBattleMonsterDTO(monster).intentAtk()).isEqualTo(48);
    }

    @Test
    @DisplayName("다음 레벨 경험치는 레벨 x 100이고, 레벨업 판정과 응답이 같은 값을 쓴다")
    void expToNext() {
        GameCharacterEntity character = GameCharacterEntity.builder().level(3).exp(0).maxHp(100).currentHp(100).build();
        assertThat(character.expToNextLevel()).isEqualTo(300);
        assertThat(gameMapper.toCharacterDTO(character).getExpToNext()).isEqualTo(300);

        character.gainExp(299);
        assertThat(character.getLevel()).isEqualTo(3);
        character.gainExp(1);
        assertThat(character.getLevel()).isEqualTo(4);
        assertThat(character.expToNextLevel()).isEqualTo(400);
    }
}
