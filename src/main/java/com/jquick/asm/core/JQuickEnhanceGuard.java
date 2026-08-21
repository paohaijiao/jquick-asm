package com.jquick.asm.core;

import com.jquick.asm.util.JQuickAccessUtil;
import com.jquick.asm.util.JQuickAsmConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * asm‑core security guard: method black‑and‑white list plus protection for constructors and native methods.
 *
 * <p>All method enhancement operations must pass validation by this guard.
 * A {@link SecurityException} will be thrown when security constraints are violated to prevent breaking JVM semantics.
 *
 * <h3>Security Rules</h3>
 * <ul>
 *   <li>Modifying constructor {@code <init>} is forbidden: instrumentation will break object‑initialization semantics.</li>
 *   <li>Modifying static initializer block {@code <clinit>} is forbidden.</li>
 *   <li>Modifying native methods is forbidden: no method body available for instrumentation.</li>
 *   <li>Modifying abstract methods is forbidden: no method body available.</li>
 *   <li>Secondary filtering supported via method‑name blacklist / whitelist.</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * JQuickEnhanceGuard guard = JQuickEnhanceGuard.builder()
 *     .blacklist("hashCode", "toString")
 *     .build();
 * guard.checkModifiable("doSomething", "(I)V", Opcodes.ACC_PUBLIC);  // pass
 * guard.checkModifiable("<init>", "()V", Opcodes.ACC_PUBLIC);        // throws SecurityException
 * }</pre>
 */
public final class JQuickEnhanceGuard {

    /**
     * Default guard instance.
     */
    public static final JQuickEnhanceGuard DEFAULT = builder().build();
    /**
     * Default blacklist: critical JVM methods to avoid breaking fundamental semantics.
     */
    private static final Set<String> DEFAULT_BLACKLIST = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("getClass", "wait", "notify", "notifyAll")));
    /**
     * Custom blacklist for method names. Empty means custom blacklist is disabled.
     *
     * @return custom blacklist of method names
     */
    private final Set<String> blacklist;
    /**
     * Custom whitelist for method names. Empty means whitelist is disabled, all valid methods are permitted.
     */
    private final Set<String> whitelist;

    private JQuickEnhanceGuard(Set<String> blacklist, Set<String> whitelist) {
        this.blacklist = blacklist;
        this.whitelist = whitelist;
    }

    /**
     * Creates a builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks whether a method is modifiable. Throws {@link SecurityException} if any security constraint is violated.
     *
     * @param name       Method name
     * @param descriptor Method descriptor
     * @param access     Access flags
     * @throws SecurityException when the method cannot be modified
     */
    public void checkModifiable(String name, String descriptor, int access) {
        if (name == null || descriptor == null) {
            throw new SecurityException("Method name/descriptor cannot be null");
        }
        if (JQuickAsmConstants.INIT.equals(name)) {
            throw new SecurityException("Prohibit modifying the construction method <init>: " + name + descriptor);
        }
        if (JQuickAsmConstants.CLINIT.equals(name)) {
            throw new SecurityException("Prohibit modifying static initialization blocks <clinit>: " + name);
        }
        if (JQuickAccessUtil.isNative(access)) {
            throw new SecurityException("Prohibit modifying native methods: " + name + descriptor);
        }
        if (JQuickAccessUtil.isAbstract(access)) {
            throw new SecurityException("Prohibit modifying abstract methods: " + name + descriptor);
        }
        if (DEFAULT_BLACKLIST.contains(name)) {
            throw new SecurityException("Method is in default blacklist (JVM critical methods): " + name);
        }
        if (blacklist != null && blacklist.contains(name)) {
            throw new SecurityException("Method is in custom blacklist: " + name);
        }
        if (whitelist != null && !whitelist.isEmpty() && !whitelist.contains(name)) {
            throw new SecurityException("Method is not in custom whitelist: " + name);
        }
    }

    /**
     * Checks whether a method is modifiable.
     */
    public void checkModifiable(JQuickMethodInfo method) {
        checkModifiable(method.getName(), method.getDescriptor(), method.getAccess());
    }

    /**
     * Checks whether a method is modifiable.
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
     * Builder pattern for configuring blacklist and whitelist.
     *
     * @return builder instance
     */
    public static final class Builder {

        private final Set<String> blacklist = new HashSet<>();

        private final Set<String> whitelist = new HashSet<>();

        /**
         * Adds blacklist method names (variable arguments).
         */
        public Builder blacklist(String... names) {
            if (names != null) {
                blacklist.addAll(Arrays.asList(names));
            }
            return this;
        }

        /**
         * Adds whitelist method names (variable arguments).
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
