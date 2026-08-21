package com.jquick.asm.bench;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略3：缓存反射转换器。
 *
 * <p>Class → getter 列表 仅在首次解析时反射查找，后续直接复用缓存的 Method 数组。
 * 体现了「反射 + 缓存元数据」的常见优化做法（如 Spring BeanWrapper / FastMethod）。
 */
public class CachedReflectConverter implements ObjectToMapConverter {

    /**
     * Class → 缓存的 getter 元数据列表。
     */
    private static final ConcurrentHashMap<Class<?>, GetterEntry[]> CACHE =
            new ConcurrentHashMap<>();

    private static GetterEntry[] resolveGetters(Class<?> clazz) {
        Method[] methods = clazz.getMethods();
        java.util.List<GetterEntry> list = new java.util.ArrayList<>(methods.length);
        for (Method m : methods) {
            String name = m.getName();
            if (m.getParameterCount() != 0) {
                continue;
            }
            String fieldName;
            if (name.startsWith("get") && name.length() > 3 && !"getClass".equals(name)) {
                fieldName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            } else if (name.startsWith("is") && name.length() > 2
                    && (m.getReturnType() == boolean.class
                    || m.getReturnType() == Boolean.class)) {
                fieldName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            } else {
                continue;
            }
            m.setAccessible(true);
            list.add(new GetterEntry(fieldName, m));
        }
        return list.toArray(new GetterEntry[0]);
    }

    @Override
    public Map<String, Object> convert(Object target) {
        Class<?> clazz = target.getClass();
        GetterEntry[] entries = CACHE.get(clazz);
        if (entries == null) {
            entries = resolveGetters(clazz);
            CACHE.put(clazz, entries);
        }
        Map<String, Object> map = new LinkedHashMap<>(entries.length);
        try {
            for (GetterEntry e : entries) {
                map.put(e.fieldName, e.getter.invoke(target));
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("反射调用失败", e);
        }
        return map;
    }

    @Override
    public String name() {
        return "CachedReflect(反射+缓存元数据)";
    }

    /**
     * getter 元数据。
     */
    private static final class GetterEntry {
        final String fieldName;
        final Method getter;

        GetterEntry(String fieldName, Method getter) {
            this.fieldName = fieldName;
            this.getter = getter;
        }
    }
}
