package com.renovandelve.engine;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 보유 유물 이름 -> 전투/보상 패시브 효과 해석기.
 *
 * 1) 단일 유물 효과를 먼저 적용하고,
 * 2) 특정 유물 "조합"이 모이면 추가 시너지가 발동한다.
 *    (시너지 목록은 StatusDTO.relicSynergies로 클라이언트에 노출)
 */
@Getter
public class RelicEffects {
    private double goldMultiplier = 1.0;        // 행운의 반지
    private double skillDamageMultiplier = 1.0; // 마력 증폭 수정
    private double bleedMultiplier = 1.0;       // 핏빛 반지
    private double attackMultiplier = 1.0;      // 심연의 핵
    private int flatDamageReduction = 0;        // 수호자의 문장
    private int defenseBonus = 0;               // 심연의 핵
    private int cooldownReduction = 0;          // 고대의 부적
    private double critBonus = 0.0;             // 사냥꾼의 눈
    private int critBleedStacks = 0;            // [시너지] 치명타 시 출혈 부여
    private final List<String> activeSynergies = new ArrayList<>();

    public static RelicEffects of(Collection<String> relicNames) {
        RelicEffects effects = new RelicEffects();
        if (relicNames == null) return effects;

        Set<String> owned = new HashSet<>();
        for (String name : relicNames) {
            if (name != null) owned.add(name);
        }

        // ===== 단일 유물 효과 =====
        for (String name : owned) {
            switch (name) {
                case "행운의 반지" -> effects.goldMultiplier += 0.25;
                case "마력 증폭 수정" -> effects.skillDamageMultiplier += 0.15;
                case "핏빛 반지" -> effects.bleedMultiplier += 0.5;
                case "수호자의 문장" -> effects.flatDamageReduction += 2;
                case "심연의 핵" -> {
                    effects.attackMultiplier += 0.10;
                    effects.defenseBonus += 2;
                }
                case "고대의 부적" -> effects.cooldownReduction += 1;
                case "사냥꾼의 눈" -> effects.critBonus += 0.10;
                default -> { /* 효과 미구현 유물은 점수용 소장품 */ }
            }
        }

        // ===== 조합 시너지 =====
        // 피의 사냥: 치명타가 상처를 찢어 출혈을 남긴다
        if (owned.contains("핏빛 반지") && owned.contains("사냥꾼의 눈")) {
            effects.critBleedStacks += 2;
            effects.activeSynergies.add("🩸 피의 사냥 — 치명타 시 출혈 2스택 부여 (핏빛 반지 + 사냥꾼의 눈)");
        }
        // 비전 순환: 증폭된 마력이 부적을 타고 순환한다
        if (owned.contains("마력 증폭 수정") && owned.contains("고대의 부적")) {
            effects.skillDamageMultiplier += 0.10;
            effects.activeSynergies.add("🔮 비전 순환 — 스킬 피해 추가 +10% (마력 증폭 수정 + 고대의 부적)");
        }
        // 탐욕의 심연: 심연은 황금마저 집어삼킨다
        if (owned.contains("행운의 반지") && owned.contains("심연의 핵")) {
            effects.goldMultiplier += 0.25;
            effects.activeSynergies.add("🪙 탐욕의 심연 — 골드 획득 추가 +25% (행운의 반지 + 심연의 핵)");
        }
        // 심연의 방벽: 어둠이 갑주가 되어 감싼다
        if (owned.contains("수호자의 문장") && owned.contains("심연의 핵")) {
            effects.flatDamageReduction += 2;
            effects.activeSynergies.add("🛡 심연의 방벽 — 받는 피해 추가 -2 (수호자의 문장 + 심연의 핵)");
        }
        // 사신의 계약: 3종 이상의 유물이 공명하면 모든 능력이 미약하게 상승
        if (owned.size() >= 4) {
            effects.attackMultiplier += 0.05;
            effects.critBonus += 0.05;
            effects.activeSynergies.add("💀 사신의 공명 — 유물 4개 이상: 공격력 +5%, 치명타 +5%");
        }

        return effects;
    }
}
