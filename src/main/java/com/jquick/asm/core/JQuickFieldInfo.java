package com.jquick.asm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ASM‑core field metadata container.
 *
 * <p>Stores field access modifiers, name, descriptor, signature, initial value and annotations.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * JQuickFieldInfo field = new JQuickFieldInfo(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;", null);
 * field.addAnnotation(new JQuickAnnotationInfo("Lcom/demo/NotNull;"));
 * }</pre>
 */
public class JQuickFieldInfo {

    /**
     * Access modifiers
     * ACC_PUBLIC 0x0001
     * ACC_PRIVATE 0x0002
     * ACC_PROTECTED 0x0004
     *
     */
    private final int access;

    /**
     * 字段名
     */
    private final String name;

    /**
     * Field type descriptor，eg {@code "Ljava/lang/String;"}、{@code "I"}
     */
    private final String descriptor;

    /**
     * Field generic signature, null if there is no generic
     */
    private final String signature;

    /**
     * Initial value of field constant (only valid for static final constant fields), otherwise null
     */
    private final Object value;

    /**
     * Field annotation list
     */
    private final List<JQuickAnnotationInfo> annotations = new ArrayList<>();

    public JQuickFieldInfo(int access, String name, String descriptor, String signature, Object value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Field name cannot be empty");
        }
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("Field descriptor cannot be empty");
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
     * Retrieve the annotation of the specified descriptor, there is no null returned.
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
     * Return the immutable annotation list view
     */
    public List<JQuickAnnotationInfo> annotationsView() {
        return Collections.unmodifiableList(annotations);
    }

    @Override
    public String toString() {
        return "JQuickFieldInfo{name='" + name + "', desc='" + descriptor + "', access=" + access + "}";
    }
}
