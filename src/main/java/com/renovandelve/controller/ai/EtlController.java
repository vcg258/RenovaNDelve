package com.renovandelve.controller.ai;

import com.renovandelve.service.ai.EtlService;
import com.renovandelve.service.ai.MonsterVectorEtlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "ETL 관리자", description = "VectorStore 적재: 세계관 문서 업로드 + RDB 몬스터 동기화")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/etl")
public class EtlController {
    private final EtlService etlService;
    private final MonsterVectorEtlService monsterVectorEtlService;

    @Operation(summary = "세계관 문서 업로드 -> 청크 분할 -> PGVector 적재")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@RequestPart("file") MultipartFile file) throws IOException {
        String result = etlService.etlFormFile(file);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "RDB 몬스터 -> PGVector 수동 동기화", description = "서버 기동 시 자동 실행되지만, 몬스터 데이터 변경 후 수동 재동기화할 때 사용")
    @PostMapping("/sync-monsters")
    public ResponseEntity<String> syncMonsters(@RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(monsterVectorEtlService.syncMonstersFromRdb(force));
    }
}
