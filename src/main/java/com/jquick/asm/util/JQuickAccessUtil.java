package com.jquick.asm.util;

import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * asm-util 访问修饰符工具。
 *
 * <p>将 ASM 的 access 标志位（int 位掩码）与 Java 关键字字符串互转，便于人类阅读与配置。
 *
 * <h3>使用示例</h3>
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
     * 将 access 标志位转换为 Java 关键字字符串。
     *
     * @param access ASM access 标志位
     * @return 关键字字符串，如 {@code "public static final"}
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
     * 是否为 public。
     */
    public static boolean isPublic(int access) {
        return (access & Opcodes.ACC_PUBLIC) != 0;
    }

    /**
     * 是否为 private。
     */
    public static boolean isPrivate(int access) {
        return (access & Opcodes.ACC_PRIVATE) != 0;
    }

    /**
     * 是否为 protected。
     */
    public static boolean isProtected(int access) {
        return (access & Opcodes.ACC_PROTECTED) != 0;
    }

    /**
     * 是否为 static。
     */
    public static boolean isStatic(int access) {
        return (access & Opcodes.ACC_STATIC) != 0;
    }

    /**
     * 是否为 final。
     */
    public static boolean isFinal(int access) {
        return (access & Opcodes.ACC_FINAL) != 0;
    }

    /**
     * 是否为 abstract。
     */
    public static boolean isAbstract(int access) {
        return (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    /**
     * 是否为 native 方法（受安全限制保护，禁止修改）。
     */
    public static boolean isNative(int access) {
        return (access & Opcodes.ACC_NATIVE) != 0;
    }

    /**
     * 是否为 synchronized 方法。
     */
    public static boolean isSynchronized(int access) {
        return (access & Opcodes.ACC_SYNCHRONIZED) != 0;
    }

    /**
     * 是否为接口。
     */
    public static boolean isInterface(int access) {
        return (access & Opcodes.ACC_INTERFACE) != 0;
    }

    /**
     * 是否为构造方法（方法名为 {@code <init>}）。
     *
     * @param name 方法名
     * @return true 表示是构造方法
     */
    public static boolean isConstructor(String name) {
        return JQuickAsmConstants.INIT.equals(name);
    }

    /**
     * 是否为静态初始化块（方法名为 {@code <clinit>}）。
     */
    public static boolean isStaticInitializer(String name) {
        return JQuickAsmConstants.CLINIT.equals(name);
    }

    /**
     * 修改 access 的可见性：清除原可见性位并设置新可见性。
     *
     * @param access     原 access
     * @param visibility 新可见性，取值为 {@link Opcodes#ACC_PUBLIC}、
     *                   {@link Opcodes#ACC_PROTECTED}、{@link Opcodes#ACC_PRIVATE} 或 0（包级）
     * @return 修改后的 access
     */
    public static int changeVisibility(int access, int visibility) {
        // 清除原可见性位
        access &= ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        access |= visibility;
        return access;
    }

    /**
     * 添加标志位。
     *
     * @param access 原 access
     * @param flag   要添加的标志位
     * @return 修改后的 access
     */
    public static int addFlag(int access, int flag) {
        return access | flag;
    }

    /**
     * 移除标志位。
     *
     * @param access 原 access
     * @param flag   要移除的标志位
     * @return 修改后的 access
     */
    public static int removeFlag(int access, int flag) {
        return access & ~flag;
    }
}
