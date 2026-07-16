package com.renovandelve.service.ai;

import com.renovandelve.domain.MonsterEntity;
import com.renovandelve.repository.MonsterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RDB(monster 테이블) -> VectorStore 자동 동기화 ETL.
 *
 * 기존에는 관리자가 세계관 문서를 파일 업로드해야만 RAG가 동작했지만,
 * 몬스터의 '단일 진실 공급원'은 어차피 RDB이므로 기동 시 자동으로 임베딩해 넣는다.
 * - 벡터 문서에 source=rdb-monster 메타데이터를 달아 파일 ETL 문서와 공존
 * - 이미 동기화되어 있으면 스킵 (force로 강제 재동기화 가능)
 * - 파일 업로드 ETL(EtlService)은 세계관/지역 문서용으로 그대로 유지
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MonsterVectorEtlService {
    public static final String SOURCE_KEY = "source";
    public static final String SOURCE_RDB_MONSTER = "rdb-monster";
    private static final String[] TYPE_LABEL = {"일반 몬스터", "엘리트 몬스터", "보스 몬스터"};

    private final VectorStore vectorStore;
    private final MonsterRepository monsterRepository;

    @Value("${renovaNDelve.ai.sync-monsters-on-startup:true}")
    private boolean syncOnStartup;

    /**
     * 서버 기동 완료 후 비동기로 1회 동기화 시도.
     * (임베딩 API 실패 등 어떤 예외도 서버 기동을 막지 않는다)
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!syncOnStartup) {
            log.info("[MonsterVectorETL] 기동 시 동기화 비활성화됨 (renovaNDelve.ai.sync-monsters-on-startup=false)");
            return;
        }
        try {
            String result = syncMonstersFromRdb(false);
            log.info("[MonsterVectorETL] {}", result);
        } catch (Exception e) {
            log.warn("[MonsterVectorETL] 기동 시 몬스터 벡터 동기화 실패 (게임은 랜덤 폴백으로 동작): {}", e.getMessage());
        }
    }

    /**
     * monster 테이블 전체를 임베딩하여 VectorStore에 적재.
     * @param force true면 기존 몬스터 문서를 지우고 다시 적재
     * @return 처리 결과 메시지
     */
    public String syncMonstersFromRdb(boolean force) {
        if (!force && hasMonsterDocuments()) {
            return "몬스터 벡터 문서가 이미 존재하여 동기화를 건너뜁니다. (강제 재동기화: force=true)";
        }

        // 기존 몬스터 문서 제거 (중복 적재 방지)
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            vectorStore.delete(builder.eq(SOURCE_KEY, SOURCE_RDB_MONSTER).build());
        } catch (Exception e) {
            log.warn("[MonsterVectorETL] 기존 문서 삭제 실패 (계속 진행): {}", e.getMessage());
        }

        List<MonsterEntity> monsters = monsterRepository.findAll();
        if (monsters.isEmpty()) {
            return "RDB에 몬스터 데이터가 없습니다. data.sql 시드를 확인하세요.";
        }

        List<Document> documents = monsters.stream()
                .map(this::toDocument)
                .toList();

        vectorStore.add(documents); // 내부적으로 배치 임베딩 처리
        log.info("[MonsterVectorETL] 몬스터 {}건 임베딩 완료", documents.size());
        return "RDB 몬스터 %d건을 VectorStore에 동기화했습니다.".formatted(documents.size());
    }

    /* HELPER METHODS */

    private Document toDocument(MonsterEntity monster) {
        String typeLabel = monster.getMonsterType() >= 0 && monster.getMonsterType() < TYPE_LABEL.length
                ? TYPE_LABEL[monster.getMonsterType()]
                : "미분류";

        // RAG 검색 품질을 위해 '장소/분위기 키워드'가 살아있는 설명 중심으로 구성
        String content = """
                몬스터 이름: %s
                등급: %s
                특징과 서식 환경: %s
                """.formatted(monster.getName(), typeLabel,
                monster.getDescription() == null ? "정보 없음" : monster.getDescription());

        return new Document(content, Map.of(
                SOURCE_KEY, SOURCE_RDB_MONSTER,
                "monsterId", String.valueOf(monster.getMonsterId()),
                "name", monster.getName(),
                "monsterType", (int) monster.getMonsterType()
        ));
    }

    private boolean hasMonsterDocuments() {
        try {
            List<Document> probe = vectorStore.similaritySearch(SearchRequest.builder()
                    .query("몬스터")
                    .topK(1)
                    .filterExpression(SOURCE_KEY + " == '" + SOURCE_RDB_MONSTER + "'")
                    .build());
            return probe != null && !probe.isEmpty();
        } catch (Exception e) {
            log.warn("[MonsterVectorETL] 기존 문서 확인 실패: {}", e.getMessage());
            return false;
        }
    }
}
