package com.renovandelve.game.dto.response;

/**
 * 원정에 걸린 악마의 계약 (화면용). 조항 자체는 보내지 않고 서명 전에 본 힌트만 보낸다.
 *
 * @param boon       받은 혜택 이름
 * @param hint       조항의 작은 글씨
 * @param breakable  지금 파기할 수 있으면 true (천상 성향과 골드가 충분할 때)
 * @param breakCost  파기 비용 (골드)
 */
public record ContractDTO(String boon, String hint, boolean breakable, int breakCost) {
}
