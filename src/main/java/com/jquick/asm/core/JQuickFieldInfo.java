package com.jquick.asm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * asm-core 字段信息容器。
 *
 * <p>记录字段的访问修饰符、名称、描述符、签名、初始值与注解列表。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * JQuickFieldInfo field = new JQuickFieldInfo(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;", null);
 * field.addAnnotation(new JQuickAnnotationInfo("Lcom/demo/NotNull;"));
 * }</pre>
 */
public class JQuickFieldInfo {

    /**
     * 访问修饰符
     */
    private final int access;

    /**
     * 字段名
     */
    private final String name;

    /**
     * 字段类型描述符，如 {@code "Ljava/lang/String;"}、{@code "I"}
     */
    private final String descriptor;

    /**
     * 字段泛型签名，无泛型则为 null
     */
    private final String signature;

    /**
     * 字段常量初始值（仅 static final 常量字段有效），否则 null
     */
    private final Object value;

    /**
     * 字段注解列表
     */
    private final List<JQuickAnnotationInfo> annotations = new ArrayList<>();

    public JQuickFieldInfo(int access, String name, String descriptor, String signature, Object value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("字段描述符不能为空");
        }
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
        this.signature = signature;
        this.value = value;
    }

    public JQuickFieldInfo(int access, String name, String descriptor) {
        this(access, name, descriptor, null, null);
    }

    public int getAccess() {
        return access;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public String getSignature() {
        return signature;
    }

    public Object getValue() {
        return value;
    }

    public List<JQuickAnnotationInfo> getAnnotations() {
        return annotations;
    }

    public void addAnnotation(JQuickAnnotationInfo annotation) {
        if (annotation != null) {
            annotations.add(annotation);
        }
    }

    /**
     * 获取指定描述符的注解，不存在返回 null。
     */
    public JQuickAnnotationInfo getAnnotation(String descriptor) {
        for (JQuickAnnotationInfo ann : annotations) {
            if (ann.getDescriptor().equals(descriptor)) {
                return ann;
            }
        }
        return null;
    }

    /**
     * 返回不可变注解列表视图。
     */
    public List<JQuickAnnotationInfo> annotationsView() {
        return Collections.unmodifiableList(annotations);
    }

    @Override
    public String toString() {
        return "JQuickFieldInfo{name='" + name + "', desc='" + descriptor + "', access=" + access + "}";
    }
}
