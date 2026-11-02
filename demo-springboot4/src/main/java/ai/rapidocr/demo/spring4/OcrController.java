package ai.rapidocr.demo.spring4;

import ai.rapidocr.spring.OcrResult;
import ai.rapidocr.spring.RapidOcrService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final RapidOcrService ocrService;

    public OcrController(RapidOcrService ocrService) {
        this.ocrService = ocrService;
    }

    /** 上传图片识别：curl -F "file=@poster.jpg" http://localhost:8080/api/ocr */
    @PostMapping
    public OcrResult recognize(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("缺少上传文件: file");
        }
        return ocrService.recognize(file.getBytes());
    }

    /** 引擎信息：curl http://localhost:8080/api/ocr/info */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return ocrService.info();
    }
}
