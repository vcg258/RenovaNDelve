package com.renovandelve.global.config;

import java.util.List;

public class RecordMapper {

    /**
     * 엔티티 같은 일반 객체를 record로 옮긴다. record 구성 요소와 이름이 같은 getter의 값을 넣는다.
     *
     * @param source     값을 꺼낼 객체 (null이면 null)
     * @param recordType 만들 record 타입
     * @param <T>        record 타입
     * @return 새 record
     * @throws IllegalArgumentException record가 아니거나 값을 꺼내지 못하면
     */
    public static <T> T map(Object source, Class<T> recordType) {
        if (!recordType.isRecord()) {
            throw new IllegalArgumentException(recordType.getName() + "은 Record가 아닙니다.");
        }

        try {
            var components = recordType.getRecordComponents();
            Object[] args = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];

            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                String fieldName = components[i].getName();

                // getter 이름 변환 (JPA Entity는 getXxx 형식)
                String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

                try {
                    var getter = source.getClass().getMethod(getterName);
                    args[i] = getter.invoke(source);
                } catch (NoSuchMethodException e) {
                    // getXxx 없으면 필드명 그대로 시도 (Record accessor 방식)
                    try {
                        var getter = source.getClass().getMethod(fieldName);
                        args[i] = getter.invoke(source);
                    } catch (NoSuchMethodException ex) {
                        args[i] = null;
                    }
                }
            }

            var constructor = recordType.getDeclaredConstructor(types);
            return constructor.newInstance(args);

        } catch (Exception e) {
            throw new RuntimeException("Record 매핑 실패: " + recordType.getName(), e);
        }
    }

    // List 변환도 지원
    /**
     * 목록을 한꺼번에 record로 옮긴다.
     *
     * @param sourceList 값을 꺼낼 객체 목록
     * @param recordType 만들 record 타입
     * @param <T>        record 타입
     * @return 새 record 목록
     */
    public static <T> List<T> mapList(List<?> sourceList, Class<T> recordType) {
        return sourceList.stream()
                .map(source -> map(source, recordType))
                .toList();
    }
}
