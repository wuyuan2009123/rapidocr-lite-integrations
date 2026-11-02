package ai.rapidocr.spring;

import ai.rapidocr.lite.CpuInfo;
import ai.rapidocr.lite.RapidOcr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RapidOCRLite 自动配置。
 *
 * <p>装配一个进程级 {@link RapidOcr} 引擎（容器关闭时自动 {@code close()}）和一个
 * {@link RapidOcrService}。业务侧注入 {@link RapidOcrService} 即可识别图片；
 * 若容器里已有自定义 {@link RapidOcr} Bean，则不再创建。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "rapidocr.lite", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RapidOcrProperties.class)
public class RapidOcrAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RapidOcrAutoConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RapidOcr rapidOcr(RapidOcrProperties properties) {
        RapidOcr ocr = properties.build();
        CpuInfo cpu = RapidOcr.cpuInfo();
        log.info("RapidOCRLite 引擎已就绪: modelType={}, backend={}, isa={}, threads={}",
                properties.getModelType(), properties.getBackend(), cpu.activeIsa(), cpu.threads());
        return ocr;
    }

    @Bean
    @ConditionalOnMissingBean
    public RapidOcrService rapidOcrService(RapidOcr rapidOcr) {
        return new RapidOcrService(rapidOcr);
    }
}
