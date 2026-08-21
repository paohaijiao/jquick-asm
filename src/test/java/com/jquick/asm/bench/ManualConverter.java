package com.jquick.asm.bench;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 策略1：手写硬编码转换器。
 *
 * <p>性能基线（最快），直接调用 getter，无任何反射/字节码开销。
 * 缺点：字段变更需手动维护代码。
 */
public class ManualConverter implements ObjectToMapConverter {

    @Override
    public Map<String, Object> convert(Object target) {
        BenchmarkUser u = (BenchmarkUser) target;
        Map<String, Object> map = new LinkedHashMap<>(8);
        map.put("id", u.getId());
        map.put("name", u.getName());
        map.put("age", u.getAge());
        map.put("email", u.getEmail());
        map.put("active", u.isActive());
        map.put("score", u.getScore());
        map.put("address", u.getAddress());
        return map;
    }

    @Override
    public String name() {
        return "Manual(手写硬编码)";
    }
}
