package com.jquick.asm.enhance;

/**
 * asm-enhance try-catch 环绕通知接口。
 *
 * <p>定义方法「进入」「正常返回」「异常退出」三类钩子，由 {@link JQuickTryCatchEnhancer}
 * 在字节码层面用 try-catch 环绕实现异常捕获埋点。
 *
 * <h3>钩子触发时机</h3>
 * <ul>
 *   <li>{@link #onEnter}：方法体最前面（try 块之前，不受捕获影响）。</li>
 *   <li>{@link #onExit}：每条返回指令之前（try 块内，正常返回路径）。</li>
 *   <li>{@link #onException}：catch 处理块。调用时操作数栈顶为捕获的异常对象
 *       （{@code java/lang/Throwable}），<b>实现方必须保持该异常仍在栈顶</b>，
 *       以便增强器在钩子后执行 ATHROW 重抛。如需读取异常，请先 DUP 再调用静态方法。</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * JQuickTryCatchAdvice advice = JQuickTryCatchAdvice.builder()
 *     .onEnter(ctx -> print(ctx.methodVisitor(), ">> enter " + ctx.name()))
 *     .onExit(ctx -> print(ctx.methodVisitor(), "<< exit  " + ctx.name()))
 *     .onException(ctx -> {
 *         MethodVisitor mv = ctx.methodVisitor();
 *         mv.visitInsn(Opcodes.DUP);                       // 复制异常对象
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
     * 方法进入。
     */
    default void onEnter(JQuickMethodContext ctx) {
    }

    /**
     * 方法正常返回前。
     */
    default void onExit(JQuickMethodContext ctx) {
    }

    /**
     * 方法异常退出（catch 块）。栈顶为异常对象，钩子结束后会被重抛。
     */
    default void onException(JQuickMethodContext ctx) {
    }

    /**
     * 构建器。
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
