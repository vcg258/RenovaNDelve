package com.renovandelve.service;

import com.renovandelve.repository.ArmorRepository;
import com.renovandelve.repository.ConsumableRepository;
import com.renovandelve.repository.HelmetRepository;
import com.renovandelve.repository.RelicRepository;
import com.renovandelve.repository.SkillRepository;
import com.renovandelve.repository.WeaponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 게임 데이터 목록(카탈로그) 조회. 도감의 수집률과 아직 얻지 못한 칸("???")을 계산하는 데 쓴다.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final WeaponRepository weaponRepository;
    private final ArmorRepository armorRepository;
    private final HelmetRepository helmetRepository;
    private final SkillRepository skillRepository;
    private final RelicRepository relicRepository;
    private final ConsumableRepository consumableRepository;

    /**
     * 도감 분류별 전체 항목 수.
     * 기본 공격(비용 0 스킬)은 처음부터 주어지고 도감에 등록되지 않으므로 스킬 수에서 뺀다.
     *
     * @return weapon, armor, helmet, skill, relic, consumable 별 개수
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getTotals() {
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("weapon", weaponRepository.count());
        totals.put("armor", armorRepository.count());
        totals.put("helmet", helmetRepository.count());
        totals.put("skill", skillRepository.findAll().stream().filter(skill -> !skill.isBasic()).count());
        totals.put("relic", relicRepository.count());
        totals.put("consumable", consumableRepository.count());
        return totals;
    }
}
