package tpsController;

import com.google.common.util.concurrent.RateLimiter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TpsControllerImpl implements TpsController {

	// 초당 100건 제한 (분당 6000건 ≈ 기존 요구사항)
	private static final RateLimiter RATE_LIMITER = RateLimiter.create(100);

	private static final int MAX_RETRIES = 3;
	private static final int BACKOFF_CAP_SECONDS = 8;

	@Override
	public void tpsController(String url, Objects payload,
							  int interval, TimeUnit unit, int postCount) {

		ExecutorService executor = new ThreadPoolExecutor(
				10, 100,
				6, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(100) // 큐 사이즈 확대
		);

		HttpClient client = HttpClient.newHttpClient();

		for (int i = 0; i < postCount; i++) {
			executor.submit(() -> {
				// 1) 토큰을 얻을 때까지 블로킹 → 정확한 TPS 보장
				RATE_LIMITER.acquire();
				try {
					sendRequestWithRetry(client, url, payload);
				} catch (Exception e) {
					// TODO: 로깅/모니터링
					e.printStackTrace();
				}
			});
		}
		shutdownGracefully(executor);
	}

	/**
	 * 재시도·백오프 포함 요청
	 */
	private void sendRequestWithRetry(HttpClient client, String url, Objects payload) throws Exception {
		int attempt = 0;
		while (true) {
			HttpResponse<String> response = sendRequest(client, url, payload);
			int code = response.statusCode();
			if (code < 500 && code != 429) return; // 성공·클라이언트 오류면 종료

			if (++attempt > MAX_RETRIES) {
				throw new RuntimeException("Max retry exceeded. lastCode=" + code);
			}
			long backoff = Math.min((1 << attempt), BACKOFF_CAP_SECONDS);
			TimeUnit.SECONDS.sleep(backoff);
		}
	}

	private HttpResponse<String> sendRequest(HttpClient client, String url, Objects payload) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(new URI(url))
				.timeout(Duration.ofSeconds(10))   // 요청 총 타임아웃 :contentReference[oaicite:6]{index=6}
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private void shutdownGracefully(ExecutorService executor) {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException ex) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
