package com.jquick.asm.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * asm-core 注解信息容器。
 * <p>记录一个注解的描述符与属性键值对，是类/字段/方法注解解析结果的统一载体。
 * <h3>使用示例</h3>
 * <pre>{@code
 * JQuickAnnotationInfo ann = new JQuickAnnotationInfo("Lcom/demo/Trace;");
 * ann.setAttribute("value", "doSomething");
 * String value = (String) ann.getAttribute("value");
 * }</pre>
 */
public class JQuickAnnotationInfo {

    /**
     * 注解类型描述符，如 {@code "Lcom/demo/Trace;"}
     */
    private final String descriptor;

    /**
     * 注解属性键值对，保持插入顺序
     */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public JQuickAnnotationInfo(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("注解描述符不能为空");
        }
        this.descriptor = descriptor;
    }

    public String getDescriptor() {
        return descriptor;
    }

    /**
     * 获取注解全限定类名（点分隔）。
     *
     * @return 如 {@code "com.demo.Trace"}
     */
    public String getClassName() {
        // 去掉 L 前缀和 ; 后缀，并把 / 转为 .
        String name = descriptor;
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        return name.replace('/', '.');
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public String toString() {
        return "JQuickAnnotationInfo{" + descriptor + ", attrs=" + attributes + "}";
    }
}
