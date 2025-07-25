package top.tobyprime.mcedia.video_fetcher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DouyinLiveFetcher {
    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS) // 自动跟随短链跳转
            .build();

    public static String fetch(String rid) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rid))
                    .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) " +
                            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Unexpected HTTP status: " + response.statusCode());
            }

            String body = response.body();
            Pattern pattern = Pattern.compile("\"LiveUrl\":\"(.*?m3u8)\",");
            Matcher matcher = pattern.matcher(body);

            if (matcher.find()) {
                return matcher.group(1);
            } else {
                throw new IllegalArgumentException("Link invalid or pattern not found");
            }
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            return null;
        }
    }

}
