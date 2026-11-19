package com.renovandelve.collection.service;

import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.catalog.mapper.CatalogMapperImpl;
import com.renovandelve.collection.domain.CollectionSkillEntity;
import com.renovandelve.collection.mapper.CollectionMapper;
import com.renovandelve.collection.mapper.CollectionMapperImpl;
import com.renovandelve.collection.repository.CollectionArmorRepository;
import com.renovandelve.collection.repository.CollectionConsumableRepository;
import com.renovandelve.collection.repository.CollectionHelmetRepository;
import com.renovandelve.collection.repository.CollectionRelicRepository;
import com.renovandelve.collection.repository.CollectionSkillRepository;
import com.renovandelve.collection.repository.CollectionWeaponRepository;
import com.renovandelve.member.domain.MemberEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionSkillRepository collectionSkillRepository;
    @Mock private CollectionWeaponRepository collectionWeaponRepository;
    @Mock private CollectionArmorRepository collectionArmorRepository;
    @Mock private CollectionHelmetRepository collectionHelmetRepository;
    @Mock private CollectionRelicRepository collectionRelicRepository;
    @Mock private CollectionConsumableRepository collectionConsumableRepository;
    @Spy private CollectionMapper collectionMapper = new CollectionMapperImpl(new CatalogMapperImpl(), new com.renovandelve.member.mapper.MemberMapperImpl());
    @InjectMocks private CollectionService collectionService;

    private final MemberEntity member = MemberEntity.social("s-1");
    private final SkillEntity skill = SkillEntity.builder().skillId(3L).name("출혈 베기").build();

    @Test
    @DisplayName("처음 얻은 항목은 정적 팩토리로 만들어 저장하고 새 항목을 돌려준다")
    void registersNewEntry() {
        when(collectionSkillRepository.existsByMemberAndSkill(member, skill)).thenReturn(false);
        when(collectionSkillRepository.save(any(CollectionSkillEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(collectionService.registerSkill(member, skill)).isPresent();

        ArgumentCaptor<CollectionSkillEntity> saved = ArgumentCaptor.forClass(CollectionSkillEntity.class);
        verify(collectionSkillRepository).save(saved.capture());
        assertThat(saved.getValue().getMember()).isSameAs(member);
        assertThat(saved.getValue().getSkill()).isSameAs(skill);
    }

    @Test
    @DisplayName("이미 등록된 항목은 다시 넣지 않는다")
    void skipsKnownEntry() {
        when(collectionSkillRepository.existsByMemberAndSkill(member, skill)).thenReturn(true);

        assertThat(collectionService.registerSkill(member, skill)).isEmpty();
        verify(collectionSkillRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원이나 항목이 없으면 아무것도 하지 않는다 (맨손 등)")
    void skipsNull() {
        assertThat(collectionService.registerWeapon(member, null)).isEmpty();
        assertThat(collectionService.registerWeapon(null, WeaponEntity.builder().weaponId(1L).build())).isEmpty();
        verifyNoInteractions(collectionWeaponRepository);
    }
}
