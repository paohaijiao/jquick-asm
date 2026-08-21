package com.jquick.asm.core;

import com.jquick.asm.util.JQuickAccessUtil;

import java.util.*;

/**
 * ASM‑core class metadata container.
 *
 * <p>Holds full class structure: version, access modifiers, class name, superclass,
 * interfaces, fields, methods and annotations. Populated by {@code asm‑reader},
 * and consumed by {@code asm‑writer}/{@code asm‑enhance}.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * JQuickClassInfo info = JQuickClassReaderTool.read(MyClass.class);
 * String superName = info.getSuperName();
 * List<JQuickMethodInfo> methods = info.getMethods();
 * for (JQuickMethodInfo m : methods) {
 *     System.out.println(m);
 * }
 * }</pre>
 */
public class JQuickClassInfo {

    /**
     * Internal names of directly‑implemented interfaces.
     */
    private final List<String> interfaces = new ArrayList<>();
    /**
     * fields List.
     */
    private final List<JQuickFieldInfo> fields = new ArrayList<>();
    /**
     * method List.
     */
    private final List<JQuickMethodInfo> methods = new ArrayList<>();
    /**
     * annotation List
     */
    private final List<JQuickAnnotationInfo> annotations = new ArrayList<>();
    /**
     * Bytecode version, e.g. {@link org.objectweb.asm.Opcodes#V1_8}.
     */
    private int version;
    /**
     * Access modifiers.
     */
    private int access;
    /**
     * Class internal name, e.g. {@code "com/demo/Foo"}.
     */
    private String internalName;
    /**
     * 父类内部名，如 {@code "java/lang/Object"}
     */
    private String superName;
    /**
     * Superclass internal name, e.g. {@code "java/lang/Object"}.
     */
    private String signature;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getAccess() {
        return access;
    }

    public void setAccess(int access) {
        this.access = access;
    }

    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    /**
     * Gets fully‑qualified class name (dot‑separated).
     */
    public String getClassName() {
        return internalName == null ? null : internalName.replace('/', '.');
    }

    public String getSuperName() {
        return superName;
    }

    public void setSuperName(String superName) {
        this.superName = superName;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public List<JQuickFieldInfo> getFields() {
        return fields;
    }

    public List<JQuickMethodInfo> getMethods() {
        return methods;
    }

    public List<JQuickAnnotationInfo> getAnnotations() {
        return annotations;
    }

    public void addInterface(String internalName) {
        if (internalName != null && !interfaces.contains(internalName)) {
            interfaces.add(internalName);
        }
    }

    public void addField(JQuickFieldInfo field) {
        fields.add(field);
    }

    public void addMethod(JQuickMethodInfo method) {
        methods.add(method);
    }

    public void addAnnotation(JQuickAnnotationInfo annotation) {
        annotations.add(annotation);
    }

    /**
     * Finds method by name and descriptor.
     *
     * @param name       method name
     * @param descriptor method descriptor
     * @return matched method info, {@code null} if not found
     */
    public JQuickMethodInfo findMethod(String name, String descriptor) {
        for (JQuickMethodInfo m : methods) {
            if (m.getName().equals(name) && m.getDescriptor().equals(descriptor)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Finds field by name.
     */
    public JQuickFieldInfo findField(String name) {
        for (JQuickFieldInfo f : fields) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public List<JQuickFieldInfo> fieldsView() {
        return Collections.unmodifiableList(fields);
    }

    public List<JQuickMethodInfo> methodsView() {
        return Collections.unmodifiableList(methods);
    }

    public List<JQuickAnnotationInfo> annotationsView() {
        return Collections.unmodifiableList(annotations);
    }

    /**
     * 是否为接口。
     */
    public boolean isInterface() {
        return JQuickAccessUtil.isInterface(access);
    }

    @Override
    public String toString() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("class", internalName);
        map.put("super", superName);
        map.put("interfaces", interfaces);
        map.put("fields", fields.size());
        map.put("methods", methods.size());
        map.put("annotations", annotations.size());
        return "JQuickClassInfo" + map;
    }
}
