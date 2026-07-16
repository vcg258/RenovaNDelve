package com.renovandelve.controller.ai;

import com.renovandelve.service.ai.GuideService;
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

    @Operation(summary = "가이드 챗봇 질문")
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        // 대화 기억은 로그인 사용자 단위로 유지
        String conversationId = "guide-" + SecurityContextHolder.getContext().getAuthentication().getName();
        return Map.of("answer", guideService.chat(question, conversationId));
    }
}
