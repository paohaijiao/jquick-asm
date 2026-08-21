package com.jquick.asm.util;

import org.objectweb.asm.Type;

/**
 * asm‑util type conversion utility.
 *
 * <p>Encapsulates bidirectional conversions between ASM {@link Type}, Java reflection {@link Class},
 * field descriptors and internal names. Uniformly handles descriptor differences for primitives, arrays and reference types.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Class -> field descriptor
 * String desc = JQuickTypeUtil.toDescriptor(String.class);           // "Ljava/lang/String;"
 * // Descriptor -> internal name
 * String internal = JQuickTypeUtil.toInternalName(desc);             // "java/lang/String"
 * // Method descriptor
 * String mDesc = JQuickTypeUtil.methodDescriptor("(I)V");            // validates and returns input
 * }</pre>
 */

public final class JQuickTypeUtil {

    private JQuickTypeUtil() {
    }

    /**
     * Convert Java {@link Class} to ASM type descriptor.
     *
     * @param clazz Java type
     * @return Type descriptor, e.g. {@code "Ljava/lang/String;"}、{@code "I"}
     */
    public static String toDescriptor(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz 不能为 null");
        }
        return Type.getDescriptor(clazz);
    }

    /**
     * Convert Java {@link Class} to internal name.
     *
     * @param clazz Java type
     * @return Internal name, e.g. {@code "java/lang/String"}
     */
    public static String toInternalName(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz cannot be null");
        }
        return Type.getInternalName(clazz);
    }

    /**
     * Convert fully qualified class name (dot-separated) to internal name (slash-separated).
     *
     * @param className Fully qualified class name
     * @return Internal name, e.g. {@code "java/lang/String"}
     */
    public static String classNameToInternal(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className cannot be empty");
        }
        return className.replace('.', '/');
    }

    /**
     * Convert internal name (slash-separated) to fully qualified class name (dot-separated).
     *
     * @param internalName Internal name, e.g. {@code "java/lang/String"}
     * @return Fully qualified class name, e.g. {@code "java.lang.String"}
     */
    public static String internalToClassName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            throw new IllegalArgumentException("internalName cannot be empty");
        }
        // Descriptor form (like Ljava/lang/String;) need to remove leading/trailing markers
        if (internalName.startsWith("L") && internalName.endsWith(";")) {
            internalName = internalName.substring(1, internalName.length() - 1);
        }
        return internalName.replace('/', '.');
    }

    /**
     * Convert type descriptor to internal name (object type).
     *
     * @param descriptor Type descriptor, e.g. {@code "Ljava/lang/String;"}
     * @return Internal name, e.g. {@code "java/lang/String"}
     */
    public static String toInternalName(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("descriptor cannot be empty");
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
     * Construct method descriptor: generate from parameter type list.
     *
     * @param paramClasses Parameter type array
     * @return Method descriptor, e.g. {@code "(ILjava/lang/String;)V"}
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
     * Construct method descriptor: generate from return type and parameter type list.
     *
     * @param returnType   Return type, void pass {@code Void.class} or null
     * @param paramClasses Parameter type array
     * @return Method descriptor
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
     * Parse method descriptor, return parameter type descriptor array.
     *
     * @param methodDescriptor Method descriptor, e.g. {@code "(IJLjava/lang/String;)V"}
     * @return Parameter type descriptor array, e.g. {@code ["I","J","Ljava/lang/String;"]}
     */
    public static String[] parseArgumentDescriptors(String methodDescriptor) {
        if (methodDescriptor == null) {
            throw new IllegalArgumentException("methodDescriptor cannot be null");
        }
        Type[] argTypes = Type.getArgumentTypes(methodDescriptor);
        String[] result = new String[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            result[i] = argTypes[i].getDescriptor();
        }
        return result;
    }

    /**
     * Parse method descriptor, return return type descriptor.
     *
     * @param methodDescriptor Method descriptor
     * @return Return type descriptor, e.g. {@code "V"} or {@code "Ljava/lang/String;"}
     */
    public static String parseReturnDescriptor(String methodDescriptor) {
        if (methodDescriptor == null) {
            throw new IllegalArgumentException("methodDescriptor cannot be null");
        }
        return Type.getReturnType(methodDescriptor).getDescriptor();
    }

    /**
     * Check if type descriptor is primitive type (non-object, non-array).
     *
     * @param descriptor Type descriptor
     * @return true if is primitive type
     */
    public static boolean isPrimitive(String descriptor) {
        if (descriptor == null || descriptor.length() != 1) {
            return false;
        }
        char c = descriptor.charAt(0);
        return c == 'Z' || c == 'B' || c == 'C' || c == 'S' || c == 'I' || c == 'J' || c == 'F' || c == 'D' || c == 'V';
    }
}
