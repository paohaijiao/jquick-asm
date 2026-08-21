package com.jquick.asm.bench;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Java 对象转 Map 基准测试。
 *
 * <p>对比 4 种转换策略在 100,000+ 数据量下的性能表现：
 * <ol>
 *   <li>Manual —— 手写硬编码（性能基线）</li>
 *   <li>Asm —— ASM 动态生成（jquick-asm 库）</li>
 *   <li>CachedReflect —— 反射 + 缓存 Method 元数据</li>
 *   <li>NaiveReflect —— 朴素反射（每次重新查找）</li>
 * </ol>
 *
 * <p>测试流程：预热 → 正式测量 → 正确性校验 → 打印对比表格。
 */
public class ObjectToMapBenchmarkTest {

    /**
     * 数据量：10 万 +。
     */
    private static final int DATA_SIZE = 120_000;

    /**
     * 预热轮数（让 JIT 充分编译热点代码）。
     */
    private static final int WARMUP_ROUNDS = 3;

    /**
     * 正式测量轮数（取平均）。
     */
    private static final int MEASURE_ROUNDS = 5;

    @Test
    public void benchmark() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  Java 对象转 Map 基准测试 (数据量 = " + DATA_SIZE + ")");
        System.out.println("  JVM: " + System.getProperty("java.version")
                + " | " + System.getProperty("java.vm.name"));
        System.out.println("============================================================");

        // 1. 准备测试数据
        List<BenchmarkUser> data = buildData(DATA_SIZE);
        System.out.println("已生成测试对象: " + data.size() + " 个");

        // 2. 待对比转换器
        ObjectToMapConverter[] converters = new ObjectToMapConverter[]{
                new ManualConverter(),
                new AsmConverter(),
                new CachedReflectConverter(),
                new NaiveReflectConverter(),
        };

        // 3. 正确性校验：所有转换器输出必须与 Manual 一致
        verifyCorrectness(data.get(0), converters);

        // 4. 预热 + 测量
        List<Result> results = new ArrayList<>();
        for (ObjectToMapConverter converter : converters) {
            // 预热
            for (int i = 0; i < WARMUP_ROUNDS; i++) {
                runOnce(converter, data);
            }
            // 正式测量
            long[] roundNanos = new long[MEASURE_ROUNDS];
            for (int i = 0; i < MEASURE_ROUNDS; i++) {
                roundNanos[i] = runOnce(converter, data);
            }
            results.add(new Result(converter.name(), roundNanos));
        }

        // 5. 打印对比表格
        printReport(results, data.size());
        System.out.println("============================================================");
    }

    /**
     * 跑一轮：把所有 data 转换一遍，返回耗时（纳秒）。
     */
    private long runOnce(ObjectToMapConverter converter, List<BenchmarkUser> data) {
        long start = System.nanoTime();
        for (BenchmarkUser user : data) {
            Map<String, Object> map = converter.convert(user);
            // 防止 JIT 死码消除：消费一个值
            if (map.isEmpty()) {
                throw new AssertionError("map 不应为空");
            }
        }
        return System.nanoTime() - start;
    }

    /**
     * 正确性校验。
     */
    private void verifyCorrectness(BenchmarkUser sample, ObjectToMapConverter[] converters) {
        Map<String, Object> baseline = null;
        for (ObjectToMapConverter c : converters) {
            Map<String, Object> m = c.convert(sample);
            if (baseline == null) {
                baseline = m;
                System.out.println("正确性校验基线[" + c.name() + "]: " + m);
                continue;
            }
            if (!baseline.equals(m)) {
                throw new AssertionError("转换器[" + c.name() + "] 输出与基线不一致: " + m);
            }
            System.out.println("正确性校验通过[" + c.name() + "]");
        }
        System.out.println("------------------------------------------------------------");
    }

    /**
     * 打印对比报告。
     */
    private void printReport(List<Result> results, int dataSize) {
        // 找基线（Manual）作为对比基准
        long baselineNanos = 0;
        for (Result r : results) {
            if (r.name.startsWith("Manual")) {
                baselineNanos = r.avgNanos;
                break;
            }
        }

        System.out.println();
        System.out.println("【性能对比结果】(每轮转换 " + dataSize + " 个对象，取 "
                + MEASURE_ROUNDS + " 轮平均)");
        System.out.println("+------------------------------------------+--------------+--------------+----------+");
        System.out.println("| 转换器                                   |  平均耗时(ms)|  吞吐(ops/s) |  对比    |");
        System.out.println("+------------------------------------------+--------------+--------------+----------+");
        for (Result r : results) {
            double avgMs = r.avgNanos / 1_000_000.0;
            double throughput = dataSize / (r.avgNanos / 1_000_000_000.0);
            double ratio = baselineNanos == 0 ? 0 : (double) r.avgNanos / baselineNanos;
            System.out.printf("| %-40s | %12.3f | %12.0f | %6.2fx  |%n",
                    r.name, avgMs, throughput, ratio);
        }
        System.out.println("+------------------------------------------+--------------+--------------+----------+");
        System.out.println("说明：对比列 = 该转换器耗时 / Manual(手写) 耗时；值越接近 1x 越快。");
    }

    /**
     * 构造测试数据。
     */
    private List<BenchmarkUser> buildData(int size) {
        List<BenchmarkUser> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new BenchmarkUser(
                    i,
                    "user_" + i,
                    18 + (i % 50),
                    "user_" + i + "@example.com",
                    i % 2 == 0,
                    60.0 + (i % 40),
                    "北京市朝阳区第" + i + "号"
            ));
        }
        return list;
    }

    /**
     * 单个转换器测量结果。
     */
    private static final class Result {
        final String name;
        final long avgNanos;

        Result(String name, long[] roundNanos) {
            this.name = name;
            long sum = 0;
            for (long n : roundNanos) {
                sum += n;
            }
            this.avgNanos = sum / roundNanos.length;
        }
    }
}
