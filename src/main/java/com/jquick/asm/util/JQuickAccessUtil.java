package com.jquick.asm.util;

import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * Access flag utility for asm‑util.
 *
 * <p>Converts between ASM access flags (int bit‑mask) and Java keyword strings for human‑readable output and configuration.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
 * String text = JQuickAccessUtil.toString(access);   // "public static final"
 * boolean isStatic = JQuickAccessUtil.isStatic(access);
 * }</pre>
 */
public final class JQuickAccessUtil {

    private JQuickAccessUtil() {
    }

    /**
     * Converts access flags to Java keyword string.
     *
     * @param access ASM access flags
     * @return keyword string, e.g. {@code "public static final"}
     */
    public static String toString(int access) {
        List<String> list = new ArrayList<>();
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            list.add("public");
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            list.add("private");
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            list.add("protected");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            list.add("static");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            list.add("final");
        }
        if ((access & Opcodes.ACC_SUPER) != 0) {
            list.add("super");
        }
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            list.add("synchronized");
        }
        if ((access & Opcodes.ACC_VOLATILE) != 0) {
            list.add("volatile");
        }
        if ((access & Opcodes.ACC_BRIDGE) != 0) {
            list.add("bridge");
        }
        if ((access & Opcodes.ACC_VARARGS) != 0) {
            list.add("varargs");
        }
        if ((access & Opcodes.ACC_TRANSIENT) != 0) {
            list.add("transient");
        }
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            list.add("native");
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            list.add("interface");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            list.add("abstract");
        }
        if ((access & Opcodes.ACC_STRICT) != 0) {
            list.add("strictfp");
        }
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            list.add("synthetic");
        }
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            list.add("annotation");
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            list.add("enum");
        }
        return list.isEmpty() ? "default" : String.join(" ", list);
    }

    /**
     * Returns {@code true} if the given access flags represent {@code public}.
     *
     * @param access ASM access flags
     */
    public static boolean isPublic(int access) {
        return (access & Opcodes.ACC_PUBLIC) != 0;
    }

    /**
     * Returns {@code true} if the given access flags represent {@code private}.
     *
     * @param access ASM access flags
     */
    public static boolean isPrivate(int access) {
        return (access & Opcodes.ACC_PRIVATE) != 0;
    }

    /**
     * Returns {@code true} if the given access flags represent {@code protected}.
     *
     * @param access ASM access flags
     */
    public static boolean isProtected(int access) {
        return (access & Opcodes.ACC_PROTECTED) != 0;
    }

    /**
     * Returns {@code true} if the given access flags represent {@code static}.
     *
     * @param access ASM access flags
     */
    public static boolean isStatic(int access) {
        return (access & Opcodes.ACC_STATIC) != 0;
    }

    /**
     * Returns {@code true} if the given access flags represent {@code final}.
     *
     * @param access ASM access flags
     */
    public static boolean isFinal(int access) {
        return (access & Opcodes.ACC_FINAL) != 0;
    }

    /**
     * Returns {@code true} if the given access flags represent {@code abstract}.
     *
     * @param access ASM access flags
     */
    public static boolean isAbstract(int access) {
        return (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    /**
     * Returns {@code true} if the given access flags represent a native method (protected by security restrictions; modifications are prohibited).
     *
     * @param access ASM access flags
     */
    public static boolean isNative(int access) {
        return (access & Opcodes.ACC_NATIVE) != 0;
    }

    /**
     * Returns {@code true} if the given access flags represent a synchronized method.
     *
     * @param access ASM access flags
     */
    public static boolean isSynchronized(int access) {
        return (access & Opcodes.ACC_SYNCHRONIZED) != 0;
    }

    /**
     * Returns {@code true} if the given access flags represent an interface.
     *
     * @param access ASM access flags
     */
    public static boolean isInterface(int access) {
        return (access & Opcodes.ACC_INTERFACE) != 0;
    }

    /**
     * Returns {@code true} if the method is a constructor (method name is {@code <init>}).
     *
     * @param name Method name
     * @return {@code true} if it is a constructor
     */
    public static boolean isConstructor(String name) {
        return JQuickAsmConstants.INIT.equals(name);
    }

    /**
     * Returns {@code true} if the method is a static initializer block (method name is {@code <clinit>}).
     *
     * @param name Method name
     * @return {@code true} if it is a static initializer block
     */
    public static boolean isStaticInitializer(String name) {
        return JQuickAsmConstants.CLINIT.equals(name);
    }

    /**
     * Modifies the visibility of access flags: clears original visibility bits and sets the new visibility.
     *
     * @param access     Original access flags
     * @param visibility New visibility; valid values are {@link Opcodes#ACC_PUBLIC},
     *                   {@link Opcodes#ACC_PROTECTED}, {@link Opcodes#ACC_PRIVATE}, or 0 for package‑private access.
     * @return Modified access flags
     */
    public static int changeVisibility(int access, int visibility) {
        // Clear original visibility bits
        access &= ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        access |= visibility;
        return access;
    }

    /**
     * Adds the given flag bit.
     *
     * @param access Original access flags
     * @param flag   Flag bit to add
     * @return Modified access flags
     */
    public static int addFlag(int access, int flag) {
        return access | flag;
    }

    /**
     * Removes the given flag bit.
     *
     * @param access Original access flags
     * @param flag   Flag bit to remove
     * @return Modified access flags
     */
    public static int removeFlag(int access, int flag) {
        return access & ~flag;
    }
}
