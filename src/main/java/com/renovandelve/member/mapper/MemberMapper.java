package com.renovandelve.member.mapper;

import com.renovandelve.global.config.MapStructConfig;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.dto.MemberDTO;
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
}
