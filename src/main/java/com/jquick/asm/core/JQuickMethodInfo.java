package com.jquick.asm.core;

import com.jquick.asm.util.JQuickAccessUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * asm-core 方法信息容器。
 *
 * <p>记录方法的访问修饰符、名称、描述符、签名、抛出异常、参数名、注解列表。
 * 不直接持有方法字节码指令；指令级编辑由 {@code asm-enhance} 模块负责。
 *
 * <h3>使用示例</h3>
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
     * 方法名
     */
    private final String name;
    /**
     * 方法描述符，如 {@code "(ILjava/lang/String;)V"}
     */
    private final String descriptor;
    /**
     * 方法泛型签名，无泛型则为 null
     */
    private final String signature;
    /**
     * 方法抛出异常的内部名列表，如 {@code ["java/io/IOException"]}
     */
    private final List<String> exceptions = new ArrayList<>();
    /**
     * 方法参数名列表（若调试信息被保留）
     */
    private final List<String> parameterNames = new ArrayList<>();
    /**
     * 方法注解列表
     */
    private final List<JQuickAnnotationInfo> annotations = new ArrayList<>();
    /**
     * 访问修饰符
     */
    private int access;

    public JQuickMethodInfo(int access, String name, String descriptor, String signature) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("方法名不能为空");
        }
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("方法描述符不能为空");
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
     * 是否为构造方法。
     */
    public boolean isConstructor() {
        return JQuickAccessUtil.isConstructor(name);
    }

    /**
     * 是否为静态初始化块。
     */
    public boolean isStaticInitializer() {
        return JQuickAccessUtil.isStaticInitializer(name);
    }

    /**
     * 是否为 native 方法。
     */
    public boolean isNative() {
        return JQuickAccessUtil.isNative(access);
    }

    /**
     * 是否为 abstract 方法（无方法体，不可插桩）。
     */
    public boolean isAbstract() {
        return JQuickAccessUtil.isAbstract(access);
    }

    /**
     * 是否可安全修改：排除构造方法、native、abstract、静态初始化块。
     * 构造方法破坏会影响对象初始化语义，按安全规范禁止插桩。
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
