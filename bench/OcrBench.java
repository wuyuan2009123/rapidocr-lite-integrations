import ai.rapidocr.lite.CpuInfo;
import ai.rapidocr.lite.RapidOcr;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * RapidOCRLite 的 CPU 侧压测：单线程延迟、批处理、共享引擎并发、多引擎并发、持续负载、图片尺寸。
 *
 * <p>注意 {@link RapidOcr#recognize(BufferedImage)} 是 synchronized，单引擎会被串行化，
 * 所以“共享引擎”与“多引擎”两组数据要分开看。
 *
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -cp rapidocrlite-java.jar OcrBench.java
 * </pre>
 */
public final class OcrBench {

    private static final Path DEFAULT_IMAGE = Path.of("samples/ch_en_num.jpg");
    private static final String DEFAULT_DET = "models/PP-OCRv6_det_tiny.onnx";
    private static final String DEFAULT_REC = "models/PP-OCRv6_rec_tiny.onnx";
    private static final String DEFAULT_DICT = "models/ppocrv6_tiny_dict.txt";

    private static String det = DEFAULT_DET;
    private static String rec = DEFAULT_REC;
    private static String dict = DEFAULT_DICT;

    private static BufferedImage image;
    private static int rounds = 3;
    private static int iters = 30;
    private static int seconds = 5;

    public static void main(String[] args) throws Exception {
        Path imagePath = Path.of(args.length > 0 ? args[0] : DEFAULT_IMAGE.toString());
        if (args.length > 1) det = args[1];
        if (args.length > 2) rec = args[2];
        if (args.length > 3) dict = args[3];
        if (args.length > 4) rounds = Integer.parseInt(args[4]);
        if (args.length > 5) iters = Integer.parseInt(args[5]);
        if (args.length > 6) seconds = Integer.parseInt(args[6]);

        image = ImageIO.read(imagePath.toFile());
        if (image == null) {
            throw new IllegalStateException("无法解码图片: " + imagePath);
        }

        CpuInfo cpu = RapidOcr.cpuInfo();
        System.out.println("RapidOCRLite 压测 / image=" + imagePath.getFileName()
                + " " + image.getWidth() + "x" + image.getHeight()
                + " / model=PP-OCRv6 tiny"
                + " / cpu threads=" + cpu.threads() + " isa=" + cpu.activeIsa()
                + " avx2=" + cpu.avx2Compiled() + " avx512=" + cpu.avx512Compiled());
        System.out.println("rounds=" + rounds + " iters=" + iters + " seconds/level=" + seconds);
        System.out.println();
        System.out.println(header());

        try (RapidOcr engine = newEngine()) {
            warmup(engine);
            singleThread(engine);
            batch(engine);
            sharedEngineConcurrency(engine);
            sustained(engine);
            imageScale(engine);
        }

        multiEngineConcurrency();
    }

    /** 连续 30 秒不停机，按 5 秒窗口看吞吐是否因降频衰减。 */
    private static void sustained(RapidOcr engine) {
        section("持续负载（5 秒一窗口）");
        for (int window = 1; window <= 6; window++) {
            Samples samples = new Samples();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (System.nanoTime() < deadline) {
                long start = System.nanoTime();
                engine.recognize(image);
                samples.add(System.nanoTime() - start);
            }
            System.out.println(row("第 " + (window * 5) + " 秒", samples, 5));
        }
    }

    private static void singleThread(RapidOcr engine) {
        section("单线程延迟");
        for (int round = 1; round <= rounds; round++) {
            Samples samples = new Samples();
            long wallStart = System.nanoTime();
            for (int i = 0; i < iters; i++) {
                long start = System.nanoTime();
                engine.recognize(image);
                samples.add(System.nanoTime() - start);
            }
            double wall = (System.nanoTime() - wallStart) / 1e9;
            System.out.println(row("单线程 round-" + round, samples, wall));
        }
    }

    /** 一次提交 4 张图：验证 recognizeBatch 是否比 4 次串行调用更省。 */
    private static void batch(RapidOcr engine) {
        section("批处理（4 张/批，10 批）");
        List<BufferedImage> batchOf4 = List.of(image, image, image, image);

        Samples sequential = new Samples();
        long wallStart = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 4; j++) {
                long start = System.nanoTime();
                engine.recognize(image);
                sequential.add(System.nanoTime() - start);
            }
        }
        System.out.println(row("recognize x4 串行", sequential, (System.nanoTime() - wallStart) / 1e9));

        Samples batched = new Samples();
        wallStart = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            engine.recognizeBatch(batchOf4);
            batched.add(System.nanoTime() - start);
        }
        System.out.println(row("recognizeBatch(4)", batched, (System.nanoTime() - wallStart) / 1e9, 4)
                + "   按图片计");
    }

    private static void sharedEngineConcurrency(RapidOcr engine) throws Exception {
        section("并发（1 个引擎，被多线程共享 → 内部串行）");
        for (int threads : new int[]{1, 4, 8}) {
            for (int round = 1; round <= rounds; round++) {
                Samples samples = runFor(engine, threads, seconds);
                double wall = samples.size() == 0 ? 1 : seconds;
                System.out.println(row(threads + " 线程 round-" + round, samples, wall));
            }
        }
    }

    /** 同一张图放大 2x/3x：看延迟随像素量的增长（业务侧最直接的成本因素）。 */
    private static void imageScale(RapidOcr engine) {
        section("图片尺寸影响（同图 1x/2x/3x，各 10 次）");
        for (int scale = 1; scale <= 3; scale++) {
            BufferedImage scaled = scale == 1 ? image : resize(image, image.getWidth() * scale, image.getHeight() * scale);
            engine.recognize(scaled);
            Samples samples = new Samples();
            for (int i = 0; i < 10; i++) {
                long start = System.nanoTime();
                engine.recognize(scaled);
                samples.add(System.nanoTime() - start);
            }
            System.out.println(row(scale + "x " + scaled.getWidth() + "x" + scaled.getHeight(),
                    samples, samples.totalNanos() / 1e9));
        }
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** 每个线程一个引擎，绕过 synchronized，看真实多核扩展性。 */
    private static void multiEngineConcurrency() throws Exception {
        section("并发（每线程独立引擎）");
        int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
        List<RapidOcr> engines = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                engines.add(newEngine());
            }
            for (int round = 1; round <= rounds; round++) {
                Samples samples = runFor(engines, seconds);
                System.out.println(row(threads + " 引擎 round-" + round, samples, seconds));
            }
        } finally {
            for (RapidOcr engine : engines) {
                engine.close();
            }
        }
    }

    private static RapidOcr newEngine() {
        long start = System.nanoTime();
        RapidOcr engine = RapidOcr.forPPOCR(det, rec, dict);
        double initMs = (System.nanoTime() - start) / 1e6;
        System.out.println("引擎初始化 " + String.format(Locale.ROOT, "%.0f", initMs) + " ms");
        return engine;
    }

    private static void warmup(RapidOcr engine) {
        for (int i = 0; i < 5; i++) {
            engine.recognize(image);
        }
    }

    /** 固定时长、threads 个线程共享同一个引擎。 */
    private static Samples runFor(RapidOcr engine, int threads, int seconds) throws Exception {
        List<RapidOcr> pool = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            pool.add(engine);
        }
        return runFor(pool, seconds);
    }

    /** 固定时长、每个线程独享一个引擎。 */
    private static Samples runFor(List<RapidOcr> engines, int seconds) throws Exception {
        int threads = engines.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        Samples[] perThread = new Samples[threads];
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;

        for (int i = 0; i < threads; i++) {
            RapidOcr engine = engines.get(i);
            Samples samples = new Samples();
            perThread[i] = samples;
            pool.execute(() -> {
                try {
                    startGate.await();
                    while (System.nanoTime() < deadline) {
                        long begin = System.nanoTime();
                        engine.recognize(image);
                        samples.add(System.nanoTime() - begin);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startGate.countDown();
        finished.await();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        Samples merged = new Samples();
        for (Samples samples : perThread) {
            merged.addAll(samples);
        }
        return merged;
    }

    private static String header() {
        return pad("场景", 26) + String.format(Locale.ROOT, " %6s %9s %9s %9s %9s %8s",
                "次数", "avg ms", "p50", "p95", "p99", "img/s");
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static String row(String label, Samples samples, double wallSeconds) {
        return row(label, samples, wallSeconds, 1);
    }

    private static String row(String label, Samples samples, double wallSeconds, int imagesPerCall) {
        long[] sorted = samples.toArray();
        if (sorted.length == 0) {
            return pad(label, 26) + String.format(Locale.ROOT, " %6d %9s %9s %9s %9s %8s", 0, "-", "-", "-", "-", "-");
        }
        Arrays.sort(sorted);
        double avg = Arrays.stream(sorted).average().orElse(0) / 1e6;
        return pad(label, 26) + String.format(Locale.ROOT, " %6d %9.1f %9.1f %9.1f %9.1f %8.2f",
                sorted.length, avg,
                ms(sorted, 0.50), ms(sorted, 0.95), ms(sorted, 0.99),
                sorted.length * (long) imagesPerCall / wallSeconds);
    }

    /** 按显示宽度补空格：中日韩字符占两格，否则中文表头会错位。 */
    private static String pad(String text, int width) {
        int shown = 0;
        for (int i = 0; i < text.length(); i++) {
            shown += text.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return text + " ".repeat(Math.max(1, width - shown));
    }

    private static double ms(long[] sorted, double percentile) {
        int index = (int) Math.ceil(sorted.length * percentile) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index] / 1e6;
    }

    private static final class Samples {
        private long[] data = new long[4096];
        private int size;

        void add(long value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, size * 2);
            }
            data[size++] = value;
        }

        void addAll(Samples other) {
            for (int i = 0; i < other.size; i++) {
                add(other.data[i]);
            }
        }

        int size() {
            return size;
        }

        long[] toArray() {
            return Arrays.copyOf(data, size);
        }

        double totalNanos() {
            long total = 0;
            for (int i = 0; i < size; i++) {
                total += data[i];
            }
            return total;
        }
    }
}
