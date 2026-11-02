package ai.rapidocr.spring;

import ai.rapidocr.lite.OcrOptions;
import ai.rapidocr.lite.RapidOcr;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RapidOCRLite 配置项（前缀 {@code rapidocr.lite}）。
 *
 * <pre>{@code
 * rapidocr:
 *   lite:
 *     model-type: ppocr
 *     det-model: models/PP-OCRv6_det_tiny.onnx
 *     rec-model: models/PP-OCRv6_rec_tiny.onnx
 *     dict: models/ppocrv6_tiny_dict.txt
 * }</pre>
 */
@ConfigurationProperties(prefix = "rapidocr.lite")
public class RapidOcrProperties {

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

    /** 是否启用自动配置。 */
    private boolean enabled = true;

    private ModelType modelType = ModelType.PPOCR;

    /** PP-OCRv6 时为检测模型 onnx 路径；lite 时为包含 meta.json 的模型目录。 */
    private String detModel;

    /** 识别模型 onnx 路径（仅 PP-OCRv6）。 */
    private String recModel;

    /** 字典文件路径（仅 PP-OCRv6）。 */
    private String dict;

    /** 文本行方向分类模型 onnx 路径，可选。 */
    private String clsModel;

    private Backend backend = Backend.CPU;

    /** 检测长边限制，0 使用引擎默认值。 */
    private int detLimitSideLen;

    /** 识别并行度，0 使用引擎默认值。 */
    private int recParallelism;

    /** 检测二值化阈值，NaN 使用引擎默认值。 */
    private float detThreshold = Float.NaN;

    /** 检测框得分阈值，NaN 使用引擎默认值。 */
    private float detBoxThreshold = Float.NaN;

    /** 检测框扩张系数，NaN 使用引擎默认值。 */
    private float detUnclipRatio = Float.NaN;

    /** 是否启用文本行方向分类（需要同时配置 {@link #clsModel}）。 */
    private boolean useCls;

    /** 方向分类判定为 180° 的阈值，NaN 使用引擎默认值。 */
    private float clsThresh = Float.NaN;

    /** 按当前配置创建引擎；路径缺失或不存在时抛出 IllegalStateException。 */
    RapidOcr build() {
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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ModelType getModelType() {
        return modelType;
    }

    public void setModelType(ModelType modelType) {
        this.modelType = modelType;
    }

    public String getDetModel() {
        return detModel;
    }

    public void setDetModel(String detModel) {
        this.detModel = detModel;
    }

    public String getRecModel() {
        return recModel;
    }

    public void setRecModel(String recModel) {
        this.recModel = recModel;
    }

    public String getDict() {
        return dict;
    }

    public void setDict(String dict) {
        this.dict = dict;
    }

    public String getClsModel() {
        return clsModel;
    }

    public void setClsModel(String clsModel) {
        this.clsModel = clsModel;
    }

    public Backend getBackend() {
        return backend;
    }

    public void setBackend(Backend backend) {
        this.backend = backend;
    }

    public int getDetLimitSideLen() {
        return detLimitSideLen;
    }

    public void setDetLimitSideLen(int detLimitSideLen) {
        this.detLimitSideLen = detLimitSideLen;
    }

    public int getRecParallelism() {
        return recParallelism;
    }

    public void setRecParallelism(int recParallelism) {
        this.recParallelism = recParallelism;
    }

    public float getDetThreshold() {
        return detThreshold;
    }

    public void setDetThreshold(float detThreshold) {
        this.detThreshold = detThreshold;
    }

    public float getDetBoxThreshold() {
        return detBoxThreshold;
    }

    public void setDetBoxThreshold(float detBoxThreshold) {
        this.detBoxThreshold = detBoxThreshold;
    }

    public float getDetUnclipRatio() {
        return detUnclipRatio;
    }

    public void setDetUnclipRatio(float detUnclipRatio) {
        this.detUnclipRatio = detUnclipRatio;
    }

    public boolean isUseCls() {
        return useCls;
    }

    public void setUseCls(boolean useCls) {
        this.useCls = useCls;
    }

    public float getClsThresh() {
        return clsThresh;
    }

    public void setClsThresh(float clsThresh) {
        this.clsThresh = clsThresh;
    }
}
