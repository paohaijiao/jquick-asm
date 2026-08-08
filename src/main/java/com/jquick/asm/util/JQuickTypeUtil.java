package com.jquick.asm.util;

import org.objectweb.asm.Type;

/**
 * asm-util 类型转换工具。
 *
 * <p>封装 ASM {@link Type} 与 Java 反射 {@link Class}、描述符、内部名之间的互相转换，
 * 统一处理基础类型、数组、对象类型的描述符差异。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 类 -> 描述符
 * String desc = JQuickTypeUtil.toDescriptor(String.class);           // "Ljava/lang/String;"
 * // 描述符 -> 内部名
 * String internal = JQuickTypeUtil.toInternalName(desc);             // "java/lang/String"
 * // 方法描述符
 * String mDesc = JQuickTypeUtil.methodDescriptor("(I)V");            // 校验并返回
 * }</pre>
 */
public final class JQuickTypeUtil {

    private JQuickTypeUtil() {
    }

    /**
     * 将 Java {@link Class} 转换为 ASM 类型描述符。
     *
     * @param clazz Java 类型，不能为 null
     * @return 类型描述符，如 {@code "Ljava/lang/String;"}、{@code "I"}、{@code "[I"}
     */
    public static String toDescriptor(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz 不能为 null");
        }
        return Type.getDescriptor(clazz);
    }

    /**
     * 将 Java {@link Class} 转换为内部名（仅适用于对象/数组类型）。
     *
     * @param clazz Java 类型
     * @return 内部名，如 {@code "java/lang/String"}
     */
    public static String toInternalName(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz 不能为 null");
        }
        return Type.getInternalName(clazz);
    }

    /**
     * 将全限定类名（点分隔）转换为内部名（斜杠分隔）。
     *
     * @param className 全限定类名，如 {@code "java.lang.String"}
     * @return 内部名，如 {@code "java/lang/String"}
     */
    public static String classNameToInternal(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className 不能为空");
        }
        return className.replace('.', '/');
    }

    /**
     * 将内部名（斜杠分隔）转换为全限定类名（点分隔）。
     *
     * @param internalName 内部名，如 {@code "java/lang/String"}
     * @return 全限定类名，如 {@code "java.lang.String"}
     */
    public static String internalToClassName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            throw new IllegalArgumentException("internalName 不能为空");
        }
        // 描述符形式（如 Ljava/lang/String;）需要去掉首尾标记
        if (internalName.startsWith("L") && internalName.endsWith(";")) {
            internalName = internalName.substring(1, internalName.length() - 1);
        }
        return internalName.replace('/', '.');
    }

    /**
     * 将类型描述符转换为内部名（对象类型）。
     *
     * @param descriptor 类型描述符，如 {@code "Ljava/lang/String;"}
     * @return 内部名，如 {@code "java/lang/String"}；基础类型描述符返回其描述符本身
     */
    public static String toInternalName(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("descriptor 不能为空");
        }
        Type type = Type.getType(descriptor);
        if (type.getSort() == Type.OBJECT) {
            return type.getInternalName();
        }
        if (type.getSort() == Type.ARRAY) {
            return type.getDescriptor();
        }
        return type.getDescriptor();
    }

    /**
     * 构造方法描述符：根据参数类型列表生成。
     *
     * @param paramClasses 参数类型数组
     * @return 方法描述符，如 {@code "(ILjava/lang/String;)V"}
     */
    public static String methodDescriptor(Class<?>... paramClasses) {
        if (paramClasses == null) {
            paramClasses = new Class<?>[0];
        }
        Type[] argTypes = new Type[paramClasses.length];
        for (int i = 0; i < paramClasses.length; i++) {
            argTypes[i] = Type.getType(paramClasses[i]);
        }
        return Type.getMethodDescriptor(Type.VOID_TYPE, argTypes);
    }

    /**
     * 根据返回值类型与参数类型构造方法描述符。
     *
     * @param returnType   返回值类型，void 传 {@code Void.class} 或 null
     * @param paramClasses 参数类型数组
     * @return 方法描述符
     */
    public static String methodDescriptor(Class<?> returnType, Class<?>... paramClasses) {
        Type ret = (returnType == null || returnType == Void.class)
                ? Type.VOID_TYPE : Type.getType(returnType);
        if (paramClasses == null) {
            paramClasses = new Class<?>[0];
        }
        Type[] argTypes = new Type[paramClasses.length];
        for (int i = 0; i < paramClasses.length; i++) {
            argTypes[i] = Type.getType(paramClasses[i]);
        }
        return Type.getMethodDescriptor(ret, argTypes);
    }

    /**
     * 解析方法描述符，返回参数类型描述符数组。
     *
     * @param methodDescriptor 方法描述符，如 {@code "(IJLjava/lang/String;)V"}
     * @return 参数类型描述符数组，如 {@code ["I","J","Ljava/lang/String;"]}
     */
    public static String[] parseArgumentDescriptors(String methodDescriptor) {
        if (methodDescriptor == null) {
            throw new IllegalArgumentException("methodDescriptor 不能为 null");
        }
        Type[] argTypes = Type.getArgumentTypes(methodDescriptor);
        String[] result = new String[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            result[i] = argTypes[i].getDescriptor();
        }
        return result;
    }

    /**
     * 获取方法描述符的返回值描述符。
     *
     * @param methodDescriptor 方法描述符
     * @return 返回值描述符，如 {@code "V"}、{@code "Ljava/lang/String;"}
     */
    public static String parseReturnDescriptor(String methodDescriptor) {
        if (methodDescriptor == null) {
            throw new IllegalArgumentException("methodDescriptor 不能为 null");
        }
        return Type.getReturnType(methodDescriptor).getDescriptor();
    }

    /**
     * 判断描述符是否为基础类型（非对象、非数组）。
     *
     * @param descriptor 类型描述符
     * @return true 表示是基础类型
     */
    public static boolean isPrimitive(String descriptor) {
        if (descriptor == null || descriptor.length() != 1) {
            return false;
        }
        char c = descriptor.charAt(0);
        return c == 'Z' || c == 'B' || c == 'C' || c == 'S' || c == 'I' || c == 'J' || c == 'F' || c == 'D' || c == 'V';
    }
}
