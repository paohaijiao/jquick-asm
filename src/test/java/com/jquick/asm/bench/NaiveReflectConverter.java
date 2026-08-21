package com.jquick.asm.bench;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 策略2：朴素反射转换器（无缓存）。
 *
 * <p>每次调用都重新查找 getter 方法，性能最差。
 * 用于体现「未做缓存优化」的反射成本。
 */
public class NaiveReflectConverter implements ObjectToMapConverter {

    @Override
    public Map<String, Object> convert(Object target) {
        Class<?> clazz = target.getClass();
        Method[] methods = clazz.getMethods();
        Map<String, Object> map = new LinkedHashMap<>(methods.length);
        for (Method m : methods) {
            String name = m.getName();
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 0) {
                continue;
            }
            String fieldName;
            if (name.startsWith("get") && name.length() > 3
                    && !"getClass".equals(name)) {
                fieldName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            } else if (name.startsWith("is") && name.length() > 2
                    && (m.getReturnType() == boolean.class
                    || m.getReturnType() == Boolean.class)) {
                fieldName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            } else {
                continue;
            }
            try {
                map.put(fieldName, m.invoke(target));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("反射调用失败: " + name, e);
            }
        }
        return map;
    }

    @Override
    public String name() {
        return "NaiveReflect(朴素反射-无缓存)";
    }
}
