package com.renovandelve.global.config;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 모든 MapStruct 매퍼의 공통 설정.
 * <ul>
 *   <li>스프링 빈으로 만들고 생성자로 주입한다 (단위 테스트에서 {@code new XxxMapperImpl(...)}로 조립할 수 있다)</li>
 *   <li>DTO에 채우지 못한 필드가 있으면 컴파일 오류로 만든다. 엔티티나 DTO의 필드 이름이 바뀌면 빌드에서 바로 드러난다.
 *       일부러 비우는 필드는 매퍼에 {@code @Mapping(target = ..., ignore = true)}로 적는다</li>
 * </ul>
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MapStructConfig {
}
