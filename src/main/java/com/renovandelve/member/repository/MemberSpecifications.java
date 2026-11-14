package com.renovandelve.member.repository;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.domain.memberentity.MemberStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 회원 검색 조건 조합기.
 *
 * JPQL에서 ':param IS NULL' 방식으로 동적 조건을 만들면
 * enum 파라미터의 타입 추론 문제와 NULL 컬럼 처리에서 예기치 않게 결과가 비는 일이 있어,
 * 조건이 있을 때만 술어를 추가하는 Specification 방식으로 처리한다.
 */
public final class MemberSpecifications {

    private MemberSpecifications() {
    }

    /**
     * 관리자 회원 검색 조건. 키워드는 아이디·닉네임에 대소문자 없이 부분 일치하고, 상태가 있으면 그 상태만 고른다.
     * (JPQL의 ":param IS NULL" 패턴은 enum 파라미터와 NULL 컬럼에서 결과가 비는 문제가 있어 Specification으로 만들었다)
     *
     * @param keyword 아이디 또는 닉네임 일부 (null이면 조건 없음)
     * @param status  계정 상태 (null이면 전체)
     * @return 검색 조건
     */
    public static Specification<MemberEntity> search(String keyword, MemberStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("loginId"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("nickname"), "")), pattern)
                ));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
