package com.renovandelve.game.domain;

import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.engine.Faction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 전생의 망령. 패배한 원정의 마지막 모습이며, 다음 원정에서 같은 스테이지의 같은 자리에 적으로 나타난다.
 *
 * 능력치는 저장하지 않는다. 만날 때 서버가 그 스테이지·난이도 기준치로 정한다(필드 강도 시스템과 같은 원칙).
 * 저장하는 것은 연출과 보상에 쓰는 정보뿐이다: 쓰러진 자리, 레벨, 주력 스킬 이름, 가졌던 유물, 내렸던 선택.
 */
@Getter
@Entity
@Table(name = "wraith", indexes = @Index(name = "idx_wraith_member_status", columnList = "member_id, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WraithEntity {

    /** 망령의 상태 */
    public enum Status {
        /** 다음 원정에서 기다리는 중 */
        ACTIVE,
        /** 처치됨 */
        SLAIN,
        /** 새 망령에 밀려났거나, 세 번 놓쳐 흩어짐 */
        FADED
    }

    /** 이만큼 달아나면 망령이 흩어진다 */
    public static final int MAX_ESCAPES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long wraithId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long sourceGameStateId; // 이 망령을 남긴 원정

    @Column(nullable = false)
    private short stage;            // 쓰러진 스테이지

    @Column(nullable = false)
    private short node;             // 나타날 노드 (쓰러진 노드를 2~9로 맞춘 값)

    @Column(nullable = false)
    private int level;

    @Column(length = 100)
    private String killerName;      // 쓰러뜨린 적 (조우에서 쓰러졌으면 null)

    @Column(length = 500)
    private String skills;          // 주력 스킬 이름, 쉼표 구분

    @Column(length = 200)
    private String relicIds;        // 가졌던 유물 ID, 쉼표 구분 (처치 보상 후보)

    @Column(length = 10)
    private String faction;         // 기울었던 세력 (없으면 null)

    @Column(columnDefinition = "text")
    private String memories;        // 그 원정에서 내린 선택, 줄바꿈 구분

    @Column(nullable = false)
    private int escapes;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Status status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder
    private WraithEntity(Long memberId, Long sourceGameStateId, int stage, int node, int level, String killerName,
                         List<String> skills, List<Long> relicIds, String faction, List<String> memories) {
        this.memberId = memberId;
        this.sourceGameStateId = sourceGameStateId;
        this.stage = (short) stage;
        this.node = (short) node;
        this.level = level;
        this.killerName = killerName;
        this.skills = skills == null ? null : String.join(",", skills);
        this.relicIds = relicIds == null ? null
                : String.join(",", relicIds.stream().map(String::valueOf).toList());
        this.faction = faction;
        this.memories = memories == null ? null : String.join("\n", memories);
        this.status = Status.ACTIVE;
    }

    /**
     * 원정이 쓰러진 자리에 망령을 남긴다. 회원·원정·스테이지·세력은 원정에서 읽고,
     * 나타날 노드는 쓰러진 노드에서 정한다({@link #appearNode(int)}).
     *
     * @param gameState  쓰러진 원정
     * @param killerName 쓰러뜨린 몬스터
     * @param level      쓰러질 때의 레벨
     * @param skills     망령이 쓸 기술 이름
     * @param relicIds   망령을 쓰러뜨리면 돌아올 유물 ID
     * @param memories   선택 기록 문장
     * @return 나타날 차례를 기다리는(ACTIVE) 망령
     */
    public static WraithEntity fromDeath(GameStateEntity gameState, String killerName, int level,
                                         List<String> skills, List<Long> relicIds, List<String> memories) {
        int deathNode = gameState.getCurrentNodeId() == null ? 1 : gameState.getCurrentNodeId().intValue();
        Faction dominant = gameState.alignment().dominant();
        return WraithEntity.builder()
                .memberId(gameState.getMember().getMemberId())
                .sourceGameStateId(gameState.getGameStateId())
                .stage(gameState.getCurrentStage())
                .node(appearNode(deathNode))
                .level(level)
                .killerName(killerName)
                .skills(skills)
                .relicIds(relicIds)
                .faction(dominant == null ? null : dominant.name())
                .memories(memories)
                .build();
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 쓰러진 노드를 망령이 나타날 노드로 바꾼다. 첫 노드와 보스 노드(10)는 피한다. */
    public static int appearNode(int deathNode) {
        return Math.max(2, Math.min(9, deathNode));
    }

    /** @return 다음 원정에서 기다리는 중이면 true */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** 새 망령에 자리를 내준다 */
    public void fade() {
        this.status = Status.FADED;
    }

    /** 처치됐다 */
    public void slay() {
        this.status = Status.SLAIN;
    }

    /**
     * 플레이어가 망령에게서 달아났다. 세 번째면 흩어진다.
     *
     * @return 이번에 흩어졌으면 true
     */
    public boolean escapeFrom() {
        this.escapes++;
        if (escapes >= MAX_ESCAPES) {
            this.status = Status.FADED;
            return true;
        }
        return false;
    }

    /** @return 앞으로 더 달아날 수 있는 횟수 (다 쓰면 흩어진다) */
    public int escapesLeft() {
        return Math.max(0, MAX_ESCAPES - escapes);
    }

    /** @return 주력 스킬 이름 목록 */
    public List<String> skillNames() {
        return split(skills, ",");
    }

    /** @return 가졌던 유물 ID 목록 */
    public List<Long> relicIdList() {
        return split(relicIds, ",").stream().map(Long::valueOf).toList();
    }

    /** @return 그 원정에서 내린 선택 */
    public List<String> memoryLines() {
        return split(memories, "\n");
    }

    private static List<String> split(String value, String separator) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(separator)).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
