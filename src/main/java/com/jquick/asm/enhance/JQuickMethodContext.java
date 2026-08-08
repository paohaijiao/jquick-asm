package com.jquick.asm.enhance;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * asm-enhance 方法增强上下文。
 *
 * <p>在 {@link JQuickMethodAdvice} 钩子中向使用方暴露当前方法的元信息与底层
 * {@link MethodVisitor}，便于按需生成字节码指令。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * JQuickMethodAdvice advice = new JQuickMethodAdvice() {
 *     {@literal @}Override public void onEnter(JQuickMethodContext ctx) {
 *         MethodVisitor mv = ctx.methodVisitor();
 *         mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
 *         mv.visitLdcInsn("enter " + ctx.name());
 *         mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
 *                 "(Ljava/lang/String;)V", false);
 *     }
 * };
 * }</pre>
 */
public final class JQuickMethodContext {

    private final MethodVisitor methodVisitor;

    private final int access;
    private final String name;
    private final String descriptor;
    private final String ownerInternalName;
    private final boolean isStatic;

    public JQuickMethodContext(MethodVisitor methodVisitor, int access, String name, String descriptor, String ownerInternalName) {
        this.methodVisitor = methodVisitor;
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
        this.ownerInternalName = ownerInternalName;
        this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
    }

    /**
     * 底层方法访问器，用于发射指令。
     */
    public MethodVisitor methodVisitor() {
        return methodVisitor;
    }

    /**
     * 方法访问修饰符。
     */
    public int access() {
        return access;
    }

    /**
     * 方法名。
     */
    public String name() {
        return name;
    }

    /**
     * 方法描述符。
     */
    public String descriptor() {
        return descriptor;
    }

    /**
     * 所属类的内部名，如 {@code "com/demo/Foo"}。
     */
    public String ownerInternalName() {
        return ownerInternalName;
    }

    /**
     * 是否为静态方法。
     */
    public boolean isStatic() {
        return isStatic;
    }

    /**
     * 是否为构造方法。
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * 加载 this 到操作数栈（仅非静态方法）。静态方法调用会抛异常。
     */
    public void loadThis() {
        if (isStatic) {
            throw new IllegalStateException("静态方法无法加载 this: " + name);
        }
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    }
}
