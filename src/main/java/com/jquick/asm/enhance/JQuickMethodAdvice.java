package com.jquick.asm.enhance;


/**
 * asm‑enhance method enhancement advice interface.
 *
 * <p>Defines two hook points: insertion at method entry and insertion before method return.
 * Hooks are injected at corresponding positions on bytecode level by {@link JQuickMethodEnhancer}.
 * Implementors only need to focus on instructions to emit without dealing with low‑level Visitor details.
 *
 * <h3>Hook Execution Timing</h3>
 * <ul>
 *   <li>{@link #onEnter}: First line of method body (after super‑constructor call for constructors).</li>
 *   <li>{@link #onExit}: Before every return instruction (RETURN/IRETURN/.../ATHROW).</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * JQuickMethodAdvice advice = JQuickMethodAdvice.builder()
 *     .onEnter(ctx -> {
 *         MethodVisitor mv = ctx.methodVisitor();
 *         mv.visitLdcInsn("enter");
 *         invokePrintln(mv);
 *     })
 *     .onExit((ctx, opcode) -> {
 *         MethodVisitor mv = ctx.methodVisitor();
 *         mv.visitLdcInsn("exit");
 *         invokePrintln(mv);
 *     })
 *     .build();
 * }</pre>
 */

public interface JQuickMethodAdvice {

    /**
     * Creates a new builder instance.
     *
     * @return builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Method entry hook.
     *
     * @param ctx method context
     */
    default void onEnter(JQuickMethodContext ctx) {
    }

    /**
     * Method exit hook.
     *
     * @param ctx    method context
     * @param opcode return instruction opcode
     */
    default void onExit(JQuickMethodContext ctx, int opcode) {
    }

    /**
     * Method entry hook.
     */
    @FunctionalInterface
    interface EnterHook {
        void onEnter(JQuickMethodContext ctx);
    }

    /**
     * Method exit hook.
     */
    @FunctionalInterface
    interface ExitHook {
        void onExit(JQuickMethodContext ctx, int opcode);
    }

    /**
     * Builder pattern for configuring advice hooks.
     */
    final class Builder {
        private EnterHook enterHook = ctx -> {
        };

        private ExitHook exitHook = (ctx, op) -> {
        };

        public Builder onEnter(EnterHook hook) {
            if (hook != null) {
                this.enterHook = hook;
            }
            return this;
        }

        public Builder onExit(ExitHook hook) {
            if (hook != null) {
                this.exitHook = hook;
            }
            return this;
        }

        public JQuickMethodAdvice build() {
            return new JQuickMethodAdvice() {
                @Override
                public void onEnter(JQuickMethodContext ctx) {
                    enterHook.onEnter(ctx);
                }

                @Override
                public void onExit(JQuickMethodContext ctx, int opcode) {
                    exitHook.onExit(ctx, opcode);
                }
            };
        }
    }
}
