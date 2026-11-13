package com.renovandelve.service;

import com.renovandelve.domain.GameSkillSlotEntity;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.repository.GameSkillSlotRepository;
import com.renovandelve.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 스킬 슬롯 규칙: 8개 상한, 가득 찼을 때의 교체.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillSlotServiceTest {

    private static final long NEW_SKILL_ID = 100L;

    @Mock private GameSkillSlotRepository gameSkillSlotRepository;
    @Mock private SkillRepository skillRepository;
    @InjectMocks private SkillSlotService skillSlotService;

    private SkillEntity newSkill;

    @BeforeEach
    void setUp() {
        newSkill = SkillEntity.builder().skillId(NEW_SKILL_ID).name("처형").build();
        when(skillRepository.findById(NEW_SKILL_ID)).thenReturn(Optional.of(newSkill));
    }

    /** 스킬 n개를 든 게임. 슬롯 ID = 인덱스 + 1, 스킬 ID = 인덱스 + 1 */
    private GameStateEntity gameWithSkills(int count) {
        List<GameSkillSlotEntity> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(GameSkillSlotEntity.builder()
                    .slotId((long) i + 1).slotIndex(i).coolTime(2)
                    .skill(SkillEntity.builder().skillId((long) i + 1).name("스킬" + i).build())
                    .build());
        }
        return GameStateEntity.builder().gameStateId(7L).gameSkillSlot(slots).build();
    }

    @Test
    @DisplayName("빈 슬롯이 있으면 가장 앞의 빈 번호에 새 슬롯으로 들어간다")
    void addsToFirstFreeIndex() {
        GameStateEntity game = gameWithSkills(3);

        skillSlotService.learn(game, NEW_SKILL_ID, null);

        assertThat(game.getGameSkillSlot()).hasSize(4);
        GameSkillSlotEntity added = game.getGameSkillSlot().get(3);
        assertThat(added.getSlotIndex()).isEqualTo(3);
        assertThat(added.getSkill()).isSameAs(newSkill);
        verify(gameSkillSlotRepository).save(added);
    }

    @Test
    @DisplayName("8개가 가득 찼는데 버릴 슬롯을 고르지 않으면 거부하고 아무것도 바꾸지 않는다")
    void fullWithoutReplacementIsRejected() {
        GameStateEntity game = gameWithSkills(8);

        assertThatThrownBy(() -> skillSlotService.learn(game, NEW_SKILL_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("버릴 스킬");
        assertThat(game.getGameSkillSlot()).hasSize(8);
        verify(gameSkillSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("8개가 가득 찼을 때 버릴 슬롯을 고르면 그 자리의 스킬만 바뀐다 (슬롯 수와 순번은 그대로, 쿨타임은 초기화)")
    void replacesChosenSlotWhenFull() {
        GameStateEntity game = gameWithSkills(8);

        skillSlotService.learn(game, NEW_SKILL_ID, 5L);

        assertThat(game.getGameSkillSlot()).hasSize(8);
        GameSkillSlotEntity replaced = game.getGameSkillSlot().get(4);
        assertThat(replaced.getSlotId()).isEqualTo(5L);
        assertThat(replaced.getSlotIndex()).isEqualTo(4);
        assertThat(replaced.getSkill()).isSameAs(newSkill);
        assertThat(replaced.getCoolTime()).isZero();
        assertThat(game.getGameSkillSlot()).filteredOn(slot -> slot.getSlotId() != 5L)
                .allSatisfy(slot -> assertThat(slot.getSkill().getName()).startsWith("스킬"));
        verify(gameSkillSlotRepository).save(replaced);
    }

    @Test
    @DisplayName("이 게임에 없는 슬롯은 버릴 대상으로 지정할 수 없다")
    void cannotReplaceForeignSlot() {
        GameStateEntity game = gameWithSkills(8);

        assertThatThrownBy(() -> skillSlotService.learn(game, NEW_SKILL_ID, 999L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gameSkillSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 가진 스킬은 다시 배울 수 없다 (교체로도)")
    void duplicateSkillIsRejected() {
        GameStateEntity game = gameWithSkills(8);

        assertThatThrownBy(() -> skillSlotService.learn(game, 3L, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 보유");
    }
}
