package com.renovandelve.config;

import java.util.List;

public class RecordMapper {

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
    public static <T> List<T> mapList(List<?> sourceList, Class<T> recordType) {
        return sourceList.stream()
                .map(source -> map(source, recordType))
                .toList();
    }
}
