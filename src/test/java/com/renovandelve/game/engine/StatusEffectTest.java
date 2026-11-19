package com.renovandelve.game.engine;

import com.renovandelve.game.service.BattleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태이상 번호·버프 키·예고 값이 한곳(StatusEffect, MonsterIntent)에서 나오는지 확인한다.
 */
class StatusEffectTest {

    @Test
    @DisplayName("번호로 상태이상을 찾고, 0과 모르는 번호는 null")
    void ofCode() {
        assertThat(StatusEffect.ofCode(StatusEffect.Code.BLEED)).isEqualTo(StatusEffect.BLEED);
        assertThat(StatusEffect.ofCode(StatusEffect.Code.STUN)).isEqualTo(StatusEffect.STUN);
        assertThat(StatusEffect.ofCode(StatusEffect.Code.NONE)).isNull();
        assertThat(StatusEffect.ofCode((short) 9)).isNull();
    }

    @Test
    @DisplayName("전투 규칙과 스킬 조합이 같은 번호·키·예고 값을 쓴다")
    void sameValuesEverywhere() {
        assertThat(SkillSynergies.BURN).isEqualTo(StatusEffect.BURN.code());
        assertThat(BattleServiceImpl.BUFF_POISON).isEqualTo("POISON");
        assertThat(BattleServiceImpl.INTENT_HEAVY).isEqualTo(MonsterIntent.HEAVY);
        assertThat(StatusEffect.labelOf("BLEED")).isEqualTo("출혈");
        assertThat(StatusEffect.labelOf("ENRAGED")).isEqualTo("ENRAGED");
    }

    @Test
    @DisplayName("전투 키워드 설명은 규칙 상수의 수치를 쓴다 (화상 소멸 30%, 격노 +30%)")
    void keywordsFromRules() {
        var keywords = new BattleServiceImpl().keywords();
        assertThat(keywords).extracting(k -> k.key()).containsExactly("BLEED", "BURN", "POISON", "STUN", "ENRAGED");
        assertThat(keywords.get(1).description()).contains("30% 확률");
        assertThat(keywords.get(4).description()).contains("30% 올랐다");
    }
}
