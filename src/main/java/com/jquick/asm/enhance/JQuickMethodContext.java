package com.jquick.asm.enhance;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Method enhancement context for asm‑enhance.
 *
 * <p>Exposes current method metadata and the underlying {@link MethodVisitor} to consumers
 * within {@link JQuickMethodAdvice} hooks, enabling on‑demand bytecode instruction generation.
 *
 * <h3>Usage Example</h3>
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
     * Underlying method visitor for emitting bytecode instructions.
     */
    public MethodVisitor methodVisitor() {
        return methodVisitor;
    }

    /**
     * Method access modifiers.
     */
    public int access() {
        return access;
    }

    /**
     * Method name.
     */
    public String name() {
        return name;
    }

    /**
     * Method descriptor.
     */
    public String descriptor() {
        return descriptor;
    }

    /**
     * Internal name of the enclosing class, e.g. {@code "com/demo/Foo"}.
     */
    public String ownerInternalName() {
        return ownerInternalName;
    }

    /**
     * Whether this is a static method.
     */
    public boolean isStatic() {
        return isStatic;
    }

    /**
     * Whether this is a constructor.
     *
     * @return {@code true} if the method is a constructor
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * Loads {@code this} onto the operand stack (only for non‑static methods).
     * Throws an exception if invoked for static methods.
     */
    public void loadThis() {
        if (isStatic) {
            throw new IllegalStateException("Static methods cannot load this reference : " + name);
        }
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    }
}
