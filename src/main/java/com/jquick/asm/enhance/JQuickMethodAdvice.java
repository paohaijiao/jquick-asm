package com.jquick.asm.enhance;

import org.objectweb.asm.Opcodes;

/**
 * asm-enhance 方法增强通知接口。
 *
 * <p>定义方法「头部插入」与「返回前插入」两类钩子，由 {@link JQuickMethodEnhancer} 在
 * 字节码层面对应位置注入。实现方只需关心要插入的指令，无需处理 Visitor 细节。
 *
 * <h3>钩子触发时机</h3>
 * <ul>
 *   <li>{@link #onEnter}：方法体第一行（构造方法在 super 调用之后）。</li>
 *   <li>{@link #onExit}：每条返回指令之前（RETURN/IRETURN/.../ATHROW）。</li>
 * </ul>
 *
 * <h3>使用示例</h3>
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
     * 创建构建器。
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * 方法头部插入逻辑。
     *
     * @param ctx 方法上下文
     */
    default void onEnter(JQuickMethodContext ctx) {
    }

    /**
     * 方法返回前插入逻辑（每条返回指令触发一次）。
     *
     * @param ctx    方法上下文
     * @param opcode 返回指令操作码，如 {@link Opcodes#RETURN}、{@link Opcodes#ARETURN}、{@link Opcodes#ATHROW}
     */
    default void onExit(JQuickMethodContext ctx, int opcode) {
    }

    /**
     * 方法头部钩子。
     */
    @FunctionalInterface
    interface EnterHook {
        void onEnter(JQuickMethodContext ctx);
    }

    /**
     * 方法返回前钩子。
     */
    @FunctionalInterface
    interface ExitHook {
        void onExit(JQuickMethodContext ctx, int opcode);
    }

    /**
     * 构建器：链式组装 onEnter/onExit。
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
