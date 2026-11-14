package com.renovandelve.controller.ai;

import com.renovandelve.dto.request.GuideQuestionRequest;
import com.renovandelve.service.PersonalDataService;
import com.renovandelve.service.ai.GuideService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "유저 가이드", description = "게임 가이드 LLM 챗봇 (Tools 기반)")
public class GuideController {
    private final GuideService guideService;
    private final PersonalDataService personalDataService;

    /**
     * 가이드 챗봇에 묻는다. 대화 기억은 로그인 사용자 단위로 유지한다.
     *
     * @param request 질문 (300자 이내)
     * @return 답변
     */
    @Operation(summary = "가이드 챗봇 질문")
    @PostMapping
    public Map<String, String> chat(@Valid @RequestBody GuideQuestionRequest request) {
        // 대화 기억은 로그인 사용자 단위로 유지
        String conversationId = PersonalDataService.guideConversationId(
                SecurityContextHolder.getContext().getAuthentication().getName());
        return Map.of("answer", guideService.chat(request.question(), conversationId));
    }

    /**
     * 서버에 저장된 내 가이드 대화 기록을 지운다.
     *
     * @return 204
     */
    @Operation(summary = "가이드 챗봇 대화 기록 삭제", description = "서버에 저장된 내 대화 기록을 즉시 삭제합니다.")
    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        personalDataService.eraseGuideConversation(
                SecurityContextHolder.getContext().getAuthentication().getName());
        return ResponseEntity.noContent().build();
    }
}
