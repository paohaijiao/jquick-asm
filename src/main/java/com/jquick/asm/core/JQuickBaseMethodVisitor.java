package com.jquick.asm.core;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * asm-core 基础 MethodVisitor 封装：方法字节码编辑的统一基类。
 *
 * <p>提供三个核心钩子，屏蔽原始 Visitor 繁琐的指令级处理：
 * <ul>
 *   <li>{@link #onMethodEnter()}：方法头部插入代码（构造方法在 super 调用之后）。</li>
 *   <li>{@link #onMethodExit(int)}：方法每条返回指令之前插入逻辑（RETURN/IRETURN 等）。</li>
 *   <li>{@link #wrapWithTryCatch(Label, Label, Label)}：异常捕获环绕（try-catch 埋点）。</li>
 * </ul>
 *
 * <p>本类自行实现返回指令拦截，不依赖 {@code asm-commons} 的 AdviceAdapter，
 * 保持最小依赖（仅 asm-core）。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
 * JQuickBaseMethodVisitor adv = new JQuickBaseMethodVisitor(ASM9, mv, ACC_PUBLIC, "run", "()V") {
 *     {@literal @}Override protected void onMethodEnter() {
 *         mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
 *         mv.visitLdcInsn("enter");
 *         mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
 *                 "(Ljava/lang/String;)V", false);
 *     }
 * };
 * }</pre>
 */
public class JQuickBaseMethodVisitor extends MethodVisitor {

    /**
     * 方法访问修饰符
     */
    protected final int access;

    /**
     * 方法名
     */
    protected final String name;

    /**
     * 方法描述符
     */
    protected final String descriptor;

    /**
     * 方法是否为静态
     */
    protected final boolean isStatic;

    /**
     * 是否已调用过 visitCode（用于保证 onMethodEnter 只触发一次）
     */
    private boolean codeVisited = false;

    /**
     * 构造方法中是否已完成 super/this 调用
     */
    private boolean constructorSuperCalled = true;

    public JQuickBaseMethodVisitor(int api, MethodVisitor mv, int access, String name, String descriptor) {
        super(api, mv);
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
        // 构造方法需要等待 super 调用后再插桩；非构造方法直接允许
        this.constructorSuperCalled = !"<init>".equals(name);
    }

    /**
     * 方法头部插入逻辑钩子。子类覆写以在方法体最前面插入指令。
     * 默认空实现。本方法在第一条原始指令之前被调用一次。
     */
    protected void onMethodEnter() {
    }

    /**
     * 方法返回前插入逻辑钩子。每条返回指令（RETURN/IRETURN/ARETURN 等）触发一次。
     *
     * @param opcode 返回指令操作码，如 {@link Opcodes#RETURN}、{@link Opcodes#ARETURN}
     */
    protected void onMethodExit(int opcode) {
    }

    /**
     * 方法出口钩子（含异常退出）。子类可覆写以在方法结束（正常或异常）时执行清理。
     * 默认空实现。
     */
    protected void onMethodFinished() {
    }

    /**
     * 是否需要在异常路径上触发 onMethodExit。
     * 默认 false：异常退出不触发 onMethodExit，避免埋点异常被吞。
     * 若启用 try-catch 环绕，则由 {@link #wrapWithTryCatch} 统一处理。
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
        // 构造方法：检测 super()/this() 调用，完成后再插桩头部
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
        // 拦截所有返回指令，在返回前插入逻辑
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
     * 在 [start, end) 指令区间外包裹 try-catch：捕获 {@code exceptionType} 异常，
     * 跳转到 {@code handler} 标签处理。用于方法异常捕获环绕埋点。
     *
     * <p>调用方式：先记录 start 标签 → 写入方法体 → 记录 end 标签 → 写入 handler 标签块，
     * 然后调用本方法注册 try-catch 表项。
     *
     * @param start         try 起始标签（位于方法体第一行之前）
     * @param end           try 结束标签（位于方法体最后一行之后、handler 之前）
     * @param handler       catch 处理块起始标签
     * @param exceptionType 捕获的异常内部名，如 {@code "java/lang/Throwable"}；
     *                      null 表示 catch all（finally 语义）
     */
    public void wrapWithTryCatch(Label start, Label end, Label handler, String exceptionType) {
        visitTryCatchBlock(start, end, handler, exceptionType);
    }

    /**
     * 注册一个捕获所有异常的 try-catch-finally 环绕。
     * 等价于 {@code wrapWithTryCatch(start, end, handler, "java/lang/Throwable")}。
     */
    public void wrapWithTryCatchFinally(Label start, Label end, Label handler) {
        visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
    }

    /**
     * 加载 this 到操作数栈顶（仅非静态方法可用）。
     */
    protected void loadThis() {
        if (isStatic) {
            throw new IllegalStateException("静态方法无法加载 this");
        }
        mv.visitVarInsn(Opcodes.ALOAD, 0);
    }

    /**
     * 加载方法参数到操作数栈。参数从局部变量表索引起：
     * 非静态方法从 1 开始（0 是 this），静态方法从 0 开始。
     *
     * @param index 参数序号（从 0 开始）
     */
    protected void loadArg(int index) {
        mv.visitVarInsn(Opcodes.ALOAD, argLocalIndex(index));
    }

    /**
     * 计算第 index 个参数在局部变量表的索引。
     */
    protected int argLocalIndex(int index) {
        // 简化实现：默认按引用类型处理；精确计算由调用方保证
        return (isStatic ? 0 : 1) + index;
    }
}
