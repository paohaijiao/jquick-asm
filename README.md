# jquick-asm

> 一个在 ASM 9.x 之上**纯原生封装**的 Java 字节码操作基础库，以极简 API 降低字节码增强的技术门槛。

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Java](https://img.shields.io/badge/JDK-1.8%2B-orange.svg)](https://www.oracle.com/java/)
[![ASM](https://img.shields.io/badge/ASM-9.6-red.svg)](https://asm.ow2.io/)

`jquick-asm` 屏蔽了 ASM `ClassVisitor` / `MethodVisitor` 繁琐的指令级处理细节，对外提供「**读类 / 写类 / 增强类**」三类极简能力。仅需几行代码即可完成方法埋点、异常捕获、类结构改写与全新类生成。

- 纯原生封装：仅依赖 `asm` + `asm-util`，不依赖 `asm-commons`（返回指令拦截、try-catch 环绕全部自行实现）
- 极简流式 API：`from(bytes).match("name").advice(...).apply()`
- 默认安全：内置 `JQuickEnhanceGuard` 守卫，自动保护构造方法、native、abstract 方法
- 兼容广泛：支持 JDK 8 ~ JDK 21 字节码读写

---

## 目录

- [安装](#安装)
- [快速上手](#快速上手)
- [模块总览](#模块总览)
- [核心 API](#核心-api)
  - [1. 读取类信息（reader）](#1-读取类信息reader)
  - [2. 从零生成类（writer）](#2-从零生成类writer)
  - [3. 修改类结构（modifier）](#3-修改类结构modifier)
  - [4. 方法头部/尾部埋点（enhance）](#4-方法头部尾部埋点enhance)
  - [5. try-catch 异常环绕埋点（enhance）](#5-try-catch-异常环绕埋点enhance)
  - [6. 安全守卫（guard）](#6-安全守卫guard)
  - [7. 工具类（util）](#7-工具类util)
- [安全模型](#安全模型)
- [环境要求](#环境要求)
- [许可证](#许可证)

---

## 安装

### Maven

```xml
<dependency>
    <groupId>io.github.paohaijiao</groupId>
    <artifactId>jquick-asm</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.paohaijiao:jquick-asm:1.0.0'
```

> 传递依赖：`org.ow2.asm:asm:9.6`、`org.ow2.asm:asm-util:9.6`，无需额外声明。

---

## 快速上手

以「给 `Calculator.add` 方法加 enter/exit 埋点」为例：

```java
byte[] enhanced = JQuickMethodEnhancer.from(originalBytes)
        .match("add")                              // 仅增强 add 方法
        .advice(JQuickMethodAdvice.builder()
                .onEnter(ctx -> {                   // 方法头部插入
                    MethodVisitor mv = ctx.methodVisitor();
                    mv.visitLdcInsn(ctx.name());
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/jquick/asm/demo/Tracer", "onEnter",
                            "(Ljava/lang/String;)V", false);
                })
                .onExit((ctx, opcode) -> {          // 每条返回指令前插入
                    MethodVisitor mv = ctx.methodVisitor();
                    mv.visitLdcInsn(ctx.name());
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/jquick/asm/demo/Tracer", "onExit",
                            "(Ljava/lang/String;)V", false);
                })
                .build())
        .apply();                                   // 返回新字节码

// 内存加载并反射调用
Class<?> clazz = JQuickBytecodeUtil.defineClass("com.demo.Calculator", enhanced);
```

完整可运行示例见 [src/test/java/com/jquick/asm/demo/JQuickDemoTest.java](src/test/java/com/jquick/asm/demo/JQuickDemoTest.java)。

---

## 模块总览

`com.jquick.asm` 包含五个子包，职责清晰、互相解耦：

```
com.jquick.asm
├── core        基础封装：Visitor 基类、元信息容器、安全守卫
├── reader      类读取：从 Class / byte[] / InputStream / 类名 解析类结构
├── writer      类生成与修改：从零构建类、对已有类做结构增删改
├── enhance     方法增强：头部/尾部插桩、try-catch 环绕埋点
└── util        工具：常量、字节码加载/写出/打印、类型转换、访问修饰符
```

| 模块 | 关键类 | 职责 |
| --- | --- | --- |
| core | `JQuickBaseClassVisitor`、`JQuickBaseMethodVisitor` | ASM Visitor 基类封装，提供 `onMethodEnter/onMethodExit/wrapWithTryCatch` 钩子 |
| core | `JQuickClassInfo`、`JQuickMethodInfo`、`JQuickFieldInfo`、`JQuickAnnotationInfo` | 元信息容器 |
| core | `JQuickEnhanceGuard` | 安全守卫，黑白名单 + 构造/native/abstract 保护 |
| reader | `JQuickClassReaderTool` | 一行代码解析类结构 |
| writer | `JQuickClassWriterTool` | 流式 Builder 从零生成类 |
| writer | `JQuickClassModifierTool` | 对已有类做字段/方法/注解/权限增删改 |
| enhance | `JQuickMethodEnhancer`、`JQuickTryCatchEnhancer` | 方法字节码增强器 |
| enhance | `JQuickMethodAdvice`、`JQuickTryCatchAdvice`、`JQuickMethodContext` | 增强通知与上下文 |
| util | `JQuickBytecodeUtil`、`JQuickTypeUtil`、`JQuickAccessUtil`、`JQuickAsmConstants` | 通用工具与常量 |

---

## 核心 API

### 1. 读取类信息（reader）

`JQuickClassReaderTool` 提供多种入口解析类结构，无需手写 `ClassVisitor`：

```java
// 1. 从 Class 对象读取
JQuickClassInfo info = JQuickClassReaderTool.read(MyClass.class);
System.out.println(info.getSuperName());           // "java/lang/Object"

// 2. 从字节码读取（保留参数名，便于获取方法参数）
JQuickClassInfo info2 = JQuickClassReaderTool.readKeepDebug(bytes);
JQuickMethodInfo m = info2.findMethod("doSomething", "(I)V");
List<String> params = m.getParameterNames();

// 3. 从全限定类名读取（走系统类加载器）
JQuickClassInfo info3 = JQuickClassReaderTool.read("com.demo.MyClass");

// 4. 按名称+描述符查找方法 / 字段
JQuickMethodInfo method = info.findMethod("add", "(II)I");
JQuickFieldInfo field   = info.findField("base");

// 5. 格式化打印类结构摘要
System.out.println(JQuickClassReaderTool.summarize(info));
```

### 2. 从零生成类（writer）

`JQuickClassWriterTool` 采用流式 Builder，内存动态构建并生成 `Class`：

```java
byte[] bytes = JQuickClassWriterTool.builder("com.demo.Hello")
        .extendSuper("com.demo.Base")
        .implementInterface(Runnable.class)
        .addField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;")
        .addMethod(Opcodes.ACC_PUBLIC, "run", "()V", mv -> {
            mv.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out",
                    "Ljava/io/PrintStream;");
            mv.visitLdcInsn("hello from generated class");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                    "println", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(2, 1);
            mv.visitEnd();
        })
        .build();                                  // 返回字节码

Class<?> clazz = JQuickClassWriterTool.define(bytes, "com.demo.Hello");
JQuickClassWriterTool.writeToFile(bytes, "com.demo.Hello", "d:/out");
```

### 3. 修改类结构（modifier）

`JQuickClassModifierTool` 对已有字节码进行结构增删改查：

```java
byte[] modified = JQuickClassModifierTool.from(originalBytes)
        .addField(Opcodes.ACC_PRIVATE, "counter", "I")          // 新增字段
        .removeField("oldField")                                // 删除字段
        .changeFieldAccess("name", Opcodes.ACC_PUBLIC)          // 改字段权限
        .addMethod(Opcodes.ACC_PUBLIC, "hello", "()V", mv -> { // 新增方法
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        })
        .removeMethod("oldMethod", "()V")                       // 删除方法
        .changeMethodAccess("doSomething", "(I)V", Opcodes.ACC_PUBLIC)
        .addMethodAnnotation("doSomething", "(I)V", "Lcom/demo/Trace;") // 加方法注解
        .addClassAnnotation("Lcom/demo/Service;")               // 加类注解
        .apply();
```

> 所有破坏性修改（删除/改权限）均经 `JQuickEnhanceGuard.DEFAULT` 安全校验，构造方法、native 方法会被拒绝。

### 4. 方法头部/尾部埋点（enhance）

`JQuickMethodEnhancer` 对匹配方法插入「方法头部」与「返回前」逻辑：

```java
byte[] enhanced = JQuickMethodEnhancer.from(bytes)
        .match("doSomething")                       // 按方法名匹配（重载都会命中）
        // .match("doSomething", "(I)V")             // 按方法名+描述符精确匹配
        // .match(meta -> meta.name.startsWith("do")) // 自定义谓词
        // .matchAll()                                // 匹配所有可安全修改的方法
        .advice(JQuickMethodAdvice.builder()
                .onEnter(ctx -> {
                    // ctx.name() / ctx.descriptor() / ctx.methodVisitor()
                })
                .onExit((ctx, opcode) -> {
                    // opcode 为返回指令：RETURN / IRETURN / ARETURN / ATHROW ...
                })
                .build())
        .apply();                                    // 返回新字节码

// 一行完成「增强 + 内存加载」
Class<?> c = JQuickMethodEnhancer.from(bytes)
        .matchAll().advice(advice).applyAndDefine("com.demo.Foo");

// 一行完成「增强 + 写出文件」
JQuickMethodEnhancer.from(bytes)
        .matchAll().advice(advice).applyAndWrite("com.demo.Foo", "d:/out");
```

**钩子触发时机**：

| 钩子 | 触发位置 | 说明 |
| --- | --- | --- |
| `onEnter` | 方法体第一行 | 构造方法在 `super()` 调用之后 |
| `onExit` | 每条返回指令之前 | `RETURN/IRETURN/.../ATHROW` 各触发一次 |

### 5. try-catch 异常环绕埋点（enhance）

`JQuickTryCatchEnhancer` 用 try-catch 环绕整个方法体，提供「进入 / 正常返回 / 异常退出」三类钩子：

```java
byte[] enhanced = JQuickTryCatchEnhancer.from(bytes)
        .match("divide")
        .advice(JQuickTryCatchAdvice.builder()
                .onEnter(ctx -> print(ctx, ">> enter " + ctx.name()))
                .onExit(ctx -> print(ctx, "<< exit  " + ctx.name()))
                .onException(ctx -> {
                    // 栈顶为捕获的异常对象，需保留以供后续 ATHROW 重抛
                    MethodVisitor mv = ctx.methodVisitor();
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitLdcInsn(ctx.name());
                    mv.visitInsn(Opcodes.SWAP);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/demo/Tracer", "onError",
                            "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
                })
                .build())
        .apply();
```

**字节码结构**：

```
onEnter                       // try 之前，不受捕获影响
startLabel:
  <原始方法体>
  onExit                      // 返回前（在 try 内）
  return
endLabel:
handlerLabel:                 // catch Throwable [startLabel, endLabel)
  onException                 // 栈顶为异常，钩子需保留
  ATHROW                      // 重抛
```

> `onException` 调用时操作数栈顶为捕获的异常对象，**实现方必须保持该异常仍在栈顶**，以便增强器在钩子后执行 `ATHROW` 重抛。如需读取异常，请先 `DUP` 再调用静态方法。

### 6. 安全守卫（guard）

`JQuickEnhanceGuard` 是所有增强操作的统一安全入口，支持黑白名单与默认保护：

```java
JQuickEnhanceGuard guard = JQuickEnhanceGuard.builder()
        .blacklist("hashCode", "toString")          // 自定义黑名单
        .whitelist("doSomething", "doOther")        // 启用白名单后仅允许白名单方法
        .build();

guard.checkModifiable("doSomething", "(I)V", Opcodes.ACC_PUBLIC); // 通过
guard.checkModifiable("<init>", "()V", Opcodes.ACC_PUBLIC);        // 抛 SecurityException
```

### 7. 工具类（util）

```java
// 内存加载 / 写出文件 / 格式化打印
Class<?> clazz = JQuickBytecodeUtil.defineClass("com.demo.Foo", bytes);
String path    = JQuickBytecodeUtil.writeToFile("com.demo.Foo", bytes, "d:/out");
String text    = JQuickBytecodeUtil.dump(bytes);     // 人类可读的指令文本

// 类型转换
String desc     = JQuickTypeUtil.toDescriptor(String.class);  // "Ljava/lang/String;"
String internal = JQuickTypeUtil.classNameToInternal("com.demo.Foo"); // "com/demo/Foo"
String mDesc    = JQuickTypeUtil.methodDescriptor(int.class, String.class); // "(Ljava/lang/String;)I"

// 访问修饰符
String text = JQuickAccessUtil.toString(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC); // "public static"
boolean isStatic = JQuickAccessUtil.isStatic(access);
int newAccess = JQuickAccessUtil.changeVisibility(access, Opcodes.ACC_PUBLIC);
```

---

## 安全模型

`jquick-asm` 默认对所有增强操作进行安全校验，避免破坏 JVM 语义：

- **禁止修改构造方法** `<init>`：插桩会影响对象初始化语义
- **禁止修改静态初始化块** `<clinit>`
- **禁止修改 native 方法**：无方法体，无法插桩
- **禁止修改 abstract 方法**：无方法体
- **默认黑名单**：`getClass`、`wait`、`notify`、`notifyAll` 等 JVM 关键方法
- **自定义黑/白名单**：二次过滤，白名单启用后仅允许名单内方法被修改

不满足约束时抛出 `SecurityException`；在增强器内部，单个方法校验失败会被静默跳过（不中断整体流程）。

---

## 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | 1.8 及以上（编译目标 1.8，支持读写 JDK 8 ~ 21 字节码） |
| ASM | 9.6（传递依赖，无需手动声明） |
| 构建工具 | Maven 3.x |
| 测试 | JUnit 4.13.2（scope=test） |

---

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。

作者：Martin（goudingcheng@gmail.com）
