package com.renovandelve.game.dto.response;

import com.renovandelve.game.engine.DemonContract;

/**
 * 원정에 걸린 악마의 계약 (화면용). 조항 자체는 보내지 않고 서명 전에 본 힌트만 보낸다.
 *
 * @param boon       받은 혜택 이름
 * @param hint       조항의 작은 글씨
 * @param breakable  지금 파기할 수 있으면 true (천상 성향과 골드가 충분할 때)
 * @param breakCost  파기 비용 (골드)
 */
public record ContractDTO(String boon, String hint, boolean breakable, int breakCost) {

    /**
     * 계약을 화면용으로 옮긴다.
     *
     * @param contract       계약 (없으면 null)
     * @param heavenAffinity 천상 성향
     * @param gold           보유 골드
     * @param stage          스테이지
     * @return DTO, 계약이 없으면 null
     */
    public static ContractDTO of(DemonContract contract, int heavenAffinity, int gold, int stage) {
        if (contract == null) return null;
        int cost = DemonContract.breakCost(stage);
        boolean breakable = heavenAffinity >= DemonContract.BREAK_HEAVEN_AFFINITY && gold >= cost;
        return new ContractDTO(contract.boon().title(), contract.clause().hint(), breakable, cost);
    }
}
