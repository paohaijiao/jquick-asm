package com.jquick.asm.enhance;

import com.jquick.asm.core.JQuickBaseClassVisitor;
import com.jquick.asm.core.JQuickBaseMethodVisitor;
import com.jquick.asm.core.JQuickEnhanceGuard;
import com.jquick.asm.util.JQuickAsmConstants;
import com.jquick.asm.util.JQuickBytecodeUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * ASM‑enhance method byte‑code editor.
 * <p>Provides method‑head, tail and pre‑return advice, abstract low‑level {@link org.objectweb.asm.MethodVisitor} instruction handling.
 * Implemented via {@link JQuickBaseMethodVisitor} {@code onMethodEnter}/{@code onMethodExit} hooks.
 * <p>All enhancements guarded by {@link JQuickEnhanceGuard}: skip constructor / native / abstract methods automatically.
 * Supports method whitelist/blacklist and custom matchers.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * byte[] enhanced = JQuickMethodEnhancer.from(originalBytes)
 *     .match("doSomething")
 *     .advice(JQuickMethodAdvice.builder()
 *         .onEnter(ctx -> {
 *             MethodVisitor mv = ctx.methodVisitor();
 *             mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
 *             mv.visitLdcInsn(">> enter " + ctx.name());
 *             mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
 *                     "(Ljava/lang/String;)V", false);
 *         })
 *         .build())
 *     .apply();
 * Class<?> clazz = JQuickBytecodeUtil.defineClass("com.demo.Foo", enhanced);
 * }</pre>
 */

public final class JQuickMethodEnhancer {

    private final byte[] source;

    private final List<Predicate<MethodMeta>> matchers = new ArrayList<>();

    private JQuickMethodAdvice advice = JQuickMethodAdvice.builder().build();

    private JQuickEnhanceGuard guard = JQuickEnhanceGuard.DEFAULT;

    private boolean matchAll = false;

    private JQuickMethodEnhancer(byte[] source) {
        this.source = source;
    }

    /**
     * Create an enhancer from bytecode。
     */
    public static JQuickMethodEnhancer from(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be empty");
        }
        return new JQuickMethodEnhancer(bytes);
    }

    // Suppress unused import alerts
    @SuppressWarnings("unused")
    private static void unused() {
        new HashSet<Set<?>>();
    }

    /**
     * Set up custom security guards.
     */
    public JQuickMethodEnhancer guard(JQuickEnhanceGuard guard) {
        this.guard = guard == null ? JQuickEnhanceGuard.DEFAULT : guard;
        return this;
    }

    /**
     * Match all methods that can be safely modified (constructor/native/abstract automatically excluded).
     */
    public JQuickMethodEnhancer matchAll() {
        this.matchAll = true;
        return this;
    }

    /**
     * Match by method name (overloaded methods will be enhanced).
     */
    public JQuickMethodEnhancer match(String methodName) {
        matchers.add(m -> m.name.equals(methodName));
        return this;
    }

    /**
     * Match exactly by method name+descriptor.
     */
    public JQuickMethodEnhancer match(String methodName, String descriptor) {
        matchers.add(m -> m.name.equals(methodName) && m.descriptor.equals(descriptor));
        return this;
    }

    /**
     * Customize matching predicates.
     */
    public JQuickMethodEnhancer match(Predicate<MethodMeta> predicate) {
        if (predicate != null) {
            matchers.add(predicate);
        }
        return this;
    }

    /**
     * Set up enhanced notifications
     */
    public JQuickMethodEnhancer advice(JQuickMethodAdvice advice) {
        this.advice = advice == null ? JQuickMethodAdvice.builder().build() : advice;
        return this;
    }

    /**
     * Perform enhancement and return new bytecode。
     */
    public byte[] apply() {
        ClassReader reader = new ClassReader(source);
        ClassWriter cw = new ClassWriter(reader, JQuickAsmConstants.WRITER_FLAGS);
        JQuickBaseClassVisitor cv = new JQuickBaseClassVisitor(JQuickAsmConstants.ASM_API, cw, null);
        cv.setMethodVisitorFactory((api, downstream, access, name, descriptor, signature, exceptions, classInfo) -> {
            String owner = classInfo == null ? null : classInfo.getInternalName();
            MethodMeta meta = new MethodMeta(access, name, descriptor);
            if (!shouldEnhance(meta)) {
                return downstream; //Mismatch: Forward as the original method
            }
            // Security verification: If not met, skip enhancement (do not throw exceptions, ensure uninterrupted process)
            try {
                guard.checkModifiable(name, descriptor, access);
            } catch (SecurityException e) {
                // Skip non enhancerable methods
                return downstream;
            }
            final String ownerInternalName = owner;
            return new JQuickBaseMethodVisitor(api, downstream, access, name, descriptor) {
                @Override
                protected void onMethodEnter() {
                    JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
                    advice.onEnter(ctx);
                }

                @Override
                protected void onMethodExit(int opcode) {
                    JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
                    advice.onExit(ctx, opcode);
                }
            };
        });

        reader.accept(cv, JQuickAsmConstants.PARSE_FLAGS);
        return cw.toByteArray();
    }

    /**
     * Perform enhancement and memory loading。
     */
    public Class<?> applyAndDefine(String className) {
        return JQuickBytecodeUtil.defineClass(className, apply());
    }

    /**
     * Execute enhancement and write the file。
     */
    public String applyAndWrite(String className, String outputDir) {
        return JQuickBytecodeUtil.writeToFile(className, apply(), outputDir);
    }

    private boolean shouldEnhance(MethodMeta meta) {
        if (matchAll) {
            return true;
        }
        for (Predicate<MethodMeta> p : matchers) {
            if (p.test(meta)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method matching metadata。
     */
    public static final class MethodMeta {

        public final int access;

        public final String name;

        public final String descriptor;

        public MethodMeta(int access, String name, String descriptor) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }
    }
}
