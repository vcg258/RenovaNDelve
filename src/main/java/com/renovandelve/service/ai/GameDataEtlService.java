package com.renovandelve.service.ai;

import com.renovandelve.domain.MonsterEntity;
import com.renovandelve.domain.armorentity.ArmorEntity;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import com.renovandelve.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;

/**
 * RDB -> VectorStore 증분 ETL 파이프라인.
 *
 * 기존에는 관리자가 세계관 문서를 직접 업로드해야 RAG가 동작했지만,
 * 게임 데이터의 단일 진실 공급원(Single Source of Truth)은 어차피 RDB이므로
 * RDB를 그대로 임베딩 소스로 사용한다.
 *
 * 핵심 설계
 * - 결정적 문서 ID: "monster-12" 같은 키를 UUID v3로 변환해 행 1건 = 문서 1건으로 고정
 * - 내용 해시 비교: 바뀐 행만 다시 임베딩 -> OpenAI 임베딩 호출 비용/시간 최소화
 * - 삭제 동기화: RDB에서 사라진 행의 벡터 문서도 함께 제거 -> 정합성 유지
 * - 기동 시 자동 실행(비동기) + 관리자 API로 수동/강제 재동기화 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameDataEtlService {

    public static final String SOURCE = "rdb";
    private static final String META_SOURCE = "source";
    private static final String META_CATEGORY = "category";
    private static final String META_KEY = "docKey";
    private static final String META_HASH = "hash";

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    private final MonsterRepository monsterRepository;
    private final WeaponRepository weaponRepository;
    private final ArmorRepository armorRepository;
    private final HelmetRepository helmetRepository;
    private final SkillRepository skillRepository;
    private final RelicRepository relicRepository;
    private final ConsumableRepository consumableRepository;

    @Value("${renovaNDelve.ai.sync-on-startup:true}")
    private boolean syncOnStartup;

    /**
     * 서버 기동 완료 후 비동기 1회 동기화.
     * 임베딩 API 실패 등 어떤 예외도 서버 기동을 막지 않는다.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!syncOnStartup) {
            log.info("[GameDataETL] 기동 시 자동 동기화 비활성화 (renovaNDelve.ai.sync-on-startup=false)");
            return;
        }
        try {
            log.info("[GameDataETL] {}", sync(false));
        } catch (Exception e) {
            log.warn("[GameDataETL] 기동 시 동기화 실패 (게임은 랜덤 폴백으로 정상 동작): {}", e.getMessage());
        }
    }

    /**
     * RDB 전체를 VectorStore와 동기화한다.
     * @param force true면 내용 변경 여부와 무관하게 전량 재임베딩
     * @return 처리 결과 요약
     */
    @Transactional(readOnly = true)
    public String sync(boolean force) {
        long start = System.currentTimeMillis();

        // 1) RDB에서 임베딩 대상 문서 구성
        List<Document> desired = buildAllDocuments();
        Map<String, Document> desiredByKey = new LinkedHashMap<>();
        for (Document doc : desired) {
            desiredByKey.put((String) doc.getMetadata().get(META_KEY), doc);
        }

        // 2) VectorStore에 이미 적재된 (docKey -> hash) 조회
        Map<String, String> existing = force ? Map.of() : loadExistingHashes();

        // 3) 신규 / 변경 / 삭제 판별
        List<Document> toUpsert = new ArrayList<>();
        for (Map.Entry<String, Document> entry : desiredByKey.entrySet()) {
            String previousHash = existing.get(entry.getKey());
            String currentHash = (String) entry.getValue().getMetadata().get(META_HASH);
            if (previousHash == null || !previousHash.equals(currentHash)) {
                toUpsert.add(entry.getValue());
            }
        }
        List<String> removedKeys = existing.keySet().stream()
                .filter(key -> !desiredByKey.containsKey(key))
                .toList();

        // 4) 반영 (변경분은 삭제 후 재적재 = upsert)
        List<String> deleteIds = new ArrayList<>();
        toUpsert.forEach(doc -> deleteIds.add(doc.getId()));
        removedKeys.forEach(key -> deleteIds.add(documentId(key)));

        if (!deleteIds.isEmpty()) {
            try {
                vectorStore.delete(deleteIds);
            } catch (Exception e) {
                log.warn("[GameDataETL] 기존 문서 삭제 중 경고 (계속 진행): {}", e.getMessage());
            }
        }
        if (!toUpsert.isEmpty()) {
            vectorStore.add(toUpsert); // 내부 배치 임베딩
        }

        long elapsed = System.currentTimeMillis() - start;
        String summary = "RDB 동기화 완료 — 대상 %d건 / 신규·변경 %d건 임베딩 / 삭제 %d건 / 변경없음 %d건 (%dms)"
                .formatted(desired.size(), toUpsert.size(), removedKeys.size(),
                        desired.size() - toUpsert.size(), elapsed);
        log.info("[GameDataETL] {}", summary);
        return summary;
    }

    /**
     * 벡터 스토어의 RDB 기반 문서를 전부 제거한다. (재구축 전 정리용)
     */
    public String purge() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'" + META_SOURCE + "' = ?", SOURCE);
        log.info("[GameDataETL] RDB 문서 {}건 삭제", deleted);
        return "VectorStore에서 RDB 기반 문서 %d건을 삭제했습니다.".formatted(deleted);
    }

    /* ==================== 문서 구성 ==================== */

    /**
     * RDB의 게임 데이터 7종(몬스터·무기·갑옷·투구·스킬·유물·소모품)을 벡터 문서로 만든다.
     *
     * @return 문서 목록
     */
    private List<Document> buildAllDocuments() {
        List<Document> documents = new ArrayList<>();

        documents.addAll(build("monster", monsterRepository.findAll(),
                MonsterEntity::getMonsterId, this::monsterText));
        documents.addAll(build("weapon", weaponRepository.findAll(),
                WeaponEntity::getWeaponId, this::weaponText));
        documents.addAll(build("armor", armorRepository.findAll(),
                ArmorEntity::getArmorId, this::armorText));
        documents.addAll(build("helmet", helmetRepository.findAll(),
                HelmetEntity::getHelmetId, this::helmetText));
        documents.addAll(build("skill", skillRepository.findAll(),
                SkillEntity::getSkillId, this::skillText));
        documents.addAll(build("relic", relicRepository.findAll(),
                RelicEntity::getRelicId, this::relicText));
        documents.addAll(build("consumable", consumableRepository.findAll(),
                ConsumableEntity::getConsumableId, this::consumableText));

        return documents;
    }

    /**
     * 엔티티 목록을 벡터 문서로 만든다. 문서 ID는 "카테고리-행ID"에서 결정적으로 만들고,
     * 내용 해시를 메타데이터에 넣어 다음 동기화 때 바뀐 행만 다시 임베딩하게 한다.
     *
     * @param category    카테고리 (메타데이터 필터에 쓴다)
     * @param entities    엔티티 목록
     * @param idExtractor 행 ID 꺼내기
     * @param textBuilder 임베딩할 문장 만들기
     * @param <T>         엔티티 타입
     * @return 문서 목록
     */
    private <T> List<Document> build(String category, List<T> entities,
                                     Function<T, Long> idExtractor, Function<T, String> textBuilder) {
        List<Document> documents = new ArrayList<>(entities.size());
        for (T entity : entities) {
            String docKey = category + "-" + idExtractor.apply(entity);
            String content = textBuilder.apply(entity);
            String hash = sha256(content);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_SOURCE, SOURCE);
            metadata.put(META_CATEGORY, category);
            metadata.put(META_KEY, docKey);
            metadata.put(META_HASH, hash);
            metadata.put("refId", String.valueOf(idExtractor.apply(entity)));

            documents.add(new Document(documentId(docKey), content, metadata));
        }
        return documents;
    }

    /* ---------- 카테고리별 임베딩 텍스트 ---------- */
    // RAG 검색이 '장소/분위기/전투 특성' 키워드로 이뤄지므로 설명 원문을 살려서 구성한다.

    /**
     * 몬스터 한 행을 임베딩할 문장으로 만든다. 검색이 장소·분위기·특성 키워드로 이뤄지므로 설명 원문을 살린다.
     *
     * @param m 몬스터
     * @return 임베딩할 문장
     */
    private String monsterText(MonsterEntity m) {
        String grade = switch (m.getMonsterType()) {
            case 2 -> "보스 몬스터"; case 1 -> "엘리트 몬스터"; default -> "일반 몬스터";
        };
        return """
                [몬스터] %s
                등급: %s
                특징과 서식 환경: %s
                """.formatted(m.getName(), grade, nullSafe(m.getDescription()));
    }

    /**
     * 무기 한 행을 임베딩할 문장으로 만든다. 검색이 장소·분위기·특성 키워드로 이뤄지므로 설명 원문을 살린다.
     *
     * @param w 무기
     * @return 임베딩할 문장
     */
    private String weaponText(WeaponEntity w) {
        return """
                [무기] %s
                종류: %s / 추가 공격력: %d / 스타팅 포인트 비용: %d
                설명: %s
                """.formatted(w.getName(), w.getType(), w.getAtkBonus(), w.getScoreCost(), nullSafe(w.getDescription()));
    }

    /**
     * 갑옷 한 행을 임베딩할 문장으로 만든다. 검색이 장소·분위기·특성 키워드로 이뤄지므로 설명 원문을 살린다.
     *
     * @param a 갑옷
     * @return 임베딩할 문장
     */
    private String armorText(ArmorEntity a) {
        return """
                [갑옷] %s
                종류: %s / 추가 방어력: %d / 스타팅 포인트 비용: %d
                설명: %s
                """.formatted(a.getName(), a.getType(), a.getDefBonus(), a.getScoreCost(), nullSafe(a.getDescription()));
    }

    /**
     * 투구 한 행을 임베딩할 문장으로 만든다. 검색이 장소·분위기·특성 키워드로 이뤄지므로 설명 원문을 살린다.
     *
     * @param h 투구
     * @return 임베딩할 문장
     */
    private String helmetText(HelmetEntity h) {
        return """
                [투구] %s
                종류: %s / 추가 체력: %d / 스타팅 포인트 비용: %d
                설명: %s
                """.formatted(h.getName(), h.getType(), h.getHpBonus(), h.getScoreCost(), nullSafe(h.getDescription()));
    }

    /**
     * 스킬 한 행을 임베딩할 문장으로 만든다. 검색이 장소·분위기·특성 키워드로 이뤄지므로 설명 원문을 살린다.
     *
     * @param s 스킬
     * @return 임베딩할 문장
     */
    private String skillText(SkillEntity s) {
        String effect = switch (s.getStatusEffect()) {
            case 1 -> "출혈"; case 2 -> "화상"; case 3 -> "독"; case 4 -> "기절"; default -> "없음";
        };
        return """
                [스킬] %s
                대상: %s / 타격 횟수: %d / 공격력 계수: %s / 쿨타임: %d턴
                부여 상태이상: %s(%d스택) / 스타팅 포인트 비용: %d
                설명: %s
                """.formatted(s.getName(), s.getTarget(), s.getAttackCount(), s.getCoefficient(),
                s.getCooldown(), effect, s.getEffectStack(), s.getCost(), nullSafe(s.getDescription()));
    }

    /**
     * 유물 한 행을 임베딩할 문장으로 만든다. 검색이 장소·분위기·특성 키워드로 이뤄지므로 설명 원문을 살린다.
     *
     * @param r 유물
     * @return 임베딩할 문장
     */
    private String relicText(RelicEntity r) {
        return """
                [유물] %s
                희귀도: %s / 스타팅 포인트 비용: %d
                효과: %s
                """.formatted(r.getName(), r.getRarity(), r.getScoreCost(), nullSafe(r.getEffectDesc()));
    }

    /**
     * 소모품 한 행을 임베딩할 문장으로 만든다. 검색이 장소·분위기·특성 키워드로 이뤄지므로 설명 원문을 살린다.
     *
     * @param c 소모품
     * @return 임베딩할 문장
     */
    private String consumableText(ConsumableEntity c) {
        return """
                [소모품] %s
                효과 타입: %s / 효과 수치: %d / 스타팅 포인트 비용: %d
                설명: %s
                """.formatted(c.getName(), c.getEffectType(), c.getEffectValue(), c.getScoreCost(), nullSafe(c.getDescription()));
    }

    /* ==================== HELPER METHODS ==================== */

    /**
     * 이미 적재된 RDB 문서의 (docKey -> hash) 맵.
     * vector_store 테이블을 직접 조회한다 (유사도 검색이 아니라 메타데이터 조회이므로 SQL이 정확·저렴).
     */
    private Map<String, String> loadExistingHashes() {
        try {
            Map<String, String> result = new HashMap<>();
            jdbcTemplate.query(
                    "SELECT metadata->>'" + META_KEY + "' AS doc_key, metadata->>'" + META_HASH + "' AS hash " +
                            "FROM vector_store WHERE metadata->>'" + META_SOURCE + "' = ?",
                    rs -> {
                        String key = rs.getString("doc_key");
                        if (key != null) result.put(key, rs.getString("hash"));
                    }, SOURCE);
            return result;
        } catch (Exception e) {
            // vector_store 테이블이 아직 없거나(최초 기동) 조회 실패 -> 전량 적재로 처리
            log.warn("[GameDataETL] 기존 문서 조회 실패, 전량 적재로 진행: {}", e.getMessage());
            return Map.of();
        }
    }

    /** docKey -> 결정적 UUID (vector_store의 id 컬럼이 UUID 타입이므로) */
    private String documentId(String docKey) {
        return UUID.nameUUIDFromBytes(("renova:" + docKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 내용 해시 (SHA-256 앞 128비트). 바뀐 행을 찾는 데 쓴다.
     *
     * @param text 문서 내용
     * @return 16진수 해시
     */
    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.substring(0, 32); // 앞 128비트면 충돌 걱정 없이 충분
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    /**
     * 비어 있으면 "정보 없음"으로 바꾼다.
     *
     * @param text 원문
     * @return 원문 또는 "정보 없음"
     */
    private String nullSafe(String text) {
        return text == null || text.isBlank() ? "정보 없음" : text;
    }
}
