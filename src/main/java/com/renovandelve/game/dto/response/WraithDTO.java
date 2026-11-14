package com.renovandelve.game.dto.response;

import com.renovandelve.game.domain.WraithEntity;

import java.util.List;

/**
 * 다음 원정에서 기다리는 망령 (원정 준비 화면 안내용). 나타날 노드는 알려 주지 않는다.
 *
 * @param stage       기다리는 스테이지
 * @param level       쓰러질 때의 레벨
 * @param killerName  쓰러뜨린 적 (조우에서 쓰러졌으면 null)
 * @param skills      망령이 쓰는 기술
 * @param faction     기울었던 세력 (HEAVEN / ABYSS / HUMAN, 없으면 null)
 * @param escapesLeft 앞으로 몇 번 더 달아나면 흩어지는지
 */
public record WraithDTO(int stage, int level, String killerName, List<String> skills, String faction, int escapesLeft) {

    /**
     * 엔티티를 화면용으로 옮긴다.
     *
     * @param wraith 망령
     * @return DTO
     */
    public static WraithDTO from(WraithEntity wraith) {
        return new WraithDTO(wraith.getStage(), wraith.getLevel(), wraith.getKillerName(), wraith.skillNames(),
                wraith.getFaction(), Math.max(0, WraithEntity.MAX_ESCAPES - wraith.getEscapes()));
    }
}
