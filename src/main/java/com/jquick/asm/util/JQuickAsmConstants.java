package com.jquick.asm.util;

import org.objectweb.asm.Opcodes;

/**
 * ASM‑util constant definitions.
 * <p>Centralizes ASM API version, parse flags and internal constants to eliminate magic numbers.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * ClassReader reader = new ClassReader(bytes);
 * reader.accept(visitor, JQuickAsmConstants.PARSE_FLAGS);
 * }</pre>
 */
public final class JQuickAsmConstants {

    /**
     * ASM API version: ASM9, supports bytecode from JDK8 to JDK21.
     */
    public static final int ASM_API = Opcodes.ASM9;
    /**
     * Default compile target: JDK8. Used when no version specified for bytecode generation.
     */
    public static final int DEFAULT_CLASS_VERSION = Opcodes.V1_8;
    /**
     * ClassReader parse flags:
     * <ul>
     * <li>{@code SKIP_FRAMES}: Skip stack‑frame computation, avoid {@code IncompatibleClassChangeError}; recalculated by ClassWriter.</li>
     * <li>{@code SKIP_DEBUG}: Omit line‑number / local‑variable tables for faster parsing.</li>
     * </ul>
     * For structural analysis only. Use {@link #PARSE_FLAGS_KEEP_DEBUG} to preserve debug info.
     */
    public static final int PARSE_FLAGS = org.objectweb.asm.ClassReader.SKIP_FRAMES | org.objectweb.asm.ClassReader.SKIP_DEBUG;
    /**
     * Parse flags with debug info: preserve line‑numbers and local‑variable names, for instrumentation scenarios.
     */
    public static final int PARSE_FLAGS_KEEP_DEBUG = org.objectweb.asm.ClassReader.SKIP_FRAMES;
    /**
     * ClassWriter compute flags: auto‑calculate stack frames, max stack and local variable table.
     */
    public static final int WRITER_FLAGS = org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
    /**
     * Special method name for constructors.
     */
    public static final String INIT = "<init>";
    /**
     * Method name for static‑initializer block.
     */
    public static final String CLINIT = "<clinit>";
    /**
     * Internal name for {@code java/lang/Object}.
     */
    public static final String OBJECT_INTERNAL_NAME = "java/lang/Object";
    /**
     * Default indent for bytecode pretty‑print.
     */
    public static final String INDENT = "    ";

    private JQuickAsmConstants() {
    }
}
