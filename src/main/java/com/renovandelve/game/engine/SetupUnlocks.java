package com.renovandelve.game.engine;

import com.renovandelve.catalog.domain.Rarity;

import java.util.Set;

/**
 * 원정 준비 해금 규칙. 흔함 등급은 처음부터 고를 수 있고, 희귀·영웅은 도감에 한 번 등록해야(원정 중 손에 넣어야) 고를 수 있다.
 * 죽어도 남는 성장이 "예산이 조금 는다"뿐이던 것을, 손에 넣은 장비로 다음 원정을 시작하는 성장으로 바꾼다.
 *
 * 각 집합은 이 회원의 도감에 있는 ID다.
 */
public record SetupUnlocks(Set<Long> weapons, Set<Long> armors, Set<Long> helmets, Set<Long> skills, Set<Long> relics) {

    /** 도감 없이도 고를 수 있는 등급인가 */
    public static boolean openByDefault(Rarity rarity) {
        return rarity == null || rarity == Rarity.COMMON;
    }

    public boolean weapon(Long id, Rarity rarity) {
        return openByDefault(rarity) || weapons.contains(id);
    }

    public boolean armor(Long id, Rarity rarity) {
        return openByDefault(rarity) || armors.contains(id);
    }

    public boolean helmet(Long id, Rarity rarity) {
        return openByDefault(rarity) || helmets.contains(id);
    }

    public boolean skill(Long id, Rarity rarity) {
        return openByDefault(rarity) || skills.contains(id);
    }

    public boolean relic(Long id, Rarity rarity) {
        return openByDefault(rarity) || relics.contains(id);
    }
}
