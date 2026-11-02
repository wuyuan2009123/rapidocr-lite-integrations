# RapidOCRLite 集成：Spring Boot 4 Starter + Solon 插件

直接用官方 Java 绑定 `ai.rapidocr:rapidocrlite-java`（内含各平台 JNI + 引擎原生库），
在其上提供 Spring Boot 4 自动配置与 Solon 插件，默认对接 **PP-OCRv6** 模型，模型路径全部可配置。

## 模块

| 模块 | 说明 |
| --- | --- |
| `rapidocr-lite-spring-boot-starter` | Spring Boot 4 自动配置：`rapidocr.lite.*` 配置、`RapidOcr`/`RapidOcrService` Bean |
| `rapidocr-lite-solon-plugin` | Solon 插件：同名前缀配置，注册 `RapidOcr`/`RapidOcrService` 到容器 |
| `demo-springboot4` | Spring Boot 4 + starter 的 OCR HTTP API（8080） |
| `demo-solon` | Solon + plugin 的 OCR HTTP API（8081） |
| `models/` | PP-OCRv6 tiny 模型 + 字典 |
| `samples/` | 测试图片 `ch_en_num.jpg` |
| `bench/` | 压测程序 `OcrBench.java`，原始输出在 `bench/results/` |
| `scripts/` | 依赖安装 / 模型下载 / 压测脚本 |

两个集成模块是对等实现（包名分别为 `ai.rapidocr.spring`、`ai.rapidocr.solon`），没有共享的 common 模块。

## 1. 准备依赖

`rapidocrlite-java` 没有发布到 Maven 中央仓库，需要先装到本地仓库：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-deps.ps1
```

```bash
./scripts/install-deps.sh
```

脚本会把 jar 安装为 `ai.rapidocr:rapidocrlite-java:5.0.0a15`（jar 缺失时自动下载到 `lib/`）。

模型（PP-OCRv6 tiny/small/medium，默认 tiny 已放在 `models/`）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\download-models.ps1 -Size small
```

## 2. 配置项

Spring 用 `rapidocr.lite.*`（`application.yml`），Solon 用同样的 key（`app.properties` / `app.yml`）。

| key | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 关闭后不创建引擎 |
| `model-type` | `ppocr` | `ppocr`：det + rec + 字典；`lite`：Indra lite 模型目录 |
| `det-model` | — | PP-OCRv6 检测模型 onnx；`lite` 时为模型目录 |
| `rec-model` | — | PP-OCRv6 识别模型 onnx |
| `dict` | — | 字典文件（`ppocrv6_*_dict.txt`） |
| `cls-model` | — | 可选，文本行方向分类 onnx |
| `backend` | `cpu` | `cpu` / `gpu` / `hybrid`，须与所用 SDK 能力一致 |
| `det-limit-side-len` | `0` | 检测长边上限，0 用引擎默认 |
| `rec-parallelism` | `0` | 识别并行度，0 用引擎默认 |
| `det-threshold` | 引擎默认 | 检测二值化阈值 |
| `det-box-threshold` | 引擎默认 | 检测框得分阈值 |
| `det-unclip-ratio` | 引擎默认 | 检测框扩张系数 |
| `use-cls` | `false` | 启用方向分类（需配 `cls-model`） |
| `cls-thresh` | 引擎默认 | 180° 判定阈值 |

启动时配置的文件不存在会直接失败并给出具体路径，避免静默降级。

## 3. Spring Boot 4

```xml
<dependency>
    <groupId>ai.rapidocr</groupId>
    <artifactId>rapidocr-lite-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
rapidocr:
  lite:
    model-type: ppocr
    det-model: models/PP-OCRv6_det_tiny.onnx
    rec-model: models/PP-OCRv6_rec_tiny.onnx
    dict: models/ppocrv6_tiny_dict.txt
    backend: cpu
    det-limit-side-len: 960
    rec-parallelism: 2
```

注入即可使用：

```java
@Service
public class MyService {
    private final RapidOcrService ocr;   // 线程安全，单例复用

    public MyService(RapidOcrService ocr) { this.ocr = ocr; }

    public OcrResult run(byte[] image) {
        return ocr.recognize(image);
    }
}
```

`OcrResult(width, height, elapsedMillis, lines)`，`OcrLine(text, confidence, box, bboxX, bboxY, bboxW, bboxH, clsLabel, clsScore)`；
`RapidOcrService` 还提供 `info()`（引擎/CPU 信息）和 `engine()`（拿到底层 `RapidOcr`）。

## 4. Solon

```xml
<dependency>
    <groupId>ai.rapidocr</groupId>
    <artifactId>rapidocr-lite-solon-plugin</artifactId>
    <version>1.0.0</version>
</dependency>
```

插件由 `META-INF/solon/rapidocr-lite.properties` 声明，加依赖即生效，配置同上（写在 `app.properties`）：

```properties
rapidocr.lite.model-type=ppocr
rapidocr.lite.det-model=models/PP-OCRv6_det_tiny.onnx
rapidocr.lite.rec-model=models/PP-OCRv6_rec_tiny.onnx
rapidocr.lite.dict=models/ppocrv6_tiny_dict.txt
```

```java
@Controller
public class OcrController {
    @Inject
    private RapidOcrService ocrService;
}
```

## 5. 运行 demo

```powershell
mvn -B "-DskipTests" package

# Spring Boot 4（8080）
java -jar demo-springboot4\target\rapidocr-demo-springboot4.jar

# Solon（8081）
java -jar demo-solon\target\rapidocr-demo-solon.jar
```

```bash
curl http://localhost:8080/api/ocr/info
# {"activeIsa":"avx2","threads":8,"avx2":true,"avx512":true,"neon":false,"closed":false}

curl -F "file=@samples/ch_en_num.jpg" http://localhost:8080/api/ocr
# {"width":323,"height":430,"elapsedMillis":141,"lines":[{"text":"正品促销","confidence":0.9979868,...
```

Solon 侧同一组接口在 8081，返回结构一致。

## 6. CPU 性能压测

压测程序是 `bench/OcrBench.java`（单文件，直接跑官方 jar，不经过 Spring/Solon），一键执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\bench.ps1 -Rounds 3 -Iters 30 -Seconds 5
```

原始输出在 `bench/results/run1.txt` ~ `run3.txt`：**同一配置连跑 3 次完整压测（每次 3 轮），下表取中位数**。

测试环境：

| 项 | 值 |
| --- | --- |
| CPU | Intel Core i5-10210Y，4 核 8 线程，1.0 GHz 基准（7 W 低功耗） |
| 内存 / 系统 | 7.9 GB / Windows 10 |
| JDK | OpenJDK 25.0.4，`--enable-native-access=ALL-UNNAMED` |
| 引擎 | PP-OCRv6 **tiny**，`backend=cpu`，`activeIsa=avx2`，引擎内部线程 8 |
| 图片 | `samples/ch_en_num.jpg`，323×430 |

### 单张延迟（单线程，3 轮 × 30 次）

| avg | p50 | p95 | p99 | img/s |
| --- | --- | --- | --- | --- |
| 262 ms | 251 ms | 300 ms | 305 ms | 3.9 |

### 并发：1 个引擎被多线程共享

| 并发线程 | 吞吐 img/s | avg | p50 | p99 |
| --- | --- | --- | --- | --- |
| 1 | 4.4 | 229 ms | 228 ms | 263 ms |
| 4 | 5.2 | 832 ms | 236 ms | 3.7 s |
| 8 | 6.2 | 1.52 s | 317 ms | 4.9 s |

`RapidOcr.recognize` 是 `synchronized`，请求只能排队：吞吐从 4.4 涨到 6.2（排队把引擎喂满、省掉了调用间隙，属于假提升），代价是 avg 涨 6.7 倍、p99 到秒级。p50 仍然很低，说明"大多数请求快、少数在队列里排了很久"——这是典型的排队恶化，不是吞吐提升。

### 并发：每线程独立引擎（4 个）

| 吞吐 img/s | avg | p50 |
| --- | --- | --- |
| 5.0 | 866 ms | 837 ms |

没有扩展性：每个引擎内部已经用 8 个 ONNX 线程，4 个引擎在 4C/8T 上等于 32 个线程抢 8 个逻辑核，纯超卖。多引擎只会更费内存，不会更快。

### 批处理 API

| 调用方式 | 单张平均 | 吞吐 |
| --- | --- | --- |
| 逐张 `recognize` ×4 | 264 ms | 3.79 img/s |
| `recognizeBatch(4)` | 237 ms/张（947 ms/次） | 4.22 img/s |

`recognizeBatch` 只快 10% 左右（同一次运行内 5%~13%），仍是串行（一次调用耗时≈4 张之和），不要指望批处理提速。

### 图片尺寸

| 尺寸 | avg | img/s | 相对 1x |
| --- | --- | --- | --- |
| 1x 323×430 | 244 ms | 4.1 | 1.00 |
| 2x 646×860 | 484 ms | 2.1 | 1.98 |
| 3x 969×1290 | 582 ms | 1.7 | 2.38 |

面积翻 4 倍耗时翻约 2 倍；3x 增长放缓是因为检测阶段被默认长边上限截住，再大的图主要压在识别阶段。**图片尺寸是比任何配置项都强的成本旋钮**。

### 持续负载（30 秒，5 秒一窗口）

3.6–5.0 img/s 之间波动，没有系统性衰减（这台 7 W CPU 的睿频抖动本身就有 ±15%）。

### 结论

- 单进程吞吐上限约 **4–6 img/s**（此机 / tiny 模型 / 323×430）。加线程不加吞吐，只加排队延迟。
- 想提高吞吐只有三条路：更快的 CPU（或 GPU backend）、多进程横向扩展（每进程 2~4 并发即可，别开几十个线程排队）、减小图片尺寸或换更小的模型/det 长边。
- 引擎初始化 117–205 ms，进程启动时预热一次即可；4 个引擎约 0.7 s 且额外占内存，不建议。
- 噪声提示：同一台机器不同时段测到过单张 100 ms（CPU 空闲、睿频高）到 280 ms（持续负载后、睿频被压回 1.0 GHz），所以只看单次数字容易误判，对比方案时务必交错、重复测。

## 注意事项

- JDK 17+。JDK 24+ 的 `System.load` 受限告警可用 `--enable-native-access=ALL-UNNAMED` 消除。
- 进程级引擎实例较重（会加载 ONNX 模型），Spring/Solon 都注册为单例并在停止时释放；`RapidOcrService` 可并发调用。
- `backend=gpu/hybrid` 需要对应的 GPU SDK 版本，官方默认包只有 CPU 能力。
- 模型下载自 ModelScope `RapidAI/RapidOCR` v3.10.0，遵循其许可（PP-OCRv6 lite 模型为 modified OpenRAIL-M）。
