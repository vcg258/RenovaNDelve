package com.renovandelve.collection.controller;

import com.renovandelve.collection.dto.response.IllustratedEncyclopediaDTO;
import com.renovandelve.catalog.service.CatalogService;
import com.renovandelve.member.service.CurrentMemberService;
import com.renovandelve.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "도감", description = "회원이 획득한 장비 / 스킬 / 유물 / 소모품 도감")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/collection")
public class CollectionController {
    private final MemberService memberService;
    private final CatalogService catalogService;
    private final CurrentMemberService currentMemberService;

    /**
     * 내 도감: 지금까지 손에 넣은 장비·스킬·유물·소모품.
     *
     * @return 도감
     */
    @Operation(summary = "내 도감 전체 조회")
    @GetMapping
    public ResponseEntity<IllustratedEncyclopediaDTO> getCollection() {
        return ResponseEntity.ok(memberService.getEncyclopedia(currentMemberService.getCurrentMember()));
    }

    /**
     * 도감 분류별 전체 항목 수 (얻지 못한 칸을 "???"로 보여 주는 데 쓴다).
     *
     * @return weapon, armor, helmet, skill, relic, consumable 별 개수
     */
    @Operation(summary = "도감 전체 항목 수", description = "분류별 전체 개수. 수집률과 미발견 칸 표시에 씁니다.")
    @GetMapping("/totals")
    public ResponseEntity<java.util.Map<String, Long>> getTotals() {
        return ResponseEntity.ok(catalogService.getTotals());
    }
}
