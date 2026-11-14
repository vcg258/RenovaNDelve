package com.renovandelve.config;

import com.renovandelve.filter.ApiLoginFilter;
import com.renovandelve.filter.RefreshTokenFilter;
import com.renovandelve.filter.TokenCheckFilter;
import com.renovandelve.handler.LoginAttemptService;
import com.renovandelve.handler.LoginFailureHandler;
import com.renovandelve.handler.LoginSuccessHandler;
import com.renovandelve.handler.OAuth2SuccessHandler;
import com.renovandelve.repository.MemberRepository;
import com.renovandelve.service.redis.SecurityTokenService;
import com.renovandelve.service.security.MemberDetailsService;
import com.renovandelve.service.security.Oauth2UserService;
import com.renovandelve.util.JwtUtil;
import com.renovandelve.util.RefreshCookieFactory;
import com.renovandelve.filter.AuthRateLimitFilter;
import com.renovandelve.service.redis.RateLimitService;
import com.renovandelve.service.redis.TokenRevocationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 인증 없이 허용
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // 관리자 전용 (ETL, 회원 관리, 통계)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 나머지는 로그인 필요
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 ->
                        oauth2.userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService))
                                .successHandler(oAuth2SuccessHandler())
                                .failureHandler((request, response, exception) -> {
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType("application/json;charset=UTF-8");
                                    response.getWriter().println("{\"error\": \"OAuth2 로그인 실패\"}");
                                }))
                .exceptionHandling(exception -> exception
                        // 미인증 401
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().println("{\"error\": \"인증이 필요합니다\"}");
                        })
                        // 권한 부족 403 (일반 회원이 관리자 API 호출 등)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().println("{\"error\": \"접근 권한이 없습니다\"}");
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
