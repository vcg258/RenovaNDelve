package com.renovandelve.game.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 다음 노드 전진 요청. path = SAFE(기본) / DANGER(위험한 길) / AMBUSH(은밀한 길)
 * 본문 없이 호출해도 된다 (SAFE로 처리).
 */
public record NextNodeRequest(
        @Size(max = 20) String path
) {
}
