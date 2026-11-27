package com.renovandelve.ranking.controller;

import com.renovandelve.ranking.dto.response.RankingViewDTO;
import com.renovandelve.ranking.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "랭킹", description = "난이도별 TOP 10 랭킹")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ranking")
public class RankingController {
    private final RankingService rankingService;

    /**
     * 난이도별 랭킹.
     *
     * @param difficulty EASY / NORMAL / HARD / HELL
     * @return 랭킹 목록
     */
    @Operation(summary = "난이도별 TOP 10 랭킹 조회")
    @GetMapping
    public ResponseEntity<List<RankingViewDTO>> getRanking(@RequestParam(defaultValue = "NORMAL") String difficulty) {
        return ResponseEntity.ok(rankingService.top10(difficulty));
    }
}
