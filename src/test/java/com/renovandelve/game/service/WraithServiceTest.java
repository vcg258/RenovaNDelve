package com.renovandelve.game.service;

import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameRelicInventoryEntity;
import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.game.domain.WraithEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.repository.WraithRepository;
import com.renovandelve.member.domain.MemberEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 전생의 망령 규칙 테스트. 저장소는 목이라 DB 없이 돈다.
 */
@ExtendWith(MockitoExtension.class)
class WraithServiceTest {

    @Mock private WraithRepository wraithRepository;
    @InjectMocks private WraithService wraithService;

    private WraithEntity wraith(int stage, int node, long sourceGame, List<Long> relics) {
        return WraithEntity.builder().memberId(1L).sourceGameStateId(sourceGame).stage(stage).node(node).level(3)
                .skills(List.of("화염구")).relicIds(relics).memories(List.of("a")).build();
    }

    private SkillEntity skill(String name, int cost, String coefficient) {
        return SkillEntity.builder().name(name).cost(cost).coefficient(new BigDecimal(coefficient)).build();
    }

    @Test
    @DisplayName("쓰러진 노드는 2~9로 맞춘다 (첫 노드와 보스 노드는 피한다)")
    void appearNodeAvoidsFirstAndBossNodes() {
        assertThat(WraithEntity.appearNode(1)).isEqualTo(2);
        assertThat(WraithEntity.appearNode(6)).isEqualTo(6);
        assertThat(WraithEntity.appearNode(10)).isEqualTo(9);
    }

    @Test
    @DisplayName("쓰러지면 새 망령이 남고, 기다리던 망령은 흩어진다")
    void deathReplacesTheWaitingWraith() {
        MemberEntity member = mock(MemberEntity.class);
        when(member.getMemberId()).thenReturn(1L);
        WraithEntity old = wraith(1, 3, 2L, List.of());
        when(wraithRepository.findByMemberIdAndStatus(1L, WraithEntity.Status.ACTIVE)).thenReturn(List.of(old));

        GameStateEntity gameState = GameStateEntity.builder()
                .gameStateId(5L).currentStage(3).currentNodeId(10L).member(member).abyssAffinity(4)
                .gameCharacter(new ArrayList<>(List.of(GameCharacterEntity.builder().level(6).build())))
                .gameSkillSlot(new ArrayList<>(List.of(
                        GameSkillSlotEntity.builder().skill(skill("기본 공격", 0, "1.0")).build(),
                        GameSkillSlotEntity.builder().skill(skill("약한 기술", 10, "1.2")).build(),
                        GameSkillSlotEntity.builder().skill(skill("강한 기술", 10, "2.5")).build())))
                .gameRelicInventory(new ArrayList<>(List.of(
                        GameRelicInventoryEntity.builder().relic(RelicEntity.builder().relicId(8L).build()).build())))
                .build();

        wraithService.recordDeath(gameState, "뼈의 군주", List.of("마계의 손을 잡았다"));

        assertThat(old.getStatus()).isEqualTo(WraithEntity.Status.FADED);
        ArgumentCaptor<WraithEntity> saved = ArgumentCaptor.forClass(WraithEntity.class);
        verify(wraithRepository).save(saved.capture());
        WraithEntity created = saved.getValue();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getStage()).isEqualTo((short) 3);
        assertThat(created.getNode()).isEqualTo((short) 9); // 보스 노드에서 쓰러졌다
        assertThat(created.getLevel()).isEqualTo(6);
        assertThat(created.skillNames()).containsExactly("강한 기술", "약한 기술"); // 기본 공격 제외, 계수 순
        assertThat(created.relicIdList()).containsExactly(8L);
        assertThat(created.getFaction()).isEqualTo("ABYSS");
        assertThat(created.memoryLines()).containsExactly("마계의 손을 잡았다");
    }

    @Test
    @DisplayName("기본 공격만 있었다면 기본 공격이라도 기술로 남는다")
    void basicAttackIsKeptWhenItIsAllYouHad() {
        List<String> names = WraithService.mainSkills(List.of(
                GameSkillSlotEntity.builder().skill(skill("기본 공격", 0, "1.0")).build()));
        assertThat(names).containsExactly("기본 공격");
    }

    @Test
    @DisplayName("망령은 그 자리에서만, 그리고 자기를 남긴 원정이 아닐 때만 나타난다")
    void wraithWaitsOnlyAtItsPlace() {
        when(wraithRepository.findFirstByMemberIdAndStatusOrderByCreatedAtDesc(1L, WraithEntity.Status.ACTIVE))
                .thenReturn(Optional.of(wraith(2, 5, 3L, List.of())));

        assertThat(wraithService.waitingAt(1L, 9L, 2, 5)).isPresent();
        assertThat(wraithService.waitingAt(1L, 9L, 2, 6)).isEmpty();
        assertThat(wraithService.waitingAt(1L, 9L, 3, 5)).isEmpty();
        assertThat(wraithService.waitingAt(1L, 3L, 2, 5)).isEmpty(); // 같은 원정
    }

    @Test
    @DisplayName("세 번 달아나면 흩어진다")
    void threeEscapesFadeTheWraith() {
        WraithEntity wraith = wraith(1, 3, 2L, List.of());
        when(wraithRepository.findById(1L)).thenReturn(Optional.of(wraith));

        assertThat(wraithService.escape(1L)).isFalse();
        assertThat(wraithService.escape(1L)).isFalse();
        assertThat(wraithService.escape(1L)).isTrue();
        assertThat(wraith.getStatus()).isEqualTo(WraithEntity.Status.FADED);
        assertThat(wraithService.escape(1L)).isFalse(); // 이미 흩어졌다
    }

    @Test
    @DisplayName("처치하면 지금 갖지 않은 유물만 돌려준다")
    void slayingReturnsAnUnownedRelic() {
        WraithEntity wraith = wraith(1, 3, 2L, List.of(4L, 5L));
        when(wraithRepository.findById(1L)).thenReturn(Optional.of(wraith));

        assertThat(wraithService.slay(1L, List.of(4L))).contains(5L);
        assertThat(wraith.getStatus()).isEqualTo(WraithEntity.Status.SLAIN);
        assertThat(wraithService.slay(1L, List.of())).isEmpty(); // 두 번 처치할 수는 없다
    }

    @Test
    @DisplayName("이미 가진 유물뿐이면 돌려줄 유물이 없다")
    void nothingToReturnWhenAllOwned() {
        when(wraithRepository.findById(1L)).thenReturn(Optional.of(wraith(1, 3, 2L, List.of(4L))));
        assertThat(wraithService.slay(1L, List.of(4L))).isEmpty();
    }

    @Test
    @DisplayName("망령의 모습: 이름·기술은 망령의 것, HP는 기준치의 1.25배, 점수 보상 고정")
    void dressKeepsServerStats() {
        SessionMonster monster = new SessionMonster();
        monster.setMaxHp(100);
        monster.setCurrentHp(100);
        monster.setAtk(20);

        WraithService.dress(monster, wraith(1, 3, 2L, List.of()));

        assertThat(monster.getName()).isEqualTo(WraithService.WRAITH_NAME);
        assertThat(monster.getMaxHp()).isEqualTo(125);
        assertThat(monster.getCurrentHp()).isEqualTo(125);
        assertThat(monster.getAtk()).isEqualTo(20);
        assertThat(monster.getMoves()).containsExactly("화염구");
        assertThat(monster.getScoreReward()).isEqualTo(WraithService.SCORE_REWARD);
    }
}
