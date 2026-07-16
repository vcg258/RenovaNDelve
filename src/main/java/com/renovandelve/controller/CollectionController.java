package com.renovandelve.controller;

import com.renovandelve.dto.response.IllustratedEncyclopediaDTO;
import com.renovandelve.service.CurrentMemberService;
import com.renovandelve.service.MemberService;
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
    private final CurrentMemberService currentMemberService;

    @Operation(summary = "내 도감 전체 조회")
    @GetMapping
    public ResponseEntity<IllustratedEncyclopediaDTO> getCollection() {
        return ResponseEntity.ok(memberService.getEncyclopedia(currentMemberService.getCurrentMember()));
    }
}
