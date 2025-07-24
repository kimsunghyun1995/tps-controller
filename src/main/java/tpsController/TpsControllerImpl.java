package tpsController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;

public class TpsControllerImpl implements TpsController {

    @Override
    public void tpsController(String url, Objects objects, int time, TimeUnit timeUnit, int postCount) {
        // 최대 1분당 100개의 요청만 보낼 수 있도록 설정
        final ExecutorService executor = new ThreadPoolExecutor(10, 100, 6, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10));

        HttpClient client = HttpClient.newHttpClient();
        for (int i = 0; i < postCount; i++) {
            executor.submit(() -> {
                try {
                    sendRequest(client, url, objects);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            try {
                // 요청 사이에 일정 시간 대기 (rate limit 준수)
                timeUnit.sleep(time / postCount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    //todo : Get, post 요청마다 생성
    private void sendRequest(HttpClient client, String url, Objects objects) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objects.toString())) // objects를 JSON 문자열로 변환하여 전송
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    }

}
