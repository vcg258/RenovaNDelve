package com.renovandelve.repository.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 의미 기반 게임 지식 검색 Tool (Tool Calling + RAG 결합).
 *
 * GameTools는 "이름을 아는 아이템"을 정확히 찾아준다(이름 -> 상세).
 * 이 Tool은 반대로 "이름을 모르는 질문"을 맡는다(설명 -> 후보).
 *   예) "출혈 빌드에 어울리는 거 뭐 있어?", "방어 위주로 가려면?", "불 쓰는 적은 누구야?"
 *
 * 검색 대상은 GameDataEtlService가 RDB에서 VectorStore로 동기화한 문서이며,
 * 메타데이터 필터로 카테고리를 좁혀 다른 종류의 문서가 섞이지 않게 한다.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTools {
    private static final Set<String> CATEGORIES =
            Set.of("monster", "weapon", "armor", "helmet", "skill", "relic", "consumable");
    private static final int TOP_K = 4;
    private static final double SIMILARITY_THRESHOLD = 0.3;

    private final VectorStore vectorStore;

    @Tool(description = """
            게임 데이터를 의미로 검색합니다. 사용자가 정확한 이름을 모르고 특징·용도·분위기로 물을 때 사용하세요.
            예: '출혈에 좋은 스킬', '방어력을 올려주는 유물', '독을 쓰는 몬스터', '체력이 낮을 때 쓸 만한 소모품'.
            정확한 아이템 이름을 이미 알고 있다면 이 도구 대신 이름 조회 도구를 쓰세요.""")
    public String searchGameKnowledge(
            @ToolParam(description = "찾고 싶은 특징을 설명하는 한국어 검색어. 예: '출혈 피해를 늘려주는 것'") String query,
            @ToolParam(description = "검색 범위. monster, weapon, armor, helmet, skill, relic, consumable 중 하나. 범위를 모르면 all",
                    required = false) String category) {
        if (query == null || query.isBlank()) {
            return "검색어가 비어 있습니다.";
        }

        // 모델이 넘긴 값을 그대로 필터 표현식에 넣지 않는다: 허용 목록에 있는 값만 사용
        String normalized = category == null ? "all" : category.trim().toLowerCase();
        String filter = CATEGORIES.contains(normalized)
                ? "source == 'rdb' && category == '" + normalized + "'"
                : "source == 'rdb'";

        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(TOP_K)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .filterExpression(filter)
                    .build());

            if (documents == null || documents.isEmpty()) {
                return "'%s'와(과) 관련된 게임 데이터를 찾지 못했습니다.".formatted(query);
            }
            return documents.stream()
                    .map(doc -> "[%s] %s".formatted(doc.getMetadata().getOrDefault("category", "기타"), doc.getText()))
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.warn("[KnowledgeSearchTools] 벡터 검색 실패: {}", e.getMessage());
            return "지금은 검색을 사용할 수 없습니다. 아이템 이름으로 다시 물어봐 주세요.";
        }
    }
}
