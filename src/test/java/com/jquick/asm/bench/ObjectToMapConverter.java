package com.jquick.asm.bench;

import java.util.Map;

/**
 * 对象转 Map 转换器接口：所有转换策略需实现此接口。
 *
 * <p>统一约定：key=属性名（去除 get/is 前缀后首字母小写），value=属性值。
 */
public interface ObjectToMapConverter {

    /**
     * 将目标对象转换为 {@link Map}。
     *
     * @param target 目标对象
     * @return 字段名 → 字段值 的映射
     */
    Map<String, Object> convert(Object target);

    /**
     * 转换器名称（用于基准测试结果展示）。
     */
    String name();
}
