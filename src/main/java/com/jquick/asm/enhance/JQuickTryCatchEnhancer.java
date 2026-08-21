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
 * asm‑enhance method exception‑capture around enhancer.
 *
 * <p>Wraps matched method body entirely with try‑catch block to implement entry, normal‑return
 * and exception‑capture instrumentation. Built upon {@link JQuickBaseMethodVisitor} and {@link Label}
 * try‑catch table entries, no dependency on asm‑commons.
 *
 * <h3>Bytecode Structure</h3>
 * <pre>{@code
 * onEnter                              // before try‑block
 * startLabel:
 *   <original method body>
 *   onExit                             // before return (inside try‑block)
 *   return
 * endLabel:
 * handlerLabel:   // catch Throwable [startLabel, endLabel)
 *   onException   // throwable sits on stack top, must be preserved by hook
 *   ATHROW        // re‑throw exception
 * }</pre>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * byte[] enhanced = JQuickTryCatchEnhancer.from(bytes)
 *     .match("risky")
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
     * Create an enhancer from bytecode.
     */
    public static JQuickTryCatchEnhancer from(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes cannot be null or empty array");
        }
        return new JQuickTryCatchEnhancer(bytes);
    }

    /**
     * Set the guard to use for method enhancement.
     */
    public JQuickTryCatchEnhancer guard(JQuickEnhanceGuard guard) {
        this.guard = guard == null ? JQuickEnhanceGuard.DEFAULT : guard;
        return this;
    }

    /**
     * Match all modifiable methods.
     */
    public JQuickTryCatchEnhancer matchAll() {
        this.matchAll = true;
        return this;
    }

    /**
     * Match methods by name.
     */
    public JQuickTryCatchEnhancer match(String methodName) {
        matchers.add(m -> m.name.equals(methodName));
        return this;
    }

    /**
     * Match methods by name and descriptor.
     */
    public JQuickTryCatchEnhancer match(String methodName, String descriptor) {
        matchers.add(m -> m.name.equals(methodName) && m.descriptor.equals(descriptor));
        return this;
    }

    /**
     * Custom match predicate.
     */
    public JQuickTryCatchEnhancer match(Predicate<JQuickMethodEnhancer.MethodMeta> predicate) {
        if (predicate != null) {
            matchers.add(predicate);
        }
        return this;
    }

    /**
     * Set the advice to use for method enhancement.
     */
    public JQuickTryCatchEnhancer advice(JQuickTryCatchAdvice advice) {
        this.advice = advice == null ? JQuickTryCatchAdvice.builder().build() : advice;
        return this;
    }

    /**
     * Apply the enhancement to the bytecode and return the new bytecode.
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
     * Apply the enhancement to the bytecode and load it into memory.
     */
    public Class<?> applyAndDefine(String className) {
        return JQuickBytecodeUtil.defineClass(className, apply());
    }

    /**
     * Apply the enhancement to the bytecode and write it to a file.
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
     * Enhanced try-catch method visitor.
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
            // OnEnter is not affected by capture before the try block
            JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
            advice.onEnter(ctx);
            // Register try catch: Capture Throwable for [startLabel, endLabel], jump handlerLabels
            mv.visitTryCatchBlock(startLabel, endLabel, handlerLabel, "java/lang/Throwable");
            tryRegistered = true;
            mv.visitLabel(startLabel);
        }

        @Override
        protected void onMethodEnter() {
            //OnEnter has been manually processed in VisitCode, disabling the default header stake of the base class
        }

        @Override
        protected void onMethodExit(int opcode) {
            // ATHROW (raw method actively thrown): already in try, handled by handler, not buried here
            if (opcode == Opcodes.ATHROW) {
                return;
            }
            // Normal return to pre burial point
            JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
            advice.onExit(ctx);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // The catch block instruction must be issued before visitMaxs, in compliance with ASM call order constraints
            emitHandler();
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            //Bottom line: If visitMaxs is not called upstream (theoretically not happening), reissue here
            emitHandler();
            super.visitEnd();
        }

        /**
         * Issue the try block end tag and catch processing block (only once).
         */
        private void emitHandler() {
            if (!tryRegistered || handlerEmitted) {
                return;
            }
            // Mark the end of the try block
            mv.visitLabel(endLabel);
            // Catch processing block: The stack top is the exception object captured
            mv.visitLabel(handlerLabel);
            JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
            advice.onException(ctx);
            // Resubmit exception (the onEException hook needs to keep the exception at the top of the stack)
            mv.visitInsn(Opcodes.ATHROW);
            handlerEmitted = true;
        }
    }
}
