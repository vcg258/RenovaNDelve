package com.renovandelve.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청.
 * 형식 검증은 Bean Validation으로 처리하고, 중복 검사는 서비스에서 수행한다.
 */
public record SignupRequest(

        @NotBlank(message = "아이디를 입력해주세요.")
        @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문, 숫자, 밑줄(_)만 사용할 수 있습니다.")
        String loginId,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
        String password,

        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.")
        String nickname,

        // 동의 여부는 화면 체크박스만 믿지 않고 서버에서도 검증한다 (API 직접 호출로 동의 없이 가입하는 것 방지)
        @NotNull(message = "이용약관에 동의해주세요.")
        @AssertTrue(message = "이용약관에 동의해주세요.")
        Boolean agreeTerms,

        @NotNull(message = "개인정보 수집·이용에 동의해주세요.")
        @AssertTrue(message = "개인정보 수집·이용에 동의해주세요.")
        Boolean agreePrivacy,

        // 만 14세 미만은 법정대리인 동의 절차가 필요한데 이 서비스는 그 절차를 두지 않으므로 가입을 받지 않는다
        @NotNull(message = "만 14세 이상만 가입할 수 있습니다.")
        @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
        Boolean ageConfirmed
) {
}
