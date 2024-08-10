package tpsController;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public interface TpsController {
    // URL, 시간 , 보낼수 있는 횟수
    void tpsController(String url, Objects objects ,int time, TimeUnit timeUnit, int PostCount);
}
