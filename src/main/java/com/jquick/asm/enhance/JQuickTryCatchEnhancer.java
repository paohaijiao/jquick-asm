package com.jquick.asm.enhance;

import com.jquick.asm.core.JQuickBaseClassVisitor;
import com.jquick.asm.core.JQuickBaseMethodVisitor;
import com.jquick.asm.core.JQuickEnhanceGuard;
import com.jquick.asm.util.JQuickAsmConstants;
import com.jquick.asm.util.JQuickBytecodeUtil;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * asm-enhance 方法异常捕获环绕增强器。
 *
 * <p>对匹配方法用 try-catch 环绕整个方法体，实现「进入埋点 + 正常返回埋点 +
 * 异常捕获埋点」。基于 {@link JQuickBaseMethodVisitor} + {@link Label} try-catch 表项实现，
 * 不依赖 asm-commons。
 *
 * <h3>字节码结构</h3>
 * <pre>{@code
 * onEnter                              // try 之前
 * startLabel:
 *   <原始方法体>
 *   onExit                             // 返回前（在 try 内）
 *   return
 * endLabel:
 * handlerLabel:   // catch Throwable [startLabel, endLabel)
 *   onException   // 栈顶为异常，钩子需保留
 *   ATHROW        // 重抛
 * }</pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * byte[] enhanced = JQuickTryCatchEnhancer.from(bytes)
 *     .match(" risky ")
 *     .advice(JQuickTryCatchAdvice.builder()
 *         .onEnter(ctx -> Tracer.print(ctx, "enter"))
 *         .onException(ctx -> {
 *             MethodVisitor mv = ctx.methodVisitor();
 *             mv.visitInsn(Opcodes.DUP);
 *             mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/demo/Tracer",
 *                     "onError", "(Ljava/lang/Throwable;)V", false);
 *         })
 *         .build())
 *     .apply();
 * }</pre>
 */
public final class JQuickTryCatchEnhancer {

    private final byte[] source;

    private final List<Predicate<JQuickMethodEnhancer.MethodMeta>> matchers = new ArrayList<>();

    private JQuickTryCatchAdvice advice = JQuickTryCatchAdvice.builder().build();

    private JQuickEnhanceGuard guard = JQuickEnhanceGuard.DEFAULT;

    private boolean matchAll = false;

    private JQuickTryCatchEnhancer(byte[] source) {
        this.source = source;
    }

    /**
     * 从字节码创建增强器。
     */
    public static JQuickTryCatchEnhancer from(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes 不能为空");
        }
        return new JQuickTryCatchEnhancer(bytes);
    }

    /**
     * 设置安全守卫。
     */
    public JQuickTryCatchEnhancer guard(JQuickEnhanceGuard guard) {
        this.guard = guard == null ? JQuickEnhanceGuard.DEFAULT : guard;
        return this;
    }

    /**
     * 匹配所有可安全修改的方法。
     */
    public JQuickTryCatchEnhancer matchAll() {
        this.matchAll = true;
        return this;
    }

    /**
     * 按方法名匹配。
     */
    public JQuickTryCatchEnhancer match(String methodName) {
        matchers.add(m -> m.name.equals(methodName));
        return this;
    }

    /**
     * 按方法名+描述符精确匹配。
     */
    public JQuickTryCatchEnhancer match(String methodName, String descriptor) {
        matchers.add(m -> m.name.equals(methodName) && m.descriptor.equals(descriptor));
        return this;
    }

    /**
     * 自定义匹配谓词。
     */
    public JQuickTryCatchEnhancer match(Predicate<JQuickMethodEnhancer.MethodMeta> predicate) {
        if (predicate != null) {
            matchers.add(predicate);
        }
        return this;
    }

    /**
     * 设置环绕通知。
     */
    public JQuickTryCatchEnhancer advice(JQuickTryCatchAdvice advice) {
        this.advice = advice == null ? JQuickTryCatchAdvice.builder().build() : advice;
        return this;
    }

    /**
     * 执行增强并返回新字节码。
     */
    public byte[] apply() {
        ClassReader reader = new ClassReader(source);
        ClassWriter cw = new ClassWriter(reader, JQuickAsmConstants.WRITER_FLAGS);
        JQuickBaseClassVisitor cv = new JQuickBaseClassVisitor(JQuickAsmConstants.ASM_API, cw, null);
        cv.setMethodVisitorFactory((api, downstream, access, name, descriptor, signature,
                                    exceptions, classInfo) -> {
            String owner = classInfo == null ? null : classInfo.getInternalName();
            JQuickMethodEnhancer.MethodMeta meta = new JQuickMethodEnhancer.MethodMeta(access, name, descriptor);
            if (!shouldEnhance(meta)) {
                return downstream;
            }
            try {
                guard.checkModifiable(name, descriptor, access);
            } catch (SecurityException e) {
                return downstream;
            }
            final String ownerInternalName = owner;
            return new TryCatchMethodVisitor(api, downstream, access, name, descriptor,
                    ownerInternalName, advice);
        });
        reader.accept(cv, JQuickAsmConstants.PARSE_FLAGS);
        return cw.toByteArray();
    }

    /**
     * 执行增强并内存加载。
     */
    public Class<?> applyAndDefine(String className) {
        return JQuickBytecodeUtil.defineClass(className, apply());
    }

    /**
     * 执行增强并写出文件。
     */
    public String applyAndWrite(String className, String outputDir) {
        return JQuickBytecodeUtil.writeToFile(className, apply(), outputDir);
    }

    private boolean shouldEnhance(JQuickMethodEnhancer.MethodMeta meta) {
        if (matchAll) {
            return true;
        }
        for (Predicate<JQuickMethodEnhancer.MethodMeta> p : matchers) {
            if (p.test(meta)) {
                return true;
            }
        }
        return false;
    }

    /**
     * try-catch 环绕方法访问器。
     */
    private static final class TryCatchMethodVisitor extends JQuickBaseMethodVisitor {

        private final String ownerInternalName;
        private final JQuickTryCatchAdvice advice;

        private final Label startLabel = new Label();
        private final Label endLabel = new Label();
        private final Label handlerLabel = new Label();
        private boolean tryRegistered = false;
        private boolean handlerEmitted = false;

        TryCatchMethodVisitor(int api, MethodVisitor mv, int access, String name, String descriptor, String ownerInternalName, JQuickTryCatchAdvice advice) {
            super(api, mv, access, name, descriptor);
            this.ownerInternalName = ownerInternalName;
            this.advice = advice;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            // onEnter 在 try 块之前，不受捕获影响
            JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
            advice.onEnter(ctx);
            // 注册 try-catch：捕获 [startLabel, endLabel) 的 Throwable，跳转 handlerLabel
            mv.visitTryCatchBlock(startLabel, endLabel, handlerLabel, "java/lang/Throwable");
            tryRegistered = true;
            mv.visitLabel(startLabel);
        }

        @Override
        protected void onMethodEnter() {
            // onEnter 已在 visitCode 中手动处理，禁用基类默认头部插桩
        }

        @Override
        protected void onMethodExit(int opcode) {
            // ATHROW（原始方法主动抛出）：已在 try 内，由 handler 统一处理，不在此埋点
            if (opcode == Opcodes.ATHROW) {
                return;
            }
            // 正常返回前埋点
            JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
            advice.onExit(ctx);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // 必须在 visitMaxs 之前发出 catch 处理块指令，符合 ASM 调用顺序约束
            emitHandler();
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            // 兜底：若上游未调用 visitMaxs（理论上不会发生），在此补发
            emitHandler();
            super.visitEnd();
        }

        /**
         * 发出 try 块结束标签与 catch 处理块（仅一次）。
         */
        private void emitHandler() {
            if (!tryRegistered || handlerEmitted) {
                return;
            }
            // 标记 try 块结束
            mv.visitLabel(endLabel);
            // catch 处理块：栈顶为捕获的异常对象
            mv.visitLabel(handlerLabel);
            JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
            advice.onException(ctx);
            // 重抛异常（onException 钩子需保留异常在栈顶）
            mv.visitInsn(Opcodes.ATHROW);
            handlerEmitted = true;
        }
    }
}
