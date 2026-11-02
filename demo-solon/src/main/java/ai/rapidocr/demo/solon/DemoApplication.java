package ai.rapidocr.demo.solon;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

@SolonMain
public class DemoApplication {

    public static void main(String[] args) {
        Solon.start(DemoApplication.class, args);
    }
}
