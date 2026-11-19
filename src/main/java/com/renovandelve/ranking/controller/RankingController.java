package com.renovandelve.ranking.controller;

import com.renovandelve.ranking.mapper.RankingMapper;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.ranking.dto.response.RankingViewDTO;
import com.renovandelve.ranking.repository.RankingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "랭킹", description = "난이도별 TOP 10 랭킹")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ranking")
public class RankingController {
    private final RankingRepository rankingRepository;
    private final RankingMapper rankingMapper;

    /**
     * 난이도별 랭킹.
     *
     * @param difficulty EASY / NORMAL / HARD / HELL
     * @return 랭킹 목록
     * @throws IllegalArgumentException 알 수 없는 난이도면
     */
    @Operation(summary = "난이도별 TOP 10 랭킹 조회")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<RankingViewDTO>> getRanking(@RequestParam(defaultValue = "NORMAL") String difficulty) {
        Difficulty parsed;
        try {
            parsed = Difficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 난이도: " + difficulty);
        }

        List<RankingViewDTO> result = new ArrayList<>();
        int rank = 1;
        for (var entity : rankingRepository.findTop10ByDifficultyOrderByBestScoreDesc(parsed)) {
            result.add(rankingMapper.toViewDTO(entity, rank++));
        }
        return ResponseEntity.ok(result);
    }
}
