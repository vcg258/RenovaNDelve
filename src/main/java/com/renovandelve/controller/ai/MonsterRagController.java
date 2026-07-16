package com.renovandelve.controller.ai;

import com.renovandelve.service.ai.MonsterRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "몬스터 RAG 관리자", description = "몬스터 출현 및 인카운터 선택지 생성")
@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/rag")
public class MonsterRagController {
    private final MonsterRagService monsterRagService; // 몬스터 출현

    @Operation(summary = "몬스터 출현 / 인카운터 선택지", description = "current_node_id = Stage, currentStage = Node, conversationId = UserId")
    @PostMapping
    public ResponseEntity<Object> chat(
            @RequestParam int current_node_id,
            @RequestParam int currentStage,
            @RequestParam String conversationId) {
        return ResponseEntity.ok(monsterRagService.chat(current_node_id, currentStage, conversationId));
    }
}
