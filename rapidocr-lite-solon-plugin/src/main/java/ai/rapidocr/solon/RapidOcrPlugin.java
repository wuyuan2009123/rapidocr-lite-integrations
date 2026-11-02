package ai.rapidocr.solon;

import ai.rapidocr.lite.CpuInfo;
import ai.rapidocr.lite.RapidOcr;
import org.noear.solon.Solon;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solon 插件入口：按 {@code rapidocr.lite.*} 配置创建进程级引擎，
 * 并把 {@link RapidOcr} 与 {@link RapidOcrService} 注册进容器；应用停止时释放引擎。
 *
 * <p>插件由 {@code META-INF/solon/rapidocr-lite.properties} 声明，随 jar 加入依赖即生效。
 */
public class RapidOcrPlugin implements Plugin {

    private static final Logger log = LoggerFactory.getLogger(RapidOcrPlugin.class);

    private RapidOcr engine;

    @Override
    public void start(AppContext context) {
        RapidOcrSettings settings = RapidOcrSettings.of(Solon.cfg());
        if (!settings.isEnabled()) {
            log.info("rapidocr.lite.enabled=false, 跳过 RapidOCRLite 装配");
            return;
        }

        engine = settings.build();
        context.wrapAndPut(RapidOcr.class, engine);
        context.wrapAndPut(RapidOcrService.class, new RapidOcrService(engine));

        CpuInfo cpu = RapidOcr.cpuInfo();
        log.info("RapidOCRLite 引擎已就绪: modelType={}, backend={}, isa={}, threads={}",
                settings.getModelType(), settings.getBackend(), cpu.activeIsa(), cpu.threads());
    }

    @Override
    public void preStop() {
        if (engine != null) {
            engine.close();
        }
    }
}
