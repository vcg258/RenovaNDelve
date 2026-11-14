package com.renovandelve.ai.controller;

import com.renovandelve.ai.service.GameDataEtlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RDB -> VectorStore ETL 관리 API.
 * 기동 시 자동 동기화되므로 평소에는 호출할 일이 없고,
 * 게임 데이터(몬스터/장비/스킬/유물)를 수정한 직후 즉시 반영할 때 사용한다.
 */
@Tag(name = "ETL 관리자", description = "RDB 게임 데이터를 PGVector에 동기화")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/etl")
public class EtlController {
    private final GameDataEtlService gameDataEtlService;

    @Operation(summary = "RDB -> VectorStore 동기화",
            description = "변경된 행만 다시 임베딩합니다. force=true면 전량 재임베딩(임베딩 비용 발생).")
    /**
     * RDB 게임 데이터를 VectorStore와 동기화한다. 바뀐 행만 다시 임베딩한다.
     *
     * @param force true면 전부 다시 임베딩
     * @return 동기화 결과 요약
     */
    @PostMapping("/sync")
    public ResponseEntity<String> sync(@RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(gameDataEtlService.sync(force));
    }

    /**
     * VectorStore에서 RDB로 만든 문서를 모두 지운다 (재구축 전 정리).
     *
     * @return 삭제 결과 요약
     */
    @Operation(summary = "VectorStore의 RDB 문서 전체 삭제", description = "재구축 전 정리용")
    @DeleteMapping("/purge")
    public ResponseEntity<String> purge() {
        return ResponseEntity.ok(gameDataEtlService.purge());
    }
}
