package com.jquick.asm.demo;

/**
 * 埋点演示目标类：包含普通方法、抛异常方法、native/构造方法（用于安全校验演示）。
 *
 * <p>该类会被 {@code JQuickAsmDemoTest} 增强后重新加载，验证埋点效果。
 */
public class Calculator {

    private int base;

    public Calculator(int base) {
        this.base = base;
    }

    /**
     * 普通加法：演示头部/尾部埋点。
     */
    public int add(int a, int b) {
        return base + a + b;
    }

    /**
     * 抛异常方法：演示 try-catch 环绕埋点。
     */
    public int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("除数不能为 0");
        }
        return a / b;
    }

    @Override
    public String toString() {
        return "Calculator(base=" + base + ")";
    }
}
