package com.renovandelve.global.config;

import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.catalog.mapper.CatalogMapper;
import com.renovandelve.catalog.mapper.CatalogMapperImpl;
import com.renovandelve.collection.domain.CollectionWeaponEntity;
import com.renovandelve.collection.dto.CollectionWeaponDTO;
import com.renovandelve.collection.mapper.CollectionMapper;
import com.renovandelve.collection.mapper.CollectionMapperImpl;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.GameCharacterDTO;
import com.renovandelve.game.dto.GameEquipmentDTO;
import com.renovandelve.game.dto.GameStateDTO;
import com.renovandelve.game.dto.SkillInfoDTO;
import com.renovandelve.game.mapper.GameMapper;
import com.renovandelve.game.mapper.GameMapperImpl;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.dto.MemberDTO;
import com.renovandelve.member.mapper.MemberMapper;
import com.renovandelve.member.mapper.MemberMapperImpl;
import com.renovandelve.ranking.domain.RankingEntity;
import com.renovandelve.ranking.dto.response.RankingViewDTO;
import com.renovandelve.ranking.mapper.RankingMapper;
import com.renovandelve.ranking.mapper.RankingMapperImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MapStruct가 만든 변환 코드 확인. 필드 이름이 같은 곳은 컴파일 단계(unmappedTargetPolicy = ERROR)가 지키므로,
 * 여기서는 이름이 다르거나 일부러 비우는 곳만 본다.
 */
class MapStructMappersTest {

    private final CatalogMapper catalogMapper = new CatalogMapperImpl();
    private final MemberMapper memberMapper = new MemberMapperImpl();
    private final GameMapper gameMapper = new GameMapperImpl(catalogMapper);
    private final CollectionMapper collectionMapper = new CollectionMapperImpl(catalogMapper, memberMapper);
    private final RankingMapper rankingMapper = new RankingMapperImpl(memberMapper, gameMapper);

    private MemberEntity member() {
        return MemberEntity.builder().memberId(7L).loginId("hero01").nickname("용사")
                .passwordHash("$2a$hash").socialToken("google-token").build();
    }

    @Test
    @DisplayName("회원: ID와 표시 이름만 옮기고 비밀번호 해시·소셜 토큰은 옮기지 않는다")
    void memberPublicFieldsOnly() {
        MemberDTO dto = memberMapper.toPublicDTO(member());

        assertThat(dto.getMemberId()).isEqualTo(7L);
        assertThat(dto.getLoginId()).isEqualTo("용사");
        assertThat(dto.getPasswordHash()).isNull();
        assertThat(dto.getSocialToken()).isNull();
    }

    @Test
    @DisplayName("스킬: 고르는 화면용 DTO에는 ID가 들어간다")
    void skillInfoKeepsId() {
        SkillEntity skill = SkillEntity.builder().skillId(3L).name("베기").attackCount(2)
                .coefficient(new BigDecimal("1.50")).cooldown(1).build();

        SkillInfoDTO dto = catalogMapper.toSkillInfoDTO(skill);

        assertThat(dto.skillId()).isEqualTo(3L);
        assertThat(dto.name()).isEqualTo("베기");
        assertThat(dto.coefficient()).isEqualByComparingTo("1.50");
    }

    @Test
    @DisplayName("캐릭터: 엔티티의 exp가 DTO의 currentExp로 간다")
    void characterExp() {
        GameCharacterEntity character = GameCharacterEntity.builder().characterId(1L).level(3).exp(120)
                .currentHp(80).maxHp(130).attack(15).defense(8).build();

        GameCharacterDTO dto = gameMapper.toCharacterDTO(character);

        assertThat(dto.getCurrentExp()).isEqualTo(120);
        assertThat(dto.getMaxHp()).isEqualTo(130);
        assertThat(dto.getGameState()).isNull();
    }

    @Test
    @DisplayName("장비: 비어 있는 부위는 null, 장비 슬롯이 없으면 null")
    void equipmentEmptySlots() {
        WeaponEntity weapon = WeaponEntity.builder().weaponId(2L).name("철검").atkBonus(5).build();
        GameEquipmentEntity equipment = GameEquipmentEntity.builder().equipId(9L).weapon(weapon).build();

        GameEquipmentDTO dto = gameMapper.toEquipmentDTO(equipment);

        assertThat(dto.getWeapon().atkBonus()).isEqualTo(5);
        assertThat(dto.getArmor()).isNull();
        assertThat(dto.getHelmet()).isNull();
        assertThat(gameMapper.toEquipmentDTO(null)).isNull();
    }

    @Test
    @DisplayName("게임: 진행 정보를 옮기고 회원은 비운다")
    void gameStateWithoutMember() {
        GameStateEntity game = GameStateEntity.builder().gameStateId(5L).difficulty(Difficulty.HARD)
                .isActive(true).currentStage(2).currentNodeId(4L).score(300).gold(90).hasPhoenix(false)
                .member(member()).build();

        GameStateDTO dto = gameMapper.toGameStateDTO(game);

        assertThat(dto.getGameStateId()).isEqualTo(5L);
        assertThat(dto.getIsActive()).isTrue();
        assertThat(dto.getCurrentNodeId()).isEqualTo(4L);
        assertThat(dto.getMember()).isNull();
    }

    @Test
    @DisplayName("도감: 회원은 표시 이름만, 항목은 카탈로그 변환을 쓴다")
    void collectionEntry() {
        WeaponEntity weapon = WeaponEntity.builder().weaponId(2L).name("철검").atkBonus(5).build();
        LocalDateTime acquired = LocalDateTime.of(2026, 11, 19, 12, 0);
        CollectionWeaponEntity entry = CollectionWeaponEntity.builder().collectionId(11L).acquiredAt(acquired)
                .member(member()).weapon(weapon).build();

        CollectionWeaponDTO dto = collectionMapper.toDTO(entry);

        assertThat(dto.collectionId()).isEqualTo(11L);
        assertThat(dto.acquiredAt()).isEqualTo(acquired);
        assertThat(dto.member().getLoginId()).isEqualTo("용사");
        assertThat(dto.member().getPasswordHash()).isNull();
        assertThat(dto.weapon().name()).isEqualTo("철검");
    }

    @Test
    @DisplayName("랭킹 한 줄: 순위는 인자로, 아이디 자리에는 표시 이름")
    void rankingView() {
        RankingEntity ranking = RankingEntity.builder().rankingId(1L).difficulty(Difficulty.NORMAL)
                .bestScore(1200).clearTime(600).member(member()).build();

        RankingViewDTO dto = rankingMapper.toViewDTO(ranking, 3);

        assertThat(dto.rank()).isEqualTo(3);
        assertThat(dto.loginId()).isEqualTo("용사");
        assertThat(dto.bestScore()).isEqualTo(1200);
    }
}
