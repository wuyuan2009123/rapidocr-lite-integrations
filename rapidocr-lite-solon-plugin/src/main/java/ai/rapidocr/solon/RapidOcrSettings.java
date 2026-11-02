package ai.rapidocr.solon;

import ai.rapidocr.lite.OcrOptions;
import ai.rapidocr.lite.RapidOcr;
import org.noear.solon.core.Props;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 从 Solon 配置（app.properties / app.yml / 启动参数 / 环境变量）读取 {@code rapidocr.lite.*}。
 *
 * <pre>{@code
 * rapidocr.lite.model-type=ppocr
 * rapidocr.lite.det-model=models/PP-OCRv6_det_tiny.onnx
 * rapidocr.lite.rec-model=models/PP-OCRv6_rec_tiny.onnx
 * rapidocr.lite.dict=models/ppocrv6_tiny_dict.txt
 * }</pre>
 */
public final class RapidOcrSettings {

    /** 模型类型：PP-OCRv6 的 det + rec + 字典，或 Indra lite 模型目录。 */
    public enum ModelType {
        PPOCR,
        LITE
    }

    /** 运行时后端，需与下载的 SDK 能力匹配（官方 CPU SDK 只能用 CPU）。 */
    public enum Backend {
        CPU,
        GPU,
        HYBRID
    }

    private static final String PREFIX = "rapidocr.lite.";

    private final boolean enabled;
    private final ModelType modelType;
    private final String detModel;
    private final String recModel;
    private final String dict;
    private final String clsModel;
    private final Backend backend;
    private final int detLimitSideLen;
    private final int recParallelism;
    private final float detThreshold;
    private final float detBoxThreshold;
    private final float detUnclipRatio;
    private final boolean useCls;
    private final float clsThresh;

    private RapidOcrSettings(Props config) {
        this.enabled = config.getBool(PREFIX + "enabled", true);
        this.modelType = ModelType.valueOf(config.get(PREFIX + "model-type", "ppocr").trim().toUpperCase(Locale.ROOT));
        this.detModel = config.get(PREFIX + "det-model");
        this.recModel = config.get(PREFIX + "rec-model");
        this.dict = config.get(PREFIX + "dict");
        this.clsModel = config.get(PREFIX + "cls-model");
        this.backend = Backend.valueOf(config.get(PREFIX + "backend", "cpu").trim().toUpperCase(Locale.ROOT));
        this.detLimitSideLen = config.getInt(PREFIX + "det-limit-side-len", 0);
        this.recParallelism = config.getInt(PREFIX + "rec-parallelism", 0);
        this.detThreshold = config.getDouble(PREFIX + "det-threshold", Float.NaN).floatValue();
        this.detBoxThreshold = config.getDouble(PREFIX + "det-box-threshold", Float.NaN).floatValue();
        this.detUnclipRatio = config.getDouble(PREFIX + "det-unclip-ratio", Float.NaN).floatValue();
        this.useCls = config.getBool(PREFIX + "use-cls", false);
        this.clsThresh = config.getDouble(PREFIX + "cls-thresh", Float.NaN).floatValue();
    }

    public static RapidOcrSettings of(Props config) {
        return new RapidOcrSettings(config);
    }

    /** 按当前配置创建引擎；路径缺失或不存在时抛出 IllegalStateException。 */
    public RapidOcr build() {
        OcrOptions.Builder options = OcrOptions.builder()
                .backend(OcrOptions.Backend.valueOf(backend.name()))
                .detLimitSideLen(detLimitSideLen)
                .recParallelism(recParallelism)
                .detThreshold(detThreshold)
                .detBoxThreshold(detBoxThreshold)
                .detUnclipRatio(detUnclipRatio)
                .useCls(useCls)
                .clsThresh(clsThresh);

        if (clsModel != null && !clsModel.isBlank()) {
            options.clsModel(requireFile("cls-model", clsModel));
        }

        return switch (modelType) {
            case LITE -> RapidOcr.forLite(requireDirectory("det-model", detModel), options.build());
            case PPOCR -> RapidOcr.forPPOCR(
                    requireFile("det-model", detModel),
                    requireFile("rec-model", recModel),
                    requireFile("dict", dict),
                    options.build());
        };
    }

    private static String requireFile(String key, String value) {
        Path path = requirePath(key, value);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("配置 rapidocr.lite." + key + " 指向的文件不存在: " + path);
        }
        return path.toString();
    }

    private static String requireDirectory(String key, String value) {
        Path path = requirePath(key, value);
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("配置 rapidocr.lite." + key + " 指向的目录不存在: " + path);
        }
        return path.toString();
    }

    private static Path requirePath(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少配置 rapidocr.lite." + key);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ModelType getModelType() {
        return modelType;
    }

    public String getDetModel() {
        return detModel;
    }

    public String getRecModel() {
        return recModel;
    }

    public String getDict() {
        return dict;
    }

    public String getClsModel() {
        return clsModel;
    }

    public Backend getBackend() {
        return backend;
    }

    public int getDetLimitSideLen() {
        return detLimitSideLen;
    }

    public int getRecParallelism() {
        return recParallelism;
    }

    public float getDetThreshold() {
        return detThreshold;
    }

    public float getDetBoxThreshold() {
        return detBoxThreshold;
    }

    public float getDetUnclipRatio() {
        return detUnclipRatio;
    }

    public boolean isUseCls() {
        return useCls;
    }

    public float getClsThresh() {
        return clsThresh;
    }
}
