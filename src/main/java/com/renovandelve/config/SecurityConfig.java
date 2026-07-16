package com.renovandelve.config;

import com.renovandelve.filter.ApiLoginFilter;
import com.renovandelve.filter.RefreshTokenFilter;
import com.renovandelve.filter.TokenCheckFilter;
import com.renovandelve.handler.LoginSuccessHandler;
import com.renovandelve.handler.OAuth2SuccessHandler;
import com.renovandelve.service.security.MemberDetailsService;
import com.renovandelve.service.redis.SecurityTokenService;
import com.renovandelve.service.security.Oauth2UserService;
import com.renovandelve.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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

    @Bean
    public SecurityFilterChain securityFilterChain (HttpSecurity httpSecurity, Oauth2UserService oauth2UserService) throws Exception {

        // 검증 매니저 생성
        AuthenticationManagerBuilder authenticationManager =
                httpSecurity.getSharedObject(AuthenticationManagerBuilder.class);

        authenticationManager
                .userDetailsService(memberDetailsService)
                .passwordEncoder(passwordEncoder());

        AuthenticationManager manager = authenticationManager.build();

        // 시큐리티 설정 추가
        httpSecurity.authenticationManager(manager);

        httpSecurity
                .formLogin(formLogin -> formLogin.disable()) // 시큐리티 기본 로그인 화면 비활성화
                .httpBasic(httpBasic -> httpBasic.disable())
                .csrf(csrf -> csrf.disable()) // CSRF 검증 비활성화
                .cors(Customizer.withDefaults()) // CORS 허용
                .sessionManagement(session ->
                        // STATELESS = 세션 아예 사용안함, ALWAYS = 항상 세션 생성, IF_REQUIRED = 필요할때만 생성 기본값, NEVER = 직접 안만듬 대신 있으면 사용
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**").permitAll()
//                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
//                        .requestMatchers("/*.html").permitAll() // TODO OAuth2 테스트용
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 ->
                        oauth2.userInfoEndpoint(userInfo -> userInfo
                                .userService(oauth2UserService))
                                .successHandler(oAuth2SuccessHandler())
                                .failureHandler((request, response, exception) -> {
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                    response.getWriter().println("{\"error\": \"OAuth2 로그인 실패\"}");
                                }))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().println("{\"error\": \"인증이 필요합니다\"}");
                        })
                )
                .addFilterBefore(apiLoginFilter(manager), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(tokenCheckFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(refreshTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    public ApiLoginFilter apiLoginFilter(AuthenticationManager authenticationManager) {
        ApiLoginFilter filter = new ApiLoginFilter("/api/auth/login");
        filter.setAuthenticationManager(authenticationManager);
        filter.setAuthenticationSuccessHandler(new LoginSuccessHandler(jwtUtil, securityTokenService));
        return filter;
    }

    @Bean
    public TokenCheckFilter tokenCheckFilter() {
        return new TokenCheckFilter(jwtUtil);
    }

    @Bean
    public RefreshTokenFilter refreshTokenFilter() {
        return new RefreshTokenFilter("/api/auth/refresh", jwtUtil, securityTokenService);
    }

    @Bean
    public OAuth2SuccessHandler oAuth2SuccessHandler() {
        return new OAuth2SuccessHandler(jwtUtil, securityTokenService);
    }

    private PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
