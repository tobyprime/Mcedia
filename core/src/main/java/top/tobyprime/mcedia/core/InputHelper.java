package top.tobyprime.mcedia.core;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.Objects;

public class InputHelper {
    public static String resolveBilibili(String url)  {
       // 对 URL 进行编码
        String encodedUrl = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8);

        return "https://api.mir6.com/api/bzjiexi?url=" + encodedUrl + "&type=mp4";
    }
}
