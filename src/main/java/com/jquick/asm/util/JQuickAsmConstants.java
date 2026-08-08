package com.jquick.asm.util;

import org.objectweb.asm.Opcodes;

/**
 * asm-util 常量定义。
 *
 * <p>集中存放 ASM 版本号、读写解析标志位、内部常用常量，避免魔法数字散落各处。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 使用统一版本号创建 ClassReader
 * ClassReader reader = new ClassReader(bytes);
 * reader.accept(visitor, JQuickAsmConstants.PARSE_FLAGS);
 * }</pre>
 */
public final class JQuickAsmConstants {

    /**
     * ASM API 版本：使用 ASM9，兼容 JDK8 ~ JDK21 的字节码读写。
     */
    public static final int ASM_API = Opcodes.ASM9;
    /**
     * 默认编译目标版本：JDK8。生成字节码时若未指定则按此版本输出。
     */
    public static final int DEFAULT_CLASS_VERSION = Opcodes.V1_8;
    /**
     * ClassReader 解析标志位：
     * <ul>
     *   <li>{@code SKIP_FRAMES}：跳过栈帧计算，避免在仅修改方法体时触发
     *       {@code IncompatibleClassChangeError}，由 ClassWriter 自动重算。</li>
     *   <li>{@code SKIP_DEBUG}：跳过行号/局部变量表等调试信息，加快解析速度。</li>
     * </ul>
     * 读取用于「结构分析」时使用；需要保留行号信息时请改用 {@link #PARSE_FLAGS_KEEP_DEBUG}。
     */
    public static final int PARSE_FLAGS = org.objectweb.asm.ClassReader.SKIP_FRAMES
            | org.objectweb.asm.ClassReader.SKIP_DEBUG;
    /**
     * 解析标志位（保留调试信息）：用于需要行号、局部变量名的场景，例如埋点插桩。
     */
    public static final int PARSE_FLAGS_KEEP_DEBUG = org.objectweb.asm.ClassReader.SKIP_FRAMES;
    /**
     * ClassWriter 计算标志位：自动计算栈帧与最大操作数栈/局部变量表。
     */
    public static final int WRITER_FLAGS = org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
    /**
     * 构造方法特殊方法名。
     */
    public static final String INIT = "<init>";
    /**
     * 静态初始化块方法名。
     */
    public static final String CLINIT = "<clinit>";
    /**
     * java/lang/Object 内部名。
     */
    public static final String OBJECT_INTERNAL_NAME = "java/lang/Object";
    /**
     * 默认字节码格式化打印缩进。
     */
    public static final String INDENT = "    ";

    private JQuickAsmConstants() {
    }
}
