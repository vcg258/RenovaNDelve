package com.renovandelve.game.service;

import com.renovandelve.catalog.domain.Rarity;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.catalog.mapper.CatalogMapper;
import com.renovandelve.catalog.repository.ArmorRepository;
import com.renovandelve.catalog.repository.HelmetRepository;
import com.renovandelve.catalog.repository.RelicRepository;
import com.renovandelve.catalog.repository.SkillRepository;
import com.renovandelve.catalog.repository.WeaponRepository;
import com.renovandelve.collection.repository.CollectionArmorRepository;
import com.renovandelve.collection.repository.CollectionConsumableRepository;
import com.renovandelve.collection.repository.CollectionHelmetRepository;
import com.renovandelve.collection.repository.CollectionRelicRepository;
import com.renovandelve.collection.repository.CollectionSkillRepository;
import com.renovandelve.collection.repository.CollectionWeaponRepository;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.request.NewGameRequest;
import com.renovandelve.game.dto.response.SetupOptionsDTO;
import com.renovandelve.game.engine.KingdomState;
import com.renovandelve.game.mapper.GameMapper;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.exception.ErrorCode;
import com.renovandelve.member.domain.MemberEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 원정 준비의 메타 성장: 희귀·영웅은 도감에 등록한 것만 고를 수 있고, 클리어한 최고 난이도를 목표로 알려 준다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameStateServiceImplTest {

    @Mock private GameStateRepository gameStateRepository;
    @Mock private KingdomService kingdomService;
    @Mock private ChronicleService chronicleService;
    @Mock private WeaponRepository weaponRepository;
    @Mock private ArmorRepository armorRepository;
    @Mock private HelmetRepository helmetRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private RelicRepository relicRepository;
    @Mock private GameSessionService gameSessionService;
    @Mock private CollectionWeaponRepository collectionWeaponRepository;
    @Mock private CollectionArmorRepository collectionArmorRepository;
    @Mock private CollectionHelmetRepository collectionHelmetRepository;
    @Mock private CollectionSkillRepository collectionSkillRepository;
    @Mock private CollectionRelicRepository collectionRelicRepository;
    @Mock private CollectionConsumableRepository collectionConsumableRepository;
    @Mock private CatalogMapper catalogMapper;
    @Mock private GameMapper gameMapper;

    @InjectMocks private GameStateServiceImpl service;

    private final MemberEntity member = MemberEntity.builder().memberId(1L).loginId("tester").build();

    private final WeaponEntity oldSword = WeaponEntity.builder().weaponId(1L).name("낡은 검").scoreCost(0).rarity(Rarity.COMMON).build();
    private final WeaponEntity rareSword = WeaponEntity.builder().weaponId(2L).name("천상 기사의 검").scoreCost(250).rarity(Rarity.RARE).build();
    private final WeaponEntity epicMace = WeaponEntity.builder().weaponId(3L).name("심연의 철퇴").scoreCost(420).rarity(Rarity.EPIC).build();
    private final ArmorEntity cloth = ArmorEntity.builder().armorId(1L).name("천 옷").scoreCost(0).rarity(Rarity.COMMON).build();
    private final HelmetEntity cap = HelmetEntity.builder().helmetId(1L).name("가죽 모자").scoreCost(0).rarity(Rarity.COMMON).build();
    private final SkillEntity basic = SkillEntity.builder().skillId(1L).name("기본 공격").cost(0).rarity(Rarity.COMMON).build();
    private final SkillEntity execute = SkillEntity.builder().skillId(2L).name("처형").cost(300).rarity(Rarity.EPIC).build();
    private final RelicEntity core = RelicEntity.builder().relicId(1L).name("심연의 핵").scoreCost(500).rarity(Rarity.EPIC).build();

    @BeforeEach
    void setUp() {
        when(weaponRepository.findAll()).thenReturn(List.of(oldSword, rareSword, epicMace));
        when(armorRepository.findAll()).thenReturn(List.of(cloth));
        when(helmetRepository.findAll()).thenReturn(List.of(cap));
        when(skillRepository.findAll()).thenReturn(List.of(basic, execute));
        when(relicRepository.findAll()).thenReturn(List.of(core));
        when(weaponRepository.findById(2L)).thenReturn(Optional.of(rareSword));
        when(weaponRepository.findById(3L)).thenReturn(Optional.of(epicMace));
        when(skillRepository.findByName("기본 공격")).thenReturn(Optional.of(basic));
        when(skillRepository.findById(2L)).thenReturn(Optional.of(execute));
        when(relicRepository.findById(1L)).thenReturn(Optional.of(core));
        when(kingdomService.currentState()).thenReturn(KingdomState.BALANCE);
        when(gameStateRepository.save(any(GameStateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // 이 회원은 희귀 무기 하나만 도감에 올렸다
        when(collectionWeaponRepository.findWeaponIdsByMember(member)).thenReturn(Set.of(2L));
        when(collectionWeaponRepository.countByMember(member)).thenReturn(1L);
    }

    @Test
    @DisplayName("흔함은 처음부터, 희귀·영웅은 도감에 있는 것만 고를 수 있다")
    void lockedItemsAreThoseNotInCollection() {
        SetupOptionsDTO options = service.getSetupOptions(member);

        assertThat(options.locked().weapons()).containsExactly(3L);   // 도감에 없는 영웅 무기만 잠김
        assertThat(options.locked().skills()).containsExactly(2L);
        assertThat(options.locked().relics()).containsExactly(1L);
        assertThat(options.locked().armors()).isEmpty();
        assertThat(options.budget()).isEqualTo(GameStateServiceImpl.STARTING_BUDGET + GameStateServiceImpl.BUDGET_PER_COLLECTION);
    }

    @Test
    @DisplayName("잠긴 항목으로 원정을 시작하려 하면 서버가 거부한다 (화면을 거치지 않은 요청)")
    void rejectsLockedItems() {
        assertThatThrownBy(() -> service.createNewGame(request(3L, List.of(), List.of()), member))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SETUP_ITEM_LOCKED))
                .hasMessageContaining("심연의 철퇴");
        assertThatThrownBy(() -> service.createNewGame(request(null, List.of(2L), List.of()), member))
                .hasMessageContaining("처형");
        assertThatThrownBy(() -> service.createNewGame(request(null, List.of(), List.of(1L)), member))
                .hasMessageContaining("심연의 핵");
    }

    @Test
    @DisplayName("도감에 올린 희귀 무기로는 원정을 시작할 수 있다")
    void collectedRareItemIsAllowed() {
        GameStateEntity game = service.createNewGame(request(2L, List.of(), List.of()), member);

        assertThat(game.getGameEquipment().getWeapon().getName()).isEqualTo("천상 기사의 검");
    }

    @Test
    @DisplayName("다음 목표용으로 클리어한 가장 높은 난이도를 알려 준다")
    void bestClearedDifficulty() {
        when(gameStateRepository.findClearedDifficulties(1L)).thenReturn(Set.of(Difficulty.EASY, Difficulty.NORMAL));

        assertThat(service.getSetupOptions(member).bestCleared()).isEqualTo(Difficulty.NORMAL);
    }

    private static NewGameRequest request(Long weaponId, List<Long> skillIds, List<Long> relicIds) {
        return new NewGameRequest("EASY", weaponId, null, null, skillIds, List.of(), relicIds);
    }
}
