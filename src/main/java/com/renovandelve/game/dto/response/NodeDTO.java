package com.renovandelve.game.dto.response;

import java.util.List;

/**
 * 노드 진입 결과.
 * nodeType = BATTLE / ELITE / BOSS / ENCOUNTER
 * path / pathNote는 새로고침 후에도 복원되도록 세션에서 채운다.
 */
public record NodeDTO(
        String nodeType,
        String story,
        List<BattleMonsterDTO> monsters,
        EncounterDTO encounter,
        String path,      // 이 전투에 적용된 갈림길 (SAFE / DANGER / AMBUSH). 조우 노드는 null
        String pathNote,  // 갈림길이 무엇을 바꿨는지(또는 왜 적용되지 않았는지) 알려주는 한 줄. 없으면 null
        String contractNote // 이번 노드에서 악마의 계약 조항이 발동했다면 그 내용. 없으면 null
) {

    /** 전투 노드 */
    public static NodeDTO battle(String nodeType, String story, List<BattleMonsterDTO> monsters,
                                 String path, String pathNote, String contractNote) {
        return new NodeDTO(nodeType, story, monsters, null, path, pathNote, contractNote);
    }

    /** 조우 노드. 조우에는 길 효과가 없으므로 path는 비운다 */
    public static NodeDTO encounter(String nodeType, String story, EncounterDTO encounter,
                                    String pathNote, String contractNote) {
        return new NodeDTO(nodeType, story, null, encounter, null, pathNote, contractNote);
    }
}
