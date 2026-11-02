package ai.rapidocr.demo.solon;

import ai.rapidocr.solon.OcrResult;
import ai.rapidocr.solon.RapidOcrService;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Param;
import org.noear.solon.annotation.Post;
import org.noear.solon.core.handle.UploadedFile;

import java.io.IOException;
import java.util.Map;

@Controller
public class OcrController {

    @Inject
    private RapidOcrService ocrService;

    /** 上传图片识别：curl -F "file=@poster.jpg" http://localhost:8081/api/ocr */
    @Post
    @Mapping("/api/ocr")
    public OcrResult recognize(@Param("file") UploadedFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("缺少上传文件: file");
        }
        return ocrService.recognize(file.getContentAsBytes());
    }

    /** 引擎信息：curl http://localhost:8081/api/ocr/info */
    @Get
    @Mapping("/api/ocr/info")
    public Map<String, Object> info() {
        return ocrService.info();
    }
}
