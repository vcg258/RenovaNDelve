package com.renovandelve.ranking.mapper;

import com.renovandelve.game.mapper.GameMapper;
import com.renovandelve.global.config.MapStructConfig;
import com.renovandelve.member.mapper.MemberMapper;
import com.renovandelve.ranking.domain.RankingEntity;
import com.renovandelve.ranking.dto.RankingDTO;
import com.renovandelve.ranking.dto.response.RankingViewDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 랭킹 엔티티를 DTO로 옮긴다.
 */
@Mapper(config = MapStructConfig.class, uses = {MemberMapper.class, GameMapper.class})
public interface RankingMapper {

    /**
     * @param ranking 랭킹 기록
     * @return DTO (회원은 ID와 표시 이름만)
     */
    RankingDTO toDTO(RankingEntity ranking);

    /**
     * 랭킹 화면의 한 줄. 아이디 자리에는 표시 이름(닉네임)을 넣는다.
     *
     * @param ranking 랭킹 기록
     * @param rank    순위 (1부터)
     * @return 화면용 한 줄
     */
    @Mapping(target = "rank", source = "rank")
    @Mapping(target = "loginId", expression = "java(ranking.getMember().displayName())")
    RankingViewDTO toViewDTO(RankingEntity ranking, int rank);
}
