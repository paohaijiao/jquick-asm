package com.jquick.asm.bench;

import com.jquick.asm.writer.JQuickClassWriterTool;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 策略4：ASM 动态生成转换器。
 *
 * <p>利用 jquick-asm 库的 {@link JQuickClassWriterTool} 在运行时生成一个
 * 直接调用 getter 的转换器 Class（无任何反射开销）。
 *
 * <p>生成成本仅一次，后续每次调用相当于「手写硬编码」级别性能，
 * 体现了字节码生成相对反射的巨大优势。
 *
 * <p>生成的类等价于：
 * <pre>{@code
 * public class AsmGeneratedConverter implements ObjectToMapConverter {
 *     public Map<String, Object> convert(Object target) {
 *         BenchmarkUser u = (BenchmarkUser) target;
 *         Map<String, Object> map = new LinkedHashMap<>();
 *         map.put("id", u.getId());
 *         map.put("name", u.getName());
 *         // ... 其余字段
 *         return map;
 *     }
 *     public String name() { return "Asm(ASM动态生成)"; }
 * }
 * }</pre>
 */
public class AsmConverter implements ObjectToMapConverter {

    /**
     * 目标类型内部名。
     */
    private static final String TARGET_INTERNAL = "com/jquick/asm/bench/BenchmarkUser";
    /**
     * 转换器接口内部名。
     */
    private static final String CONVERTER_INTERNAL = "com/jquick/asm/bench/ObjectToMapConverter";
    /**
     * LinkedHashMap 内部名。
     */
    private static final String LINKED_HASH_MAP_INTERNAL = "java/util/LinkedHashMap";
    /**
     * Map 内部名。
     */
    private static final String MAP_INTERNAL = "java/util/Map";
    /**
     * 内部生成的真正转换器实例（委托给它执行）。
     */
    private static final ObjectToMapConverter DELEGATE = generateAndLoad();

    /**
     * 生成并内存加载真正的转换器 Class。
     */
    @SuppressWarnings("unchecked")
    private static ObjectToMapConverter generateAndLoad() {
        byte[] bytes = JQuickClassWriterTool.builder("com.jquick.asm.bench.AsmGeneratedConverter")
                .implementInterface(ObjectToMapConverter.class)
                // 默认构造方法：super.<init>()V
                .addMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", mv -> {
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                            "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                })
                .addMethod(Opcodes.ACC_PUBLIC, "convert",
                        "(Ljava/lang/Object;)Ljava/util/Map;",
                        AsmConverter::writeConvertBody)
                .addMethod(Opcodes.ACC_PUBLIC, "name",
                        "()Ljava/lang/String;",
                        mv -> {
                            mv.visitCode();
                            mv.visitLdcInsn("Asm(ASM动态生成)");
                            mv.visitInsn(Opcodes.ARETURN);
                            mv.visitMaxs(1, 1);
                            mv.visitEnd();
                        })
                .build();

        Class<?> clazz = JQuickClassWriterTool.define(bytes,
                "com.jquick.asm.bench.AsmGeneratedConverter");
        try {
            return (ObjectToMapConverter) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("实例化 ASM 生成转换器失败", e);
        }
    }

    /**
     * 写入 convert 方法体：cast → new LinkedHashMap → 循环 put → return。
     *
     * <p>局部变量表：0=this, 1=target(Object), 2=map(LinkedHashMap), 3=user(BenchmarkUser)
     */
    private static void writeConvertBody(org.objectweb.asm.MethodVisitor mv) {
        mv.visitCode();
        // 1. cast target -> BenchmarkUser，存入 local 3
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, TARGET_INTERNAL);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        // 2. new LinkedHashMap，存入 local 2
        mv.visitTypeInsn(Opcodes.NEW, LINKED_HASH_MAP_INTERNAL);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, LINKED_HASH_MAP_INTERNAL,
                "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);

        // 对每个 getter：map.put("field", box(user.getXxx()))
        emitPut(mv, "id", "getId", "()J", "java/lang/Long", "(J)Ljava/lang/Long;");
        emitPut(mv, "name", "getName", "()Ljava/lang/String;", null, null);
        emitPut(mv, "age", "getAge", "()I", "java/lang/Integer", "(I)Ljava/lang/Integer;");
        emitPut(mv, "email", "getEmail", "()Ljava/lang/String;", null, null);
        emitPut(mv, "active", "isActive", "()Z", "java/lang/Boolean", "(Z)Ljava/lang/Boolean;");
        emitPut(mv, "score", "getScore", "()D", "java/lang/Double", "(D)Ljava/lang/Double;");
        emitPut(mv, "address", "getAddress", "()Ljava/lang/String;", null, null);

        // 3. return map
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ARETURN);
        // COMPUTE_FRAMES 会自动重算
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * 发射一条 {@code map.put("field", box(user.getXxx()));} 指令序列。
     *
     * @param boxInternal   包装类内部名，null 表示返回值已是对象类型无需装箱
     * @param boxMethodDesc 包装方法描述符
     */
    private static void emitPut(org.objectweb.asm.MethodVisitor mv,
                                String fieldName, String getterName, String getterDesc,
                                String boxInternal, String boxMethodDesc) {
        // map
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        // "field"
        mv.visitLdcInsn(fieldName);
        // user.getXxx()  —— local 3 存的是已 cast 的 BenchmarkUser
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET_INTERNAL,
                getterName, getterDesc, false);
        // 装箱（如需要）
        if (boxInternal != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, boxInternal,
                    "valueOf", boxMethodDesc, false);
        }
        // Map.put(String, Object) -> Object
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP_INTERNAL,
                "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        // 弹出 put 返回值
        mv.visitInsn(Opcodes.POP);
    }

    /**
     * 暴露内部生成类供调试打印字节码。
     */
    static Class<?> generatedClass() {
        return DELEGATE.getClass();
    }

    /**
     * 防止未使用导入告警。
     */
    @SuppressWarnings("unused")
    private static void touch() throws NoSuchMethodException {
        Method m = LinkedHashMap.class.getMethod("size");
        m.getName();
    }

    @Override
    public Map<String, Object> convert(Object target) {
        return DELEGATE.convert(target);
    }

    @Override
    public String name() {
        return DELEGATE.name();
    }
}
