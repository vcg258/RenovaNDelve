package com.renovandelve.game.service;

import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.catalog.repository.ArmorRepository;
import com.renovandelve.catalog.repository.ConsumableRepository;
import com.renovandelve.catalog.repository.HelmetRepository;
import com.renovandelve.catalog.repository.RelicRepository;
import com.renovandelve.catalog.repository.SkillRepository;
import com.renovandelve.catalog.repository.WeaponRepository;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.engine.ShopStockItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 상점 진열: 착용 중인 장비는 진열하지 않고, 장비에는 보너스를 담는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShopServiceImplTest {

    @Mock private WeaponRepository weaponRepository;
    @Mock private ArmorRepository armorRepository;
    @Mock private HelmetRepository helmetRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private RelicRepository relicRepository;
    @Mock private ConsumableRepository consumableRepository;

    @InjectMocks private ShopServiceImpl shopService;

    private GameStateEntity gameState;
    private final WeaponEntity oldSword = weapon(1L, 5);
    private final WeaponEntity greatSword = weapon(2L, 8);
    private final ArmorEntity cloth = armor(1L, 2);
    private final HelmetEntity cap = helmet(1L, 10);

    @BeforeEach
    void setUp() {
        gameState = mock(GameStateEntity.class);
        when(gameState.getGameStateId()).thenReturn(7L);
        when(gameState.getCurrentStage()).thenReturn(1);
        when(consumableRepository.findAll()).thenReturn(List.of());
        when(skillRepository.findBySkillNotOwner(anyLong())).thenReturn(List.of());
        when(relicRepository.findByRelicNotOwner(anyLong())).thenReturn(List.of());
        when(weaponRepository.findAll()).thenReturn(List.of(oldSword, greatSword));
        when(armorRepository.findAll()).thenReturn(List.of(cloth));
        when(helmetRepository.findAll()).thenReturn(List.of(cap));
    }

    @RepeatedTest(10)
    @DisplayName("착용 중인 장비는 진열하지 않는다 (부위의 유일한 장비를 끼고 있으면 그 부위는 비운다)")
    void excludesEquippedGear() {
        GameEquipmentEntity equipment = mock(GameEquipmentEntity.class);
        when(equipment.getWeapon()).thenReturn(oldSword);
        when(equipment.getArmor()).thenReturn(cloth);
        when(gameState.getGameEquipment()).thenReturn(equipment);

        List<ShopStockItem> stock = shopService.generateStock(gameState);

        assertThat(stock).filteredOn(i -> i.getItemType().equals("WEAPON"))
                .singleElement().satisfies(i -> {
                    assertThat(i.getItemId()).isEqualTo(2L);
                    assertThat(i.getBonus()).isEqualTo(8);
                });
        assertThat(stock).noneMatch(i -> i.getItemType().equals("ARMOR"));
        assertThat(stock).filteredOn(i -> i.getItemType().equals("HELMET"))
                .singleElement().satisfies(i -> assertThat(i.getBonus()).isEqualTo(10));
    }

    @Test
    @DisplayName("장비 슬롯이 없어도 진열할 수 있고, 장비가 아닌 진열품에는 보너스가 없다")
    void worksWithoutEquipment() {
        when(gameState.getGameEquipment()).thenReturn(null);

        List<ShopStockItem> stock = shopService.generateStock(gameState);

        assertThat(stock).extracting(ShopStockItem::getItemType).contains("WEAPON", "ARMOR", "HELMET");
        assertThat(stock).allMatch(i -> i.getBonus() != null);
    }

    private static WeaponEntity weapon(long id, int bonus) {
        WeaponEntity w = mock(WeaponEntity.class);
        when(w.getWeaponId()).thenReturn(id);
        when(w.getName()).thenReturn("무기" + id);
        when(w.getAtkBonus()).thenReturn(bonus);
        return w;
    }

    private static ArmorEntity armor(long id, int bonus) {
        ArmorEntity a = mock(ArmorEntity.class);
        when(a.getArmorId()).thenReturn(id);
        when(a.getName()).thenReturn("갑옷" + id);
        when(a.getDefBonus()).thenReturn(bonus);
        return a;
    }

    private static HelmetEntity helmet(long id, int bonus) {
        HelmetEntity h = mock(HelmetEntity.class);
        when(h.getHelmetId()).thenReturn(id);
        when(h.getName()).thenReturn("투구" + id);
        when(h.getHpBonus()).thenReturn(bonus);
        return h;
    }
}
