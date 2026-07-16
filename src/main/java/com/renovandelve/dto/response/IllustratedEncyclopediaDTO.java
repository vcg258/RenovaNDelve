package com.renovandelve.dto.response;

import com.renovandelve.dto.*;
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
