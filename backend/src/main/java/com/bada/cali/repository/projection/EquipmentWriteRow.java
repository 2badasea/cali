package com.bada.cali.repository.projection;

/**
 * 성적서작성 시 '데이터' 시트에 삽입할 표준장비 데이터 Projection.
 * EquipmentRefRepository.findEquipmentForWrite() 결과를 매핑한다.
 */
public interface EquipmentWriteRow {
    Integer getSeq();
    String getName();
    String getNameEn();
    String getMakeAgent();
    String getMakeAgentEn();
    String getModelName();
    String getSerialNo();
}
