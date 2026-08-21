package com.jquick.asm.enhance;

/**
 * asm‑enhance try‑catch around advice interface.
 *
 * <p>Defines three hook points: method entry, normal return and exceptional exit.
 * Implemented via bytecode‑level try‑catch wrapping by {@link JQuickTryCatchEnhancer} for exception‑capture instrumentation.
 *
 * <h3>Hook Execution Timing</h3>
 * <ul>
 *   <li>{@link #onEnter}: At the very beginning of method body (before try‑block, unaffected by exception catching).</li>
 *   <li>{@link #onExit}: Before each return instruction within try‑block, for normal‑return path.</li>
 *   <li>{@link #onException}: Executes inside catch handler. The caught throwable object ({@code java/lang/Throwable})
 *       sits on top of operand stack on invocation. <b>Implementors must keep this throwable on stack top</b>,
 *       so enhancer can re‑throw with ATHROW after hook. To consume the exception, duplicate it with DUP before invoking static methods.</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * JQuickTryCatchAdvice advice = JQuickTryCatchAdvice.builder()
 *     .onEnter(ctx -> print(ctx.methodVisitor(), ">> enter " + ctx.name()))
 *     .onExit(ctx -> print(ctx.methodVisitor(), "<< exit  " + ctx.name()))
 *     .onException(ctx -> {
 *         MethodVisitor mv = ctx.methodVisitor();
 *         mv.visitInsn(Opcodes.DUP);                       // duplicate throwable object
 *         mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/demo/Tracer",
 *                 "onError", "(Ljava/lang/Throwable;)V", false);
 *     })
 *     .build();
 * }</pre>
 */

public interface JQuickTryCatchAdvice {

    static Builder builder() {
        return new Builder();
    }

    /**
     * Method entry hook.
     */
    default void onEnter(JQuickMethodContext ctx) {
    }

    /**
     * Method exit hook.
     */
    default void onExit(JQuickMethodContext ctx) {
    }

    /**
     * Exception hook.
     */
    default void onException(JQuickMethodContext ctx) {
    }

    /**
     * Builder pattern for configuring advice hooks.
     */
    final class Builder {
        private JQuickMethodAdvice.EnterHook enterHook = ctx -> {
        };
        private JQuickMethodAdvice.EnterHook exitHook = ctx -> {
        };
        private JQuickMethodAdvice.EnterHook exceptionHook = ctx -> {
        };

        public Builder onEnter(JQuickMethodAdvice.EnterHook hook) {
            if (hook != null) {
                this.enterHook = hook;
            }
            return this;
        }

        public Builder onExit(JQuickMethodAdvice.EnterHook hook) {
            if (hook != null) {
                this.exitHook = hook;
            }
            return this;
        }

        public Builder onException(JQuickMethodAdvice.EnterHook hook) {
            if (hook != null) {
                this.exceptionHook = hook;
            }
            return this;
        }

        public JQuickTryCatchAdvice build() {
            return new JQuickTryCatchAdvice() {
                @Override
                public void onEnter(JQuickMethodContext ctx) {
                    enterHook.onEnter(ctx);
                }

                @Override
                public void onExit(JQuickMethodContext ctx) {
                    exitHook.onEnter(ctx);
                }

                @Override
                public void onException(JQuickMethodContext ctx) {
                    exceptionHook.onEnter(ctx);
                }
            };
        }
    }
}
