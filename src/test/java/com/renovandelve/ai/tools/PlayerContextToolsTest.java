package com.renovandelve.ai.tools;

import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.engine.GameSession;
import com.renovandelve.game.engine.ShopStockItem;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.service.CurrentMemberService;
import com.renovandelve.ranking.repository.RankingRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 가이드의 상점 진열 조회: 실제 진열품과, 착용 중인 장비와의 비교.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerContextToolsTest {

    @Mock private CurrentMemberService currentMemberService;
    @Mock private GameStateRepository gameStateRepository;
    @Mock private GameSessionService gameSessionService;
    @Mock private RankingRepository rankingRepository;
    @InjectMocks private PlayerContextTools tools;

    private final GameStateEntity game = mock(GameStateEntity.class);
    private final GameSession session = new GameSession();

    @BeforeEach
    void setUp() {
        MemberEntity member = mock(MemberEntity.class);
        when(member.getMemberId()).thenReturn(1L);
        when(currentMemberService.getCurrentMember()).thenReturn(member);
        when(gameStateRepository.findByMember_MemberIdAndIsActiveTrue(1L)).thenReturn(Optional.of(game));
        when(game.getGameStateId()).thenReturn(9L);
        when(game.getGold()).thenReturn(300);
        when(gameSessionService.get(9L)).thenReturn(session);

        WeaponEntity staff = mock(WeaponEntity.class);
        when(staff.getName()).thenReturn("멸망의 지팡이");
        when(staff.getAtkBonus()).thenReturn(20);
        GameEquipmentEntity equipment = mock(GameEquipmentEntity.class);
        when(equipment.getWeapon()).thenReturn(staff);
        when(game.getGameEquipment()).thenReturn(equipment);
    }

    @Test
    @DisplayName("상점에서는 진열품과 착용 장비 비교를 돌려준다 (약한 무기는 손해로 표시)")
    void shopStockComparesWithEquipped() {
        session.setPhase(GameSession.PHASE_SHOP);
        session.setShop(List.of(item("WEAPON", "전쟁 둔기", 280, 10), item("RELIC", "흡혈의 반지", 420, null)));

        String result = tools.getShopStock();

        assertThat(result).contains("멸망의 지팡이(공격 +20)");
        assertThat(result).contains("[무기] 전쟁 둔기, 가격 280, 공격 +10 (지금 착용 중 +20, 사면 -10)");
        assertThat(result).contains("[유물] 흡혈의 반지, 가격 420 (골드 부족)");
    }

    @Test
    @DisplayName("상점이 아니면 그 사실을 알린다")
    void notInShop() {
        session.setPhase(GameSession.PHASE_EXPLORE);
        assertThat(tools.getShopStock()).startsWith("지금은 상점에 있지 않습니다");
    }

    private static ShopStockItem item(String type, String name, int price, Integer bonus) {
        ShopStockItem item = new ShopStockItem();
        item.setItemType(type);
        item.setItemId(1L);
        item.setName(name);
        item.setDescription("설명");
        item.setPrice(price);
        item.setBonus(bonus);
        return item;
    }
}
