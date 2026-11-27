package com.renovandelve.game.domain;

import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.engine.DemonContract;
import com.renovandelve.game.engine.KingdomState;
import com.renovandelve.game.engine.Pact;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.ranking.domain.RankingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 엔티티의 생성·변경 규칙 (정적 팩토리와 도메인 메서드). DB 없이 돈다.
 */
class GameStateEntityFactoryTest {

    private final WeaponEntity sword = WeaponEntity.builder().weaponId(1L).name("철검").atkBonus(5).build();
    private final ArmorEntity mail = ArmorEntity.builder().armorId(1L).name("사슬 갑옷").defBonus(3).build();
    private final HelmetEntity cap = HelmetEntity.builder().helmetId(1L).name("가죽 모자").hpBonus(20).build();
    private final SkillEntity basic = SkillEntity.builder().skillId(1L).name("기본 공격").basic(true).build();
    private final RelicEntity relic = RelicEntity.builder().relicId(1L).name("낡은 부적").build();

    private GameStateEntity start(Set<Pact> pacts) {
        return GameStateEntity.start(MemberEntity.social("s-1"), Difficulty.HARD, 40, pacts, KingdomState.BALANCE,
                sword, mail, cap, List.of(basic), List.of(relic));
    }

    @Test
    @DisplayName("원정 시작: 장비·캐릭터·스킬·유물이 게임에 붙고, 캐릭터 능력치는 기본값 + 장비 보너스")
    void startWiresAggregate() {
        GameStateEntity game = start(Set.of(Pact.FURY));

        assertThat(game.getGold()).isEqualTo(GameStateEntity.STARTING_GOLD);
        assertThat(game.getCurrentStage()).isEqualTo(1);
        assertThat(game.getCurrentNodeId()).isZero();
        assertThat(game.getStartingPoints()).isEqualTo(40);
        assertThat(game.getPacts()).isEqualTo("FURY");
        assertThat(game.getKingdom()).isEqualTo("BALANCE");
        assertThat(game.getGameEquipment().getGameState()).isSameAs(game);
        assertThat(game.getGameEquipment().getWeapon()).isSameAs(sword);

        GameCharacterEntity character = game.character();
        assertThat(character.getGameState()).isSameAs(game);
        assertThat(character.getMaxHp()).isEqualTo(GameCharacterEntity.BASE_MAX_HP + 20);
        assertThat(character.getCurrentHp()).isEqualTo(character.getMaxHp());
        assertThat(character.getAttack()).isEqualTo(GameCharacterEntity.BASE_ATTACK + 5);
        assertThat(character.getDefense()).isEqualTo(GameCharacterEntity.BASE_DEFENSE + 3);
        assertThat(game.getGameSkillSlot()).extracting(GameSkillSlotEntity::getSkill).containsExactly(basic);
        assertThat(game.getGameRelicInventory()).extracting(GameRelicInventoryEntity::getRelic).containsExactly(relic);
    }

    @Test
    @DisplayName("서약이 없으면 pacts는 NULL로 저장한다")
    void startWithoutPacts() {
        assertThat(start(Set.of()).getPacts()).isNull();
    }

    @Test
    @DisplayName("장비 교체: 예전 보너스를 빼고 새 보너스를 더한다")
    void equipRecalculatesStats() {
        GameStateEntity game = start(Set.of());
        int attack = game.character().getAttack();
        int defense = game.character().getDefense();
        int maxHp = game.character().getMaxHp();

        game.equipWeapon(WeaponEntity.builder().weaponId(2L).name("대검").atkBonus(12).build());
        game.equipArmor(ArmorEntity.builder().armorId(2L).name("판금").defBonus(1).build());
        game.equipHelmet(HelmetEntity.builder().helmetId(2L).name("투구").hpBonus(50).build());

        assertThat(game.character().getAttack()).isEqualTo(attack + 7);
        assertThat(game.character().getDefense()).isEqualTo(defense - 2);
        assertThat(game.character().getMaxHp()).isEqualTo(maxHp + 30);
        assertThat(game.getGameEquipment().getWeapon().getName()).isEqualTo("대검");
    }

    @Test
    @DisplayName("계약 파기: 천상 성향이 모자라면 거부, 채우면 비용을 내고 계약이 사라진다")
    void breakContract() {
        GameStateEntity weak = GameStateEntity.builder().gold(500).heavenAffinity(0)
                .contract(DemonContract.of(DemonContract.Boon.GOLD, DemonContract.Clause.BLOOD_TITHE).serialize()).build();
        assertThat(weak.canBreakContract()).isFalse();
        assertThatThrownBy(weak::breakContract).isInstanceOf(BusinessException.class);

        GameStateEntity devout = GameStateEntity.builder().gold(500).heavenAffinity(DemonContract.BREAK_HEAVEN_AFFINITY)
                .contract(DemonContract.of(DemonContract.Boon.GOLD, DemonContract.Clause.BLOOD_TITHE).serialize()).build();
        int cost = devout.contractBreakCost();
        assertThat(devout.canBreakContract()).isTrue();

        devout.breakContract();

        assertThat(devout.demonContract()).isNull();
        assertThat(devout.getGold()).isEqualTo(500 - cost);
        assertThat(devout.canBreakContract()).isFalse();
    }

    @Test
    @DisplayName("랭킹: 첫 기록은 게임에서 읽고, 더 높은 점수일 때만 같은 엔티티를 고친다")
    void rankingRenewsInPlace() {
        GameStateEntity first = GameStateEntity.builder().member(MemberEntity.social("s-1"))
                .difficulty(Difficulty.HARD).score(300).build();
        RankingEntity ranking = RankingEntity.first(first, 1200);
        assertThat(ranking.getBestScore()).isEqualTo(300);
        assertThat(ranking.getDifficulty()).isEqualTo(Difficulty.HARD);

        GameStateEntity worse = GameStateEntity.builder().difficulty(Difficulty.HARD).score(200).build();
        assertThat(ranking.renewIfBetter(worse, 900)).isFalse();
        assertThat(ranking.getClearTime()).isEqualTo(1200);

        GameStateEntity better = GameStateEntity.builder().difficulty(Difficulty.HARD).score(450).build();
        assertThat(ranking.renewIfBetter(better, null)).isTrue();
        assertThat(ranking.getBestScore()).isEqualTo(450);
        assertThat(ranking.getClearTime()).isNull();
        assertThat(ranking.getGameState()).isSameAs(better);
        assertThat(ranking.getAchievedAt()).isNotNull();
    }

    @Test
    @DisplayName("회원 가입: 이메일은 소문자, 이름은 앞뒤 공백 없이, 동의 일시와 버전을 남긴다")
    void registerNormalizes() {
        MemberEntity member = MemberEntity.register("tester", "hash", "닉", "  홍길동 ", "Tester@Example.COM", "2026-11-14");

        assertThat(member.getEmail()).isEqualTo("tester@example.com");
        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getTermsAgreedAt()).isNotNull();
        assertThat(member.getPrivacyAgreedAt()).isEqualTo(member.getTermsAgreedAt());
        assertThat(member.getPolicyVersion()).isEqualTo("2026-11-14");
        assertThat(member.getRole().name()).isEqualTo("USER");
        assertThat(MemberEntity.admin("admin", "hash").getRole().name()).isEqualTo("ADMIN");
    }
}
