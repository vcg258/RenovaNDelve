package com.renovandelve.game.dto;

import com.renovandelve.catalog.domain.consumableentity.Type;
import lombok.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BuffDTO {
    private Long id; //아이디
    private String name; //이름
    private String context; //설명
    private int stack;      // 현재 스택 수
    private Type buffType;  // 버프 타입
}
