package com.jquick.asm.core;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM‑core base MethodVisitor wrapper: unified base for byte‑code editing.
 * <p>Expose three high‑level hooks to hide low‑level visitor complexity:
 * <ul>
 * <li>{@link #onMethodEnter()}: Inject code at method start (after super‑call for constructors).</li>
 * <li>{@link #onMethodExit(int)}: Inject logic before every return instruction (RETURN / IRETURN etc).</li>
 * </ul>
 *
 * <p>Intercept return instructions internally, no {@code asm‑commons} AdviceAdapter dependency; depends only on asm‑core.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
 * JQuickBaseMethodVisitor adv = new JQuickBaseMethodVisitor(ASM9, mv, ACC_PUBLIC, "run", "()V") {
 *     @Override protected void onMethodEnter() {
 *         mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
 *         mv.visitLdcInsn("enter");
 *         mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
 *     }
 * };
 * }</pre>
 */

public class JQuickBaseMethodVisitor extends MethodVisitor {

    /**
     * Method access modifier
     */
    protected final int access;

    /**
     * Method name
     */
    protected final String name;

    /**
     * Method Descriptor
     */
    protected final String descriptor;

    /**
     * Is the method static
     */
    protected final boolean isStatic;

    /**
     * Have you called visitCode (to ensure that onMethodEnter is only triggered once)
     */
    private boolean codeVisited = false;

    /**
     * Has the super/this call been completed in the constructor method
     */
    private boolean constructorSuperCalled = true;

    public JQuickBaseMethodVisitor(int api, MethodVisitor mv, int access, String name, String descriptor) {
        super(api, mv);
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
        //The construction method needs to wait for the super call before inserting the stake; Non construction methods directly allow
        this.constructorSuperCalled = !"<init>".equals(name);
    }

    /**
     * Hook for method‑entry injection. Override to insert byte‑code at method start.
     * Called once before the first original instruction; default no‑op.
     */
    protected void onMethodEnter() {
    }

    /**
     * Hook for pre‑return injection. Triggered on every return instruction (RETURN, IRETURN, ARETURN etc).
     *
     * @param opcode return opcode, e.g. {@link Opcodes#RETURN}, {@link Opcodes#ARETURN}
     */
    protected void onMethodExit(int opcode) {
    }

    /**
     * Method‑exit hook (normal & exceptional exit). Override to execute cleanup logic on method termination.
     * Default no‑op implementation.
     */
    protected void onMethodFinished() {
    }

    /**
     * Whether to invoke {@code onMethodExit} on exception paths.
     * Default {@code false}: skip on exception to prevent swallowing instrumentation errors.
     * Enabling try‑catch wrapping is handled via {@link #wrapWithTryCatch}.
     */

    protected boolean interceptExceptionExit() {
        return false;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        codeVisited = true;
        if (constructorSuperCalled) {
            onMethodEnter();
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // Constructor handling: inject entry advice only after {@code super()} / {@code this()} invocation completes
        if ("<init>".equals(this.name) && !constructorSuperCalled && opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            constructorSuperCalled = true;
            onMethodEnter();
            return;
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInsn(int opcode) {
        //Intercept all return instructions and insert logic before returning
        if (isReturnOpcode(opcode)) {
            onMethodExit(opcode);
        }
        super.visitInsn(opcode);
        if (isReturnOpcode(opcode)) {
            onMethodFinished();
        }
    }

    /**
     * 判断是否为返回指令操作码。
     */
    protected boolean isReturnOpcode(int opcode) {
        switch (opcode) {
            case Opcodes.RETURN:
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.ATHROW:
                return true;
            default:
                return false;
        }
    }

    /**
     * Wraps a try‑catch block around the instruction range
     *[start, end): catches the {@code exceptionType}
     *- exception and jumps to the {@code handler} label for processing. Used for method‑level exception‑capture surrounding instrumentation.
     *- <p>Usage workflow: emit the start label → write method body → emit the end label → write the handler label block,
     *- then invoke this method to register the try‑catch table entry.
     *- @param start         Start label of the try block (placed before the first line of the method body)
     *- @param end           End label of the try block (placed after the last line of the method body and before the handler)
     *- @param handler       Start label of the catch‑handler block
     *- @param exceptionType Internal name of the exception to catch, e.g. {@code "java/lang/Throwable"};
     *- {@code null} means catch‑all (finally semantics)
     */
    public void wrapWithTryCatch(Label start, Label end, Label handler, String exceptionType) {
        visitTryCatchBlock(start, end, handler, exceptionType);
    }

    /**
     * Registers a try‑catch‑finally surround that catches all exceptions.
     * Equivalent to {@code wrapWithTryCatch(start, end, handler, "java/lang/Throwable")}.
     */

    public void wrapWithTryCatchFinally(Label start, Label end, Label handler) {
        visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
    }

    /**
     * Loads {@code this} onto the top of the operand stack (only valid for non‑static methods).
     */
    protected void loadThis() {
        if (isStatic) {
            throw new IllegalStateException("Static methods cannot load this");
        }
        mv.visitVarInsn(Opcodes.ALOAD, 0);
    }

    /**
     * Loads method arguments onto the operand stack. Arguments start from the local variable table index:
     * non‑static methods start at 1 (0 holds {@code this}), static methods start at 0.
     *
     * @param index Argument index (starting from 0)
     */
    protected void loadArg(int index) {
        mv.visitVarInsn(Opcodes.ALOAD, argLocalIndex(index));
    }

    /**
     * Computes the local variable table index for the {@code index}-th method argument.
     */
    protected int argLocalIndex(int index) {
        return (isStatic ? 0 : 1) + index;
    }
}
