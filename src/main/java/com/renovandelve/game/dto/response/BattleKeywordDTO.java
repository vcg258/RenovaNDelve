package com.renovandelve.game.dto.response;

/**
 * 전투 키워드 설명 (상태이상, 보스 격노).
 *
 * @param key         버프 키 (BLEED / BURN / POISON / STUN / ENRAGED)
 * @param label       한국어 이름
 * @param description 규칙 설명
 */
public record BattleKeywordDTO(String key, String label, String description) {
}
