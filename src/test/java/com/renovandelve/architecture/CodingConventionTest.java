package com.renovandelve.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리뷰에서 정한 코딩 규칙을 테스트로 고정한다. 규칙을 어기면 빌드가 실패한다.
 * (계층 의존 방향과 엔티티 생성 규칙은 {@link LayerDependencyTest})
 */
@AnalyzeClasses(packages = "com.renovandelve", importOptions = ImportOption.DoNotIncludeTests.class)
class CodingConventionTest {

    /**
     * 데이터 접근은 JPA로 한다. {@code JdbcTemplate}은 Spring AI가 관리하는 테이블(vector_store, spring_ai_chat_memory)에만 쓴다.
     */
    @ArchTest
    static final ArchRule jdbcTemplateOnlyForSpringAiTables = noClasses()
            .that().doNotHaveFullyQualifiedName("com.renovandelve.ai.service.GameDataEtlService")
            .and().doNotHaveFullyQualifiedName("com.renovandelve.member.service.PersonalDataService")
            .should().dependOnClassesThat().areAssignableTo(JdbcTemplate.class)
            .because("도메인 테이블은 JPA(필요하면 JPQL)로만 다룬다");

    /**
     * 사용자 요청 때문에 나는 오류는 {@code BusinessException(ErrorCode)}로 던진다.
     * {@code IllegalStateException}은 데이터·설정이 깨진 경우(500)에만 쓴다.
     */
    @ArchTest
    static final ArchRule businessErrorsUseErrorCode = noClasses()
            .that().resideInAnyPackage("..service..", "..domain..", "..engine..")
            .should().callConstructor(IllegalArgumentException.class, String.class)
            .orShould().callConstructor(NoSuchElementException.class, String.class)
            .because("비즈니스 오류는 BusinessException과 ErrorCode로 던진다 (JDK 예외는 500이 된다)");

    /** DTO는 값만 담는다: 엔티티를 모르고, 변환은 매퍼가 한다 */
    @ArchTest
    static final ArchRule dtosDoNotKnowEntities = noClasses().that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("엔티티 -> DTO 변환은 MapStruct 매퍼가 한다");

    /** DTO에 정적 변환·생성 메서드를 두지 않는다 (예외: 스프링 Page 포장, 팀 원본 AsyncStageResponseDTO) */
    @ArchTest
    static final ArchRule dtosHaveNoStaticMethods = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..dto..")
            .and().areDeclaredInClassesThat(notAllowedToHaveFactories())
            .and().areStatic().and().doNotHaveName("values").and().doNotHaveName("valueOf")
            .and().doNotHaveName("builder") // Lombok @Builder
            .should().bePublic()
            .allowEmptyShould(true) // 지금은 해당하는 메서드가 하나도 없다
            .because("DTO는 값만 담는다. 변환은 매퍼, 조립은 서비스가 한다");

    private static DescribedPredicate<JavaClass> notAllowedToHaveFactories() {
        return new DescribedPredicate<>("not PageResponse / AsyncStageResponseDTO") {
            @Override
            public boolean test(JavaClass c) {
                return !c.getSimpleName().equals("PageResponse") && !c.getSimpleName().equals("AsyncStageResponseDTO");
            }
        };
    }

    private static final Path MAIN = Path.of("src/main/java/com/renovandelve");
    /** 클래스 본문 바로 아래(들여쓰기 4칸)의 메서드·생성자 선언 */
    private static final Pattern METHOD = Pattern.compile("^ {4}(?:public |protected |private )?(?:static )?(?:final )?(?:<[^>]+> )?[\\w<>\\[\\], .?]*\\w+\\s*\\([^;]*$");
    /** 클래스 본문 바로 아래의 인스턴스 필드 선언 */
    private static final Pattern FIELD = Pattern.compile("^ {4}(?:public |protected |private )(?!static)(?:final )?[\\w<>\\[\\], .?]+ \\w+( =[^(]*.*)?;.*$");

    @Test
    @DisplayName("엔티티는 필드(컬럼, FK)를 모두 위에 두고 메서드를 아래에 둔다")
    void entityFieldsComeBeforeMethods() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (lines.stream().noneMatch(l -> l.startsWith("@Entity"))) continue;
                boolean methodSeen = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String trimmed = line.trim();
                    if (trimmed.startsWith("*") || trimmed.startsWith("/") || trimmed.startsWith("@")) continue;
                    if (!methodSeen && METHOD.matcher(line).matches() && !line.contains(" = ")) methodSeen = true;
                    else if (methodSeen && FIELD.matcher(line).matches()) {
                        violations.add(MAIN.relativize(file) + ":" + (i + 1) + "  " + trimmed);
                    }
                }
            }
        }
        assertThat(violations).as("메서드 아래에 있는 필드").isEmpty();
    }
}
