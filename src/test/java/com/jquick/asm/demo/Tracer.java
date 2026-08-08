package com.jquick.asm.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * 埋点辅助类：增强后的字节码通过 INVOKESTATIC 调用本类静态方法完成埋点上报。
 *
 * <p>用 {@code synchronized} + 单例列表收集埋点事件，便于测试断言。
 */
public final class Tracer {

    private Tracer() {
    }

    /** 收集的埋点事件。 */
    private static final List<String> EVENTS = new ArrayList<>();

    /** 方法进入埋点。 */
    public static void onEnter(String methodName) {
        EVENTS.add("ENTER " + methodName);
    }

    /** 方法正常返回埋点。 */
    public static void onExit(String methodName) {
        EVENTS.add("EXIT  " + methodName);
    }

    /** 方法异常埋点。 */
    public static void onError(String methodName, Throwable t) {
        EVENTS.add("ERROR " + methodName + " -> " + t.getClass().getSimpleName());
    }

    /** 清空埋点（每个测试用例开始前调用）。 */
    public static synchronized void reset() {
        EVENTS.clear();
    }

    /** 获取埋点快照。 */
    public static synchronized List<String> snapshot() {
        return new ArrayList<>(EVENTS);
    }
}
