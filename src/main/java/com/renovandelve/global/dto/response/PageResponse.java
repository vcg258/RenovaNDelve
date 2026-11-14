package com.renovandelve.global.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 응답 공통 포맷.
 * Spring의 Page를 그대로 노출하면 직렬화 구조가 불안정하므로 필요한 필드만 담는다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    /**
     * 스프링 데이터 페이지를 응답 형식으로 옮긴다.
     *
     * @param page   조회한 페이지
     * @param mapper 항목 변환 함수
     * @param <E>    원래 항목 타입
     * @param <T>    응답 항목 타입
     * @return 페이지 응답
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
