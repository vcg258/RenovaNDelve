package com.renovandelve.catalog.domain;

/**
 * 등급. 유물·무기·갑옷·투구·스킬이 같은 등급을 쓴다. 상점과 전리품은 스테이지별 확률로 등급을 먼저 정한다({@code RarityTable}).
 */
public enum Rarity {
    COMMON, RARE, EPIC
}
