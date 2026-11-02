package ai.rapidocr.solon;

import ai.rapidocr.lite.TextLine;

/**
 * 识别出的一行文本，直接用于 JSON 序列化。
 *
 * @param text       文本内容
 * @param confidence 置信度（0~1）
 * @param box        四点坐标 TL、TR、BR、BL，每点为 {x, y}
 * @param bboxX      外接矩形左上角 x
 * @param bboxY      外接矩形左上角 y
 * @param bboxW      外接矩形宽
 * @param bboxH      外接矩形高
 * @param clsLabel   方向分类结果（0 = 0°，1 = 180°，-1 = 未分类）
 * @param clsScore   方向分类置信度
 */
public record OcrLine(String text, float confidence, float[][] box,
                      int bboxX, int bboxY, int bboxW, int bboxH,
                      int clsLabel, float clsScore) {

    static OcrLine of(TextLine line) {
        return new OcrLine(line.text(), line.confidence(), line.box(),
                line.bboxX(), line.bboxY(), line.bboxW(), line.bboxH(),
                line.clsLabel(), line.clsScore());
    }
}
