package com.renovandelve.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스토리 응답에서 키워드 줄을 떼어내는 규칙.
 * 키워드를 스토리와 한 번에 받으면서 LLM 호출이 1회 줄었으므로, 모델이 형식을 어겼을 때의 동작이 중요하다.
 */
class MonsterRagSceneTest {

    @Test
    @DisplayName("마지막 키워드 줄을 본문에서 떼어낸다")
    void splitsStoryAndKeywords() {
        var scene = MonsterRagService.parseScene("""
                무너진 정문 너머로 흰 빛이 걸려 있다.
                발밑에서는 낮은 속삭임이 올라온다.

                키워드: 정문, 폐허, 흰 빛, 속삭임
                """);

        assertThat(scene.story()).isEqualTo("무너진 정문 너머로 흰 빛이 걸려 있다.\n발밑에서는 낮은 속삭임이 올라온다.");
        assertThat(scene.keywords()).isEqualTo("정문, 폐허, 흰 빛, 속삭임");
    }

    @Test
    @DisplayName("키워드 줄이 없으면 본문만 돌려주고 keywords는 null (호출하는 쪽이 따로 추출한다)")
    void missingKeywordLine() {
        var scene = MonsterRagService.parseScene("연무장에 녹슨 갑옷들이 서 있다.");

        assertThat(scene.story()).isEqualTo("연무장에 녹슨 갑옷들이 서 있다.");
        assertThat(scene.keywords()).isNull();
    }

    @Test
    @DisplayName("키워드 줄 뒤에 덧붙은 말은 버린다")
    void ignoresTextAfterKeywordLine() {
        var scene = MonsterRagService.parseScene("탑의 계단이 끝없이 이어진다.\n키워드: 탑, 계단\n이상입니다.");

        assertThat(scene.story()).isEqualTo("탑의 계단이 끝없이 이어진다.");
        assertThat(scene.keywords()).isEqualTo("탑, 계단");
    }

    @Test
    @DisplayName("키워드 머리말만 있고 내용이 비면 keywords는 null")
    void emptyKeywords() {
        var scene = MonsterRagService.parseScene("지하 묘지에 물이 고여 있다.\n키워드:");

        assertThat(scene.story()).isEqualTo("지하 묘지에 물이 고여 있다.");
        assertThat(scene.keywords()).isNull();
    }

    @Test
    @DisplayName("본문 없이 키워드만 온 응답은 통째로 본문으로 본다")
    void keywordsOnly() {
        var scene = MonsterRagService.parseScene("키워드: 정원, 덩굴");

        assertThat(scene.story()).isEqualTo("키워드: 정원, 덩굴");
        assertThat(scene.keywords()).isNull();
    }

    @Test
    @DisplayName("null 응답은 빈 본문")
    void nullResponse() {
        assertThat(MonsterRagService.parseScene(null).story()).isEmpty();
    }
}
