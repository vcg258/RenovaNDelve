package com.renovandelve.member.mapper;

import com.renovandelve.global.config.MapStructConfig;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.dto.MemberDTO;
import com.renovandelve.member.dto.response.MemberProfileDTO;
import com.renovandelve.member.dto.response.MemberSummaryDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 회원 엔티티를 DTO로 옮긴다.
 */
@Mapper(config = MapStructConfig.class)
public interface MemberMapper {

    /**
     * 도감·랭킹 응답에 붙는 회원 정보: ID와 표시 이름(닉네임)만.
     * {@code MemberDTO}에는 비밀번호 해시·소셜 토큰 필드도 있어서, 이름이 같다고 자동으로 옮기지 않도록
     * 기본값을 "옮기지 않음"으로 두고 두 필드만 적는다.
     *
     * @param member 회원
     * @return ID와 표시 이름만 채운 DTO
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "memberId", source = "memberId")
    @Mapping(target = "loginId", expression = "java(member.displayName())")
    MemberDTO toPublicDTO(MemberEntity member);

    /**
     * 내 정보. 닉네임이 없으면 표시 이름을 쓰고, 비밀번호 해시는 있는지만 보낸다.
     *
     * @param member          회원
     * @param collectionCount 도감 수집 수
     * @param bestScore       최고 점수
     * @return 내 정보
     */
    @Mapping(target = "nickname", expression = "java(member.displayName())")
    @Mapping(target = "hasPassword", expression = "java(member.getPasswordHash() != null)")
    MemberProfileDTO toProfileDTO(MemberEntity member, int collectionCount, int bestScore);

    /**
     * 관리자 화면의 회원 한 줄.
     *
     * @param member  회원
     * @param playing 지금 원정 중이면 true
     * @return 회원 요약
     */
    @Mapping(target = "nickname", expression = "java(member.displayName())")
    MemberSummaryDTO toSummaryDTO(MemberEntity member, boolean playing);
}
