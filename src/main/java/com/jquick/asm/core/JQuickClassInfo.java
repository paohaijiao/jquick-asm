package com.jquick.asm.core;

import com.jquick.asm.util.JQuickAccessUtil;

import java.util.*;

/**
 * asm-core 类信息容器。
 *
 * <p>记录一个类的完整结构信息：版本、访问修饰符、类名、父类、接口、字段、方法、注解。
 * 由 {@code asm-reader} 解析填充，供 {@code asm-writer}/{@code asm-enhance} 使用。
 *
 * <h3>使用示例</h3>
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
     * 直接实现接口的内部名列表
     */
    private final List<String> interfaces = new ArrayList<>();
    /**
     * 字段列表
     */
    private final List<JQuickFieldInfo> fields = new ArrayList<>();
    /**
     * 方法列表
     */
    private final List<JQuickMethodInfo> methods = new ArrayList<>();
    /**
     * 类注解列表
     */
    private final List<JQuickAnnotationInfo> annotations = new ArrayList<>();
    /**
     * 字节码版本，如 {@link org.objectweb.asm.Opcodes#V1_8}
     */
    private int version;
    /**
     * 访问修饰符
     */
    private int access;
    /**
     * 类内部名，如 {@code "com/demo/Foo"}
     */
    private String internalName;
    /**
     * 父类内部名，如 {@code "java/lang/Object"}
     */
    private String superName;
    /**
     * 泛型签名
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
     * 获取全限定类名（点分隔）。
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
     * 按名称+描述符查找方法。
     *
     * @param name       方法名
     * @param descriptor 方法描述符
     * @return 找到的方法信息，否则 null
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
     * 按名称查找字段。
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
