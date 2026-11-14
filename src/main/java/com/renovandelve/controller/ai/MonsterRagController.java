package com.renovandelve.controller.ai;

import com.renovandelve.service.ai.MonsterRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "몬스터 RAG 관리자", description = "몬스터 출현 및 인카운터 선택지 생성")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/rag")
public class MonsterRagController {
    private final MonsterRagService monsterRagService; // 몬스터 출현

    /**
     * [개발용] 노드 하나를 AI로 만들어 본다.
     * 주의: 서비스의 인자 순서는 (스테이지, 노드)인데 여기서는 (노드, 스테이지)로 넘기고 있다.
     *
     * @param current_node_id 노드 번호
     * @param currentStage    스테이지
     * @param conversationId  대화 ID
     * @return 생성된 노드
     */
    @Operation(summary = "몬스터 출현 / 인카운터 선택지", description = "current_node_id = Stage, currentStage = Node, conversationId = UserId")
    @PostMapping
    public ResponseEntity<Object> chat(
            @RequestParam int current_node_id,
            @RequestParam int currentStage,
            @RequestParam String conversationId) {
        return ResponseEntity.ok(monsterRagService.chat(current_node_id, currentStage, conversationId));
    }
}
