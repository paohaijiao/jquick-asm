package com.jquick.asm.core;

import com.jquick.asm.util.JQuickAccessUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Method information container for asm‑core.
 *
 * <p>Stores method access flags, name, descriptor, signature, thrown exceptions, parameter names and annotation list.
 * Does not hold method bytecode instructions directly; instruction‑level editing is handled by the {@code asm‑enhance} module.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * JQuickMethodInfo m = new JQuickMethodInfo(
 *     Opcodes.ACC_PUBLIC, "doSomething", "(ILjava/lang/String;)V");
 * m.addException("java/io/IOException");
 * m.addAnnotation(new JQuickAnnotationInfo("Lcom/demo/Trace;"));
 * boolean safe = m.isModifiable();
 * }</pre>
 */
public class JQuickMethodInfo {

    /**
     * method Name
     */
    private final String name;
    /**
     * method， Descriptor, eg  {@code "(ILjava/lang/String;)V"}
     */
    private final String descriptor;
    /**
     * Method generic signature, null if there is no generic
     */
    private final String signature;
    /**
     * Method throws a list of internal names for exceptions, eg{@code ["java/io/IOException"]}
     */
    private final List<String> exceptions = new ArrayList<>();
    /**
     * Method parameter name list (if debugging information is retained)
     */
    private final List<String> parameterNames = new ArrayList<>();
    /**
     * Method Annotation List
     */
    private final List<JQuickAnnotationInfo> annotations = new ArrayList<>();
    /**
     * Access Modifiers
     */
    private int access;

    public JQuickMethodInfo(int access, String name, String descriptor, String signature) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Method name cannot be empty");
        }
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("Method descriptor cannot be empty");
        }
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
        this.signature = signature;
    }

    public JQuickMethodInfo(int access, String name, String descriptor) {
        this(access, name, descriptor, null);
    }

    public int getAccess() {
        return access;
    }

    public void setAccess(int access) {
        this.access = access;
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

    public List<String> getExceptions() {
        return exceptions;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    public List<JQuickAnnotationInfo> getAnnotations() {
        return annotations;
    }

    public void addException(String internalName) {
        if (internalName != null && !internalName.isEmpty()) {
            exceptions.add(internalName);
        }
    }

    public void addParameterName(String name) {
        parameterNames.add(name);
    }

    public void addAnnotation(JQuickAnnotationInfo annotation) {
        if (annotation != null) {
            annotations.add(annotation);
        }
    }

    public JQuickAnnotationInfo getAnnotation(String descriptor) {
        for (JQuickAnnotationInfo ann : annotations) {
            if (ann.getDescriptor().equals(descriptor)) {
                return ann;
            }
        }
        return null;
    }

    /**
     * Checks whether this is a constructor method.
     */
    public boolean isConstructor() {
        return JQuickAccessUtil.isConstructor(name);
    }

    /**
     * Returns {@code true} if this is a static initializer block.
     */
    public boolean isStaticInitializer() {
        return JQuickAccessUtil.isStaticInitializer(name);
    }

    /**
     * Returns {@code true} if this is a native method.
     */
    public boolean isNative() {
        return JQuickAccessUtil.isNative(access);
    }

    /**
     * Returns {@code true} if this is an abstract method (no method body, instrumentation is not applicable).
     */
    public boolean isAbstract() {
        return JQuickAccessUtil.isAbstract(access);
    }

    /**
     * Returns {@code true} if this method can be safely modified: excludes constructors, native methods, abstract methods, and static initializer blocks.
     * Corruption of constructors may break object‑initialization semantics; instrumentation is prohibited per safety specifications.
     */
    public boolean isModifiable() {
        return !isConstructor() && !isNative() && !isAbstract() && !isStaticInitializer();
    }

    public List<JQuickAnnotationInfo> annotationsView() {
        return Collections.unmodifiableList(annotations);
    }

    @Override
    public String toString() {
        return "JQuickMethodInfo{name='" + name + "', desc='" + descriptor + "', access=" + JQuickAccessUtil.toString(access) + "}";
    }
}
