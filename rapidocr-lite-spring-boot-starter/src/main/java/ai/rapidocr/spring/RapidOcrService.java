package ai.rapidocr.spring;

import ai.rapidocr.lite.CpuInfo;
import ai.rapidocr.lite.RapidOcr;
import ai.rapidocr.lite.TextLine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 业务侧 OCR 入口：图片解码（ImageIO）、结果转换与耗时统计。
 *
 * <p>线程安全：底层 {@link RapidOcr#recognize(BufferedImage)} 已串行化，因此单个实例
 * 可被多线程共用；需要真正并行时，按线程或按需创建多个 {@link RapidOcr} 引擎。
 */
public final class RapidOcrService {

    private final RapidOcr engine;

    public RapidOcrService(RapidOcr engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /** 识别内存中的图片字节（JPEG/PNG/BMP/GIF 等 ImageIO 支持的格式）。 */
    public OcrResult recognize(byte[] imageBytes) {
        Objects.requireNonNull(imageBytes, "imageBytes");
        return recognize(decode(new ByteArrayInputStream(imageBytes)));
    }

    /** 识别输入流中的图片，流由本方法负责关闭。 */
    public OcrResult recognize(InputStream in) {
        Objects.requireNonNull(in, "in");
        return recognize(decode(in));
    }

    /** 识别图片文件。 */
    public OcrResult recognize(Path imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile.toFile());
        if (image == null) {
            throw new IllegalArgumentException("无法解码图片：" + imageFile.toAbsolutePath());
        }
        return recognize(image);
    }

    /** 识别已解码的图片。 */
    public OcrResult recognize(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        long start = System.nanoTime();
        List<TextLine> lines = engine.recognize(image);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        List<OcrLine> converted = new ArrayList<>(lines.size());
        for (TextLine line : lines) {
            converted.add(OcrLine.of(line));
        }
        return new OcrResult(image.getWidth(), image.getHeight(), elapsedMillis, List.copyOf(converted));
    }

    /** 引擎与运行环境信息，适合做健康检查或运维接口。 */
    public Map<String, Object> info() {
        CpuInfo cpu = RapidOcr.cpuInfo();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("activeIsa", cpu.activeIsa());
        info.put("threads", cpu.threads());
        info.put("avx2", cpu.avx2Compiled());
        info.put("avx512", cpu.avx512Compiled());
        info.put("neon", cpu.neonCompiled());
        info.put("closed", engine.isClosed());
        return info;
    }

    public RapidOcr engine() {
        return engine;
    }

    private static BufferedImage decode(InputStream in) {
        try (InputStream source = in) {
            BufferedImage image = ImageIO.read(source);
            if (image == null) {
                throw new IllegalArgumentException("无法解码图片：格式不受支持或数据已损坏");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException("读取图片失败", e);
        }
    }
}
