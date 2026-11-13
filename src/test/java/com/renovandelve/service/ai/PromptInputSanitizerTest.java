package com.renovandelve.service.ai;

import com.renovandelve.engine.PathChoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptInputSanitizerTest {

    @Test
    @DisplayName("전화번호·이메일·주민등록번호·카드번호는 외부 전송 전에 가려진다")
    void masksPersonalData() {
        String masked = PromptInputSanitizer.maskPersonalData(
                "내 번호는 010-1234-5678이고 메일은 hero@example.com, 주민번호 900101-1234567, 카드 1234-5678-9012-3456");

        assertThat(masked)
                .doesNotContain("010-1234-5678")
                .doesNotContain("hero@example.com")
                .doesNotContain("900101-1234567")
                .doesNotContain("1234-5678-9012-3456")
                .contains("[개인정보 가림]");
    }

    @Test
    @DisplayName("게임 질문에 나오는 숫자는 건드리지 않는다")
    void keepsGameNumbers() {
        String question = "공격력 35에 방어력 12인데 HP 120/180이면 스테이지 3 보스 잡을 수 있어?";

        assertThat(PromptInputSanitizer.maskPersonalData(question)).isEqualTo(question);
    }

    @Test
    @DisplayName("null 입력은 빈 문자열로 처리한다")
    void nullSafe() {
        assertThat(PromptInputSanitizer.maskPersonalData(null)).isEmpty();
    }

    @Test
    @DisplayName("갈림길: 알 수 없는 값은 기본 길로 처리하고, 위험한 길만 보상과 적이 함께 오른다")
    void pathChoiceParsing() {
        assertThat(PathChoice.parse(null)).isEqualTo(PathChoice.SAFE);
        assertThat(PathChoice.parse("")).isEqualTo(PathChoice.SAFE);
        assertThat(PathChoice.parse("GODMODE")).isEqualTo(PathChoice.SAFE);
        assertThat(PathChoice.parse("danger")).isEqualTo(PathChoice.DANGER);

        assertThat(PathChoice.DANGER.enemyMultiplier()).isGreaterThan(1.0);
        assertThat(PathChoice.DANGER.rewardMultiplier()).isGreaterThan(1.0);
        assertThat(PathChoice.AMBUSH.ambush()).isTrue();
        assertThat(PathChoice.AMBUSH.rewardMultiplier()).isLessThan(1.0);
    }
}
