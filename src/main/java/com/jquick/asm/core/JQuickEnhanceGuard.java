package com.jquick.asm.core;

import com.jquick.asm.util.JQuickAccessUtil;
import com.jquick.asm.util.JQuickAsmConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * asm-core 安全守卫：方法黑白名单 + 构造方法/native 保护。
 *
 * <p>所有方法增强操作必须先经过本守卫校验，违反安全约束时抛出
 * {@link SecurityException}，避免破坏 JVM 语义。
 *
 * <h3>安全规则</h3>
 * <ul>
 *   <li>禁止修改构造方法 {@code <init>}：插桩会影响对象初始化语义。</li>
 *   <li>禁止修改静态初始化块 {@code <clinit>}。</li>
 *   <li>禁止修改 native 方法：无方法体，无法插桩。</li>
 *   <li>禁止修改 abstract 方法：无方法体。</li>
 *   <li>支持方法名黑名单/白名单二次过滤。</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * JQuickEnhanceGuard guard = JQuickEnhanceGuard.builder()
 *     .blacklist("hashCode", "toString")
 *     .build();
 * guard.checkModifiable("doSomething", "(I)V", Opcodes.ACC_PUBLIC);  // 通过
 * guard.checkModifiable("<init>", "()V", Opcodes.ACC_PUBLIC);        // 抛 SecurityException
 * }</pre>
 */
public final class JQuickEnhanceGuard {

    /**
     * 默认守卫实例。
     */
    public static final JQuickEnhanceGuard DEFAULT = builder().build();
    /**
     * 默认黑名单：JVM 关键方法，避免影响基础语义
     */
    private static final Set<String> DEFAULT_BLACKLIST = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("getClass", "wait", "notify", "notifyAll")));
    /**
     * 自定义黑名单方法名（为空表示不启用自定义黑名单）
     */
    private final Set<String> blacklist;
    /**
     * 自定义白名单方法名（为空表示不启用白名单，允许所有合法方法）
     */
    private final Set<String> whitelist;

    private JQuickEnhanceGuard(Set<String> blacklist, Set<String> whitelist) {
        this.blacklist = blacklist;
        this.whitelist = whitelist;
    }

    /**
     * 创建构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 校验方法是否可被修改。任一安全约束不满足即抛 {@link SecurityException}。
     *
     * @param name       方法名
     * @param descriptor 方法描述符
     * @param access     访问修饰符
     */
    public void checkModifiable(String name, String descriptor, int access) {
        if (name == null || descriptor == null) {
            throw new SecurityException("方法名/描述符不能为 null");
        }
        // 1. 构造方法保护
        if (JQuickAsmConstants.INIT.equals(name)) {
            throw new SecurityException("禁止修改构造方法 <init>: " + name + descriptor);
        }
        // 2. 静态初始化块保护
        if (JQuickAsmConstants.CLINIT.equals(name)) {
            throw new SecurityException("禁止修改静态初始化块 <clinit>: " + name);
        }
        // 3. native 方法保护
        if (JQuickAccessUtil.isNative(access)) {
            throw new SecurityException("禁止修改 native 方法: " + name + descriptor);
        }
        // 4. abstract 方法无方法体，不可插桩
        if (JQuickAccessUtil.isAbstract(access)) {
            throw new SecurityException("禁止修改 abstract 方法（无方法体）: " + name + descriptor);
        }
        // 5. 默认黑名单（JVM 关键方法）
        if (DEFAULT_BLACKLIST.contains(name)) {
            throw new SecurityException("方法在默认黑名单中（JVM 关键方法）: " + name);
        }
        // 6. 自定义黑名单
        if (blacklist != null && blacklist.contains(name)) {
            throw new SecurityException("方法在自定义黑名单中: " + name);
        }
        // 7. 自定义白名单：若启用，则仅允许白名单内方法
        if (whitelist != null && !whitelist.isEmpty() && !whitelist.contains(name)) {
            throw new SecurityException("方法不在白名单中: " + name);
        }
    }

    /**
     * 校验 {@link JQuickMethodInfo} 是否可被修改。
     */
    public void checkModifiable(JQuickMethodInfo method) {
        checkModifiable(method.getName(), method.getDescriptor(), method.getAccess());
    }

    /**
     * 静态判断方法是否可修改（不抛异常）。
     */
    public boolean isModifiable(String name, String descriptor, int access) {
        try {
            checkModifiable(name, descriptor, access);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * 构建器：链式配置黑/白名单。
     */
    public static final class Builder {

        private final Set<String> blacklist = new HashSet<>();

        private final Set<String> whitelist = new HashSet<>();

        /**
         * 添加黑名单方法名（可变参数）。
         */
        public Builder blacklist(String... names) {
            if (names != null) {
                blacklist.addAll(Arrays.asList(names));
            }
            return this;
        }

        /**
         * 添加白名单方法名（可变参数）。设置白名单后，仅白名单方法可被修改。
         */
        public Builder whitelist(String... names) {
            if (names != null) {
                whitelist.addAll(Arrays.asList(names));
            }
            return this;
        }

        public JQuickEnhanceGuard build() {
            return new JQuickEnhanceGuard(
                    blacklist.isEmpty() ? null : Collections.unmodifiableSet(new HashSet<>(blacklist)),
                    whitelist.isEmpty() ? null : Collections.unmodifiableSet(new HashSet<>(whitelist)));
        }
    }
}
