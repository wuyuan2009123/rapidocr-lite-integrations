package ai.rapidocr.solon;

import java.util.List;

/**
 * 一张图片的识别结果。
 *
 * @param width         图片宽
 * @param height        图片高
 * @param elapsedMillis 引擎识别耗时（毫秒，不含图片解码）
 * @param lines         文本行，按引擎输出顺序
 */
public record OcrResult(int width, int height, long elapsedMillis, List<OcrLine> lines) {
}
