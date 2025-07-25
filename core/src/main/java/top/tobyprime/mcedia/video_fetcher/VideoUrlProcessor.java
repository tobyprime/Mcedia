package top.tobyprime.mcedia.video_fetcher;

public class VideoUrlProcessor {
    public static String Process(String mediaUrl) throws RuntimeException {
        String realUrl = null;
        try {
            if (mediaUrl.startsWith("https://media.zenoxs.cn/")) {
                realUrl =  mediaUrl;
            }
            else if (mediaUrl.startsWith("https://live.bilibili.com/")) {
                realUrl = BiliBiliLiveFetcher.fetch(mediaUrl);
            }
            else if (mediaUrl.startsWith("https://www.bilibili.com/")) {
                realUrl = BiliBiliVideoFetcher.fetch(mediaUrl);

            }else if (mediaUrl.contains("https://v.douyin.com/")) {
                realUrl = DouyinVideoFetcher.fetch( DouyinVideoFetcher.getSharedUrl(mediaUrl));
                return realUrl;
            } else if (mediaUrl.startsWith("https://live.douyin.com/")) {
                realUrl = DouyinLiveFetcher.fetch(mediaUrl);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("无法解析: "+mediaUrl);
        }

        if (realUrl == null){
            throw new RuntimeException("无法解析: "+mediaUrl);
        }
        return realUrl;
    }
}
