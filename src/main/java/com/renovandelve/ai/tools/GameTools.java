package com.renovandelve.ai.tools;

import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.catalog.repository.ArmorRepository;
import com.renovandelve.catalog.repository.ConsumableRepository;
import com.renovandelve.catalog.repository.HelmetRepository;
import com.renovandelve.catalog.repository.RelicRepository;
import com.renovandelve.catalog.repository.WeaponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GameTools {
    private final HelmetRepository helmetRepository;
    private final ArmorRepository armorRepository;
    private final WeaponRepository weaponRepository;
    private final ConsumableRepository consumableRepository;
    private final RelicRepository relicRepository;

    /**
     * [AI Tool] 이름으로 투구 하나를 찾아 능력치와 설명을 돌려준다.
     * 정확히 일치 → 이름 포함 → 유사도 순의 3중 검색(findBestMatch)으로 찾는다.
     *
     * @param name 찾을 투구 이름 (오타·부분 이름 허용)
     * @return 능력치와 설명, 없으면 안내 문구
     */
    @Tool(description = "투구(헬멧)의 능력치와 설명을 조회합니다. 플레이어가 특정 투구의 체력 보너스, 특수 효과, 스타팅 포인트 비용을 물어볼 때 호출하세요.")
    public String getHelmetDescription(@ToolParam(description = "조회할 투구의 이름. 예: 철제 투구, 가죽 모자") String name) {
        // 3중 검색
        Optional<HelmetEntity> find = findBestMatch(
                name, helmetRepository.findByNameContains(name), helmetRepository.findAll(), HelmetEntity::getName);
        // 검색을 했을때 값이 있다면
        if (find.isPresent()) {
            HelmetEntity helmet = find.get();
            return "투구명 : %s\n투구종류 : %s\n추가 체력 : %d\n설명 : %s\n스타팅 포인트 비용 : %d"
                    .formatted(helmet.getName(), helmet.getType(), helmet.getHpBonus(), helmet.getDescription(), helmet.getScoreCost());
        }
        // 없을 경우 투구 카테고리에서 전체 검색으로 이름 한번더 검색
        return notFoundWithData("투구", name, helmetRepository.findAll(), HelmetEntity::getName);
    }

    /**
     * [AI Tool] 이름으로 갑옷 하나를 찾아 능력치와 설명을 돌려준다.
     * 정확히 일치 → 이름 포함 → 유사도 순의 3중 검색(findBestMatch)으로 찾는다.
     *
     * @param name 찾을 갑옷 이름 (오타·부분 이름 허용)
     * @return 능력치와 설명, 없으면 안내 문구
     */
    @Tool(description = "갑옷의 능력치와 설명을 조회합니다. 플레이어가 특정 갑옷의 방어력 보너스, 특수 효과, 스타팅 포인트 비용을 물어볼 때 호출하세요.")
    public String getArmorDescription(@ToolParam(description = "조회할 갑옷의 이름. 예: 사슬 갑옷, 판금 갑옷") String name) {
        Optional<ArmorEntity> find = findBestMatch(
                name, armorRepository.findByNameContains(name), armorRepository.findAll(), ArmorEntity::getName);
        if (find.isPresent()) {
            ArmorEntity armor = find.get();
            return "갑옷명 : %s\n갑옷종류 : %s\n추가 방어력 : %d\n설명 : %s\n스타팅 포인트 비용 : %d"
                    .formatted(armor.getName(), armor.getType(), armor.getDefBonus(), armor.getDescription(), armor.getScoreCost());
        }
        return notFoundWithData("갑옷", name, armorRepository.findAll(), ArmorEntity::getName);
    }

    /**
     * [AI Tool] 이름으로 무기 하나를 찾아 능력치와 설명을 돌려준다.
     * 정확히 일치 → 이름 포함 → 유사도 순의 3중 검색(findBestMatch)으로 찾는다.
     *
     * @param name 찾을 무기 이름 (오타·부분 이름 허용)
     * @return 능력치와 설명, 없으면 안내 문구
     */
    @Tool(description = "무기의 능력치와 설명을 조회합니다. 플레이어가 특정 무기의 공격력 보너스, 특수 효과, 스타팅 포인트 비용을 물어볼 때 호출하세요.")
    public String getWeaponDescription(@ToolParam(description = "조회할 무기의 이름. 예: 장검, 단검, 스태프") String name) {
        Optional<WeaponEntity> find =
                findBestMatch(name, weaponRepository.findByNameContains(name), weaponRepository.findAll(), WeaponEntity::getName);
        if (find.isPresent()) {
            WeaponEntity weapon = find.get();
            return "무기명 : %s\n무기종류 : %s\n추가 공격력 : %d\n설명 : %s\n스타팅 포인트 비용 : %d"
                    .formatted(weapon.getName(), weapon.getType(), weapon.getAtkBonus(), weapon.getDescription(), weapon.getScoreCost());
        }

        return notFoundWithData("무기", name, weaponRepository.findAll(), WeaponEntity::getName);
    }

    /**
     * [AI Tool] 이름으로 소모품 하나를 찾아 능력치와 설명을 돌려준다.
     * 정확히 일치 → 이름 포함 → 유사도 순의 3중 검색(findBestMatch)으로 찾는다.
     *
     * @param name 찾을 소모품 이름 (오타·부분 이름 허용)
     * @return 능력치와 설명, 없으면 안내 문구
     */
    @Tool(description = "소모품의 효과와 설명을 조회합니다. 플레이어가 특정 소모품의 효과 타입, 효과 수치, 스타팅 포인트 비용을 물어볼 때 호출하세요.")
    public String getConsumableDescription(@ToolParam(description = "조회할 소모품의 이름. 예: 회복 포션, 불사조의 깃털") String name) {
        Optional<ConsumableEntity> find =
                findBestMatch(name, consumableRepository.findByNameContains(name), consumableRepository.findAll(), ConsumableEntity::getName);
        if (find.isPresent()) {
            ConsumableEntity consumable = find.get();
            return "소모품명 : %s\n효과 타입 : %s\n효과 수치 : %d\n설명 : %s\n스타팅 포인트 비용 : %d"
                    .formatted(consumable.getName(), consumable.getEffectType(), consumable.getEffectValue(), consumable.getDescription(), consumable.getScoreCost());
        }

        return notFoundWithData("소모품", name, consumableRepository.findAll(), ConsumableEntity::getName);
    }

    /**
     * [AI Tool] 이름으로 유물 하나를 찾아 능력치와 설명을 돌려준다.
     * 정확히 일치 → 이름 포함 → 유사도 순의 3중 검색(findBestMatch)으로 찾는다.
     *
     * @param name 찾을 유물 이름 (오타·부분 이름 허용)
     * @return 능력치와 설명, 없으면 안내 문구
     */
    @Tool(description = "유물의 효과와 설명을 조회합니다. 플레이어가 특정 유물의 효과, 희귀도, 스타팅 포인트 비용을 물어볼 때 호출하세요.")
    public String getRelicDescription(@ToolParam(description = "조회할 유물의 이름. 예: 피의 반지, 저주받은 목걸이") String name) {
        Optional<RelicEntity> find =
                findBestMatch(name, relicRepository.findByNameContains(name), relicRepository.findAll(), RelicEntity::getName);
        if (find.isPresent()) {
            RelicEntity relic = find.get();
            return "유물명: %s\n희귀도: %s\n효과: %s\n스타팅 포인트 비용 : %d"
                    .formatted(relic.getName(), relic.getRarity(), relic.getEffectDesc(), relic.getScoreCost());
        }

        return notFoundWithData("유물", name, relicRepository.findAll(), RelicEntity::getName);
    }


    /**
     * [AI Tool] 등록된 투구 전체를 한 줄씩 요약해 돌려준다.
     *
     * @return 목록 문자열, 없으면 안내 문구
     */
    @Tool(description = "투구 전체 목록을 조회합니다. 플레이어가 어떤 투구가 있는지, 기본 투구가 뭔지, 투구 종류를 물어볼 때 호출하세요. score_cost가 0인 항목이 기본 세트입니다.")
    public String getAllHelmets() {
        List<HelmetEntity> helmets = helmetRepository.findAll();
        if (helmets.isEmpty()) return "등록된 투구가 없습니다.";
        return helmets.stream()
                .map(h -> "투구명: %s | 종류: %s | 추가 체력: %d | 비용: %d"
                        .formatted(h.getName(), h.getType(), h.getHpBonus(), h.getScoreCost()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * [AI Tool] 등록된 갑옷 전체를 한 줄씩 요약해 돌려준다.
     *
     * @return 목록 문자열, 없으면 안내 문구
     */
    @Tool(description = "갑옷 전체 목록을 조회합니다. 플레이어가 어떤 갑옷이 있는지, 기본 갑옷이 뭔지, 갑옷 종류를 물어볼 때 호출하세요. score_cost가 0인 항목이 기본 세트입니다.")
    public String getAllArmors() {
        List<ArmorEntity> armors = armorRepository.findAll();
        if (armors.isEmpty()) return "등록된 갑옷이 없습니다.";
        return armors.stream()
                .map(a -> "갑옷명: %s | 종류: %s | 추가 방어력: %d | 비용: %d"
                        .formatted(a.getName(), a.getType(), a.getDefBonus(), a.getScoreCost()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * [AI Tool] 등록된 무기 전체를 한 줄씩 요약해 돌려준다.
     *
     * @return 목록 문자열, 없으면 안내 문구
     */
    @Tool(description = "무기 전체 목록을 조회합니다. 플레이어가 어떤 무기가 있는지, 기본 무기가 뭔지, 무기 종류를 물어볼 때 호출하세요. score_cost가 0인 항목이 기본 세트입니다.")
    public String getAllWeapons() {
        List<WeaponEntity> weapons = weaponRepository.findAll();
        if (weapons.isEmpty()) return "등록된 무기가 없습니다.";
        return weapons.stream()
                .map(w -> "무기명: %s | 종류: %s | 추가 공격력: %d | 비용: %d"
                        .formatted(w.getName(), w.getType(), w.getAtkBonus(), w.getScoreCost()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * [AI Tool] 등록된 소모품 전체를 한 줄씩 요약해 돌려준다.
     *
     * @return 목록 문자열, 없으면 안내 문구
     */
    @Tool(description = "소모품 전체 목록을 조회합니다. 플레이어가 어떤 소모품이 있는지, 소모품 종류를 물어볼 때 호출하세요.")
    public String getAllConsumables() {
        List<ConsumableEntity> consumables = consumableRepository.findAll();
        if (consumables.isEmpty()) return "등록된 소모품이 없습니다.";
        return consumables.stream()
                .map(c -> "소모품명: %s | 효과 타입: %s | 효과 수치: %d | 비용: %d"
                        .formatted(c.getName(), c.getEffectType(), c.getEffectValue(), c.getScoreCost()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * [AI Tool] 등록된 유물 전체를 한 줄씩 요약해 돌려준다.
     *
     * @return 목록 문자열, 없으면 안내 문구
     */
    @Tool(description = "유물 전체 목록을 조회합니다. 플레이어가 어떤 유물이 있는지, 희귀도별 유물 목록, 기본 유물이 뭔지 물어볼 때 호출하세요.")
    public String getAllRelics() {
        List<RelicEntity> relics = relicRepository.findAll();
        if (relics.isEmpty()) return "등록된 유물이 없습니다.";
        return relics.stream()
                .map(r -> "유물명: %s | 희귀도: %s | 효과: %s | 비용: %d"
                        .formatted(r.getName(), r.getRarity(), r.getEffectDesc(), r.getScoreCost()))
                .collect(Collectors.joining("\n"));
    }

    /* HELPER METHODS */

    /**
     * 사용자 입력값(input)으로 아이템 이름을 찾는 공통 매칭 메서드.
     * @param <T> 검색 대상 엔티티
     * @param input 사용자의 입력 키워드
     * @param containsResult 레파짓토리 조회 메서드의 결과값
     * @param all 레파짓토리의 조회로 없다면 리스트에 담아서 한번더 검색할 리스트
     * @param nameExtractor 엔티티에서 비교 대상 이름(all을 이용한)을 추출하는 함수 (
     * @return 일치하는 DB데이터
     */
    private <T> Optional<T> findBestMatch(
            String input,
            Optional<T> containsResult,
            List<T> all,
            Function<T, String> nameExtractor
    ) {
        // 조회 메서드로 데이터를 찾았다면 바로 리턴
        if (containsResult.isPresent()) return containsResult;

        // 데이터 공백 제거
        String normalizedInput = normalize(input);

        // 조회 메서드로 데이터를 못찾았을 경우 리스트에 담아서 한번더 조회
        Optional<T> exact = all.stream()
                .filter(item -> normalize(nameExtractor.apply(item)).equals(normalizedInput)).findFirst();
        if (exact.isPresent()) return exact;

        // 리스트로 조회를 또 못했을 경우 contains로 한번더 조회
        return all.stream()
                .filter(item -> normalize(nameExtractor.apply(item)).contains(normalizedInput)).findFirst();
    }

    /**
     * 사용자가 입력한 값을 찾지 못했을 경우
     * @param <T> 검색 대상 엔티티
     * @param category 사용자 입력 카테고리
     * @param input 사용자 입력값
     * @param all 조회할 데이터를 담은 리스트
     * @param nameExtractor 엔티티에서 이름을 찾을 함수
     * @return 찾지 못했다는 문구 또는 다른 예시 문구
     */
    private <T> String notFoundWithData(
            String category,
            String input,
            List<T> all,
            Function<T, String> nameExtractor
    ) {
        String normalizedInput = normalize(input);
        List<String> data = all.stream()
                .map(nameExtractor)
                .filter(name -> normalize(name).contains(normalizedInput))
                .limit(3)
                .collect(Collectors.toList());

        if (data.isEmpty()) {
            return "해당 %s을(를) 찾을 수 없습니다. 검색어를 조금 더 정확히 입력해주세요.".formatted(category);
        }

        return "해당 %s을(를) 찾지 못했습니다. 혹시 이 중 하나인가요? %s"
                .formatted(category, String.join(", ", data));

    }

    /**
     * 데이터 중간 공백제거
     * @param text Tools 데이터
     * @return 공백 제거 데이터
     */
    private String normalize(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s", "").toLowerCase();
    }
}
