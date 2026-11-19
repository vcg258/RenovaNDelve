package com.renovandelve.architecture;

import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 계층 의존 방향을 테스트로 고정한다 (예전에는 문서로만 약속).
 * 모듈 사이(game, member, catalog ...)는 JPA 양방향 연관 때문에 서로 참조하는 곳이 있어 여기서는 계층만 본다.
 */
@AnalyzeClasses(packages = "com.renovandelve", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

    /** 엔티티·도메인은 서비스와 컨트롤러를 모른다 */
    @ArchTest
    static final ArchRule domainIsIndependent = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..");

    /** 서비스는 컨트롤러를 모른다 */
    @ArchTest
    static final ArchRule servicesDoNotUseControllers = noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    /** 저장소는 서비스를 모른다 */
    @ArchTest
    static final ArchRule repositoriesDoNotUseServices = noClasses().that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..");

    /**
     * 게임 엔진(순수 계산)은 서비스·저장소·컨트롤러를 모른다.
     * 예전에 SkillSynergies가 서비스의 예고 상수를 문자열로 베껴 쓴 것도 이 방향을 지키려던 것이었다 (지금은 engine의 MonsterIntent).
     */
    @ArchTest
    static final ArchRule engineIsPure = noClasses().that().resideInAPackage("com.renovandelve.game.engine..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..", "..controller..");

    /**
     * 엔티티는 도메인 패키지 밖에서 빌더로 만들지 않는다. 밖에서는 이름 있는 정적 팩토리(register, start, of ...)만 쓴다.
     * 빌더는 엔티티 자신의 팩토리와 테스트 픽스처용이다 (테스트 코드는 이 검사에서 빠진다).
     */
    @ArchTest
    static final ArchRule entitiesAreCreatedByFactories = noClasses().that().resideOutsideOfPackage("..domain..")
            .should().callMethodWhere(target(HasName.Predicates.name("builder"))
                    .and(target(HasOwner.Predicates.With.owner(CanBeAnnotated.Predicates.annotatedWith(Entity.class)))))
            .because("엔티티 생성 규칙은 엔티티의 정적 팩토리가 갖는다");
}
