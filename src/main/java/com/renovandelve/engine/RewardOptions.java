package com.renovandelve.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 전투 승리 보상 후보 (Redis 저장).
 * 클라이언트가 보상 후보에 없는 아이템을 요청하지 못하도록 서버가 ID를 보관한다.
 */
@Data
@NoArgsConstructor
public class RewardOptions {
    private int exp;
    private int gold;
    private List<Long> skillIds = new ArrayList<>();
    private List<Long> relicIds = new ArrayList<>();
    private List<Long> consumableIds = new ArrayList<>();
}
