package com.renovandelve.auth.config;

import com.renovandelve.auth.filter.ApiLoginFilter;
import com.renovandelve.auth.filter.RefreshTokenFilter;
import com.renovandelve.auth.filter.TokenCheckFilter;
import com.renovandelve.auth.handler.LoginAttemptService;
import com.renovandelve.auth.handler.LoginFailureHandler;
import com.renovandelve.auth.handler.LoginSuccessHandler;
import com.renovandelve.auth.handler.OAuth2SuccessHandler;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.service.MemberDetailsService;
import com.renovandelve.auth.service.Oauth2UserService;
import com.renovandelve.auth.util.JwtUtil;
import com.renovandelve.auth.util.RefreshCookieFactory;
import com.renovandelve.auth.filter.AuthRateLimitFilter;
import com.renovandelve.global.redis.RateLimitService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtUtil jwtUtil;
    private final MemberDetailsService memberDetailsService;
    private final SecurityTokenService securityTokenService;
    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final RateLimitService rateLimitService;
    private final TokenRevocationService tokenRevocationService;

    /** 화면 응답에 붙는 콘텐츠 보안 정책 */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /**
     * 보안 필터 체인을 구성한다.
     * 세션을 쓰지 않는 JWT 방식이며, 요청 제한 → 로그인 → 재발급 → 토큰 검사 순으로 필터를 건다.
     * /api/admin/** 은 ADMIN 권한만, 인증 API·Swagger·OAuth 경로는 공개한다.
     *
     * @param httpSecurity     보안 설정 빌더
     * @param oauth2UserService 구글 로그인 사용자 로더
     * @return 필터 체인
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity, Oauth2UserService oauth2UserService) throws Exception {

        AuthenticationManagerBuilder authenticationManager =
                httpSecurity.getSharedObject(AuthenticationManagerBuilder.class);

        authenticationManager
                .userDetailsService(memberDetailsService)
                .passwordEncoder(passwordEncoder());

        AuthenticationManager manager = authenticationManager.build();
        httpSecurity.authenticationManager(manager);

        httpSecurity
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .csrf(csrf -> csrf.disable())     // JWT + STATELESS 이므로 CSRF 토큰 불필요
                .cors(Customizer.withDefaults())
                // 함께 배포한 화면이 자기 주소의 스크립트·글꼴·API만 쓰도록 제한한다 (XSS가 생겨도 외부로 못 나가게 하는 한 겹)
                // 인라인 style 속성은 React 화면이 쓰므로 허용한다
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 인증 없이 허용
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // 관리자 전용 (ETL, 회원 관리, 통계)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // /api 밖은 함께 배포한 프론트 화면과 정적 파일 (화면은 공개, 데이터는 /api가 지킨다)
                        .requestMatchers(request -> !request.getRequestURI().startsWith("/api/")).permitAll()
                        // 나머지는 로그인 필요
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 ->
                        oauth2.userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService))
                                .successHandler(oAuth2SuccessHandler())
                                .failureHandler((request, response, exception) -> {
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType("application/json;charset=UTF-8");
                                    response.getWriter().println("{\"code\": \"OAUTH2_LOGIN_FAILED\", \"error\": \"OAuth2 로그인 실패\"}");
                                }))
                .exceptionHandling(exception -> exception
                        // 미인증 401
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().println("{\"code\": \"AUTHENTICATION_REQUIRED\", \"error\": \"인증이 필요합니다.\"}");
                        })
                        // 권한 부족 403 (일반 회원이 관리자 API 호출 등)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().println("{\"code\": \"ACCESS_DENIED\", \"error\": \"접근 권한이 없습니다.\"}");
                        })
                )
                // 같은 기준 필터 앞에 등록하면 등록 순서대로 실행된다.
                // 요청 제한을 가장 먼저 두어, 차단된 요청은 비밀번호 검증(BCrypt)까지 가지 않게 한다.
                .addFilterBefore(new AuthRateLimitFilter(rateLimitService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiLoginFilter(manager), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(tokenCheckFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(refreshTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    /**
     * 아이디·비밀번호 로그인 필터(/api/auth/login). 성공·실패 처리기와 로그인 잠금을 연결한다.
     *
     * @param authenticationManager 인증 관리자
     * @return 로그인 필터
     */
    public ApiLoginFilter apiLoginFilter(AuthenticationManager authenticationManager) {
        ApiLoginFilter filter = new ApiLoginFilter("/api/auth/login", loginAttemptService);
        filter.setAuthenticationManager(authenticationManager);
        filter.setAuthenticationSuccessHandler(
                new LoginSuccessHandler(jwtUtil, securityTokenService, memberRepository, loginAttemptService,
                        refreshCookieFactory));
        filter.setAuthenticationFailureHandler(new LoginFailureHandler(loginAttemptService));
        return filter;
    }

    /**
     * 요청마다 AccessToken을 검사하는 필터. 폐기된 토큰도 여기서 걸러진다.
     *
     * @return 토큰 검사 필터
     */
    @Bean
    public TokenCheckFilter tokenCheckFilter() {
        return new TokenCheckFilter(jwtUtil, tokenRevocationService);
    }

    /**
     * RefreshToken 쿠키로 AccessToken을 다시 발급하는 필터(/api/auth/refresh).
     *
     * @return 재발급 필터
     */
    @Bean
    public RefreshTokenFilter refreshTokenFilter() {
        return new RefreshTokenFilter("/api/auth/refresh", jwtUtil, securityTokenService, memberRepository,
                refreshCookieFactory);
    }

    /**
     * 토큰 검사 필터를 서블릿 필터로 따로 등록하지 않는다.
     * 필터를 빈으로 만들면 스프링 부트가 서블릿 필터로도 자동 등록하는데, 이 필터는 보안 필터 체인 안에서만 돌아야 한다.
     *
     * @param filter 토큰 검사 필터
     * @return 비활성 등록
     */
    @Bean
    public FilterRegistrationBean<TokenCheckFilter> tokenCheckFilterRegistration(TokenCheckFilter filter) {
        FilterRegistrationBean<TokenCheckFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 재발급 필터를 서블릿 필터로 따로 등록하지 않는다 (위와 같은 이유).
     *
     * @param filter 재발급 필터
     * @return 비활성 등록
     */
    @Bean
    public FilterRegistrationBean<RefreshTokenFilter> refreshTokenFilterRegistration(RefreshTokenFilter filter) {
        FilterRegistrationBean<RefreshTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 구글 로그인 성공 시 토큰을 발급하는 처리기.
     *
     * @return OAuth2 성공 처리기
     */
    @Bean
    public OAuth2SuccessHandler oAuth2SuccessHandler() {
        return new OAuth2SuccessHandler(jwtUtil, securityTokenService, refreshCookieFactory);
    }

    /**
     * 비밀번호 해시 방식 (BCrypt).
     *
     * @return 비밀번호 인코더
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
