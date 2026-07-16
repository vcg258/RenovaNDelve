package com.renovandelve.service;

import com.renovandelve.config.RecordMapper;
import com.renovandelve.domain.MonsterEntity;
import com.renovandelve.dto.MonsterDTO;
import com.renovandelve.repository.MonsterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class MonsterServiceImpl implements MonsterService{
    private final MonsterRepository monsterRepository;

    /**
     * 몬스터 이름으로 일반 몬스터 조회(LLM이 스토리와 테마에 알맞은 몬스터를 출현시키기 떄문)
     * @param name 몬스터 이름
     * @return 일반 몬스터
     */
    @Override
    public List<MonsterDTO> getMonsterByNameNormal(List<String> name) {
        List<MonsterEntity> monsterList = monsterRepository.findByNameNormal(name);
        return monsterList.stream().map(monsterEntity -> RecordMapper.map(monsterEntity, MonsterDTO.class)).toList();
    }

    /**
     * 몬스터 이름으로 보스 몬스터 조회(LLM이 스토리와 테마에 알맞은 몬스터를 출현시키기 떄문)
     * @param name 몬스터 이름
     * @return 보스 몬스터
     */
    @Override
    public List<MonsterDTO> getMonsterByNameBoss(List<String> name) {
        List<MonsterEntity> monsterList = monsterRepository.findByNameBoss(name);
        return monsterList.stream().map(monsterEntity -> RecordMapper.map(monsterEntity, MonsterDTO.class)).toList();
    }

    /**
     * RAG의 키워드에서 유사도가 너무 낮을경우 랜덤 일반 몬스터 출현할 메서드
     * @return 일반 몬스터
     */
    @Override
    public List<MonsterDTO> getRandomMonsterNormal() {
        int count = (int) ((Math.random() * 3) + 1);
        Pageable pageable = PageRequest.of(0, count);
        List<MonsterEntity> randomMonster = monsterRepository.findRandomMonsterNormal(pageable);
        return randomMonster.stream().map(monsterEntity -> RecordMapper.map(monsterEntity, MonsterDTO.class)).toList();
    }

    /**
     * RAG의 키워드에서 유사도가 너무 낮을경우 랜덤 보스 몬스터 출현할 메서드
     * @return 보스 몬스터
     */
    @Override
    public List<MonsterDTO> getRandomMonsterBoss() {
        Pageable pageable = PageRequest.of(0, 1);
        List<MonsterEntity> randomMonster = monsterRepository.findRandomMonsterBoss(pageable);
        return randomMonster.stream().map(monsterEntity -> RecordMapper.map(monsterEntity, MonsterDTO.class)).toList();
    }
}
