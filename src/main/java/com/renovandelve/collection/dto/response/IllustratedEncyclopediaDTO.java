package com.renovandelve.collection.dto.response;

import com.renovandelve.collection.dto.CollectionArmorDTO;
import com.renovandelve.collection.dto.CollectionConsumableDTO;
import com.renovandelve.collection.dto.CollectionHelmetDTO;
import com.renovandelve.collection.dto.CollectionRelicDTO;
import com.renovandelve.collection.dto.CollectionSkillDTO;
import com.renovandelve.collection.dto.CollectionWeaponDTO;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

//도감정보
public record IllustratedEncyclopediaDTO (
    List<CollectionArmorDTO> armorDTO, //
    List<CollectionConsumableDTO> consumableDTO, // 소모품
    List<CollectionHelmetDTO>  helmetDTO,
    List<CollectionRelicDTO> relicDTO,
    List<CollectionSkillDTO> skillDTO,
    List<CollectionWeaponDTO> weaponDTO){
}
