//package top.tobyprime.mcedia.video_fetcher;
//
//import top.tobyprime.mcedia.interfaces.IMediaFetcher;
//
//import java.util.ArrayList;
//
//public class MediaFetchers {
//    static public ArrayList<IMediaFetcher> mediaFetchers = new ArrayList<>();
//
//    static {
//        mediaFetchers.add(new BiliBiliLiveFetcher());
//        mediaFetchers.add(new top.tobyprime.mcedia.video_fetcher.BiliBiliMediaPlayProvider());
//    }
//
//    public static MediaInfo getMedia(String mediaUrl) throws RuntimeException {
//        try {
//            for (IMediaFetcher mediaFetcher : mediaFetchers) {
//                if (mediaFetcher.isValidUrl(mediaUrl)) {
//                    return mediaFetcher.getMedia(mediaUrl);
//                }
//            }
//        }
//        catch (Exception e) {
//            throw new RuntimeException("无法解析: "+mediaUrl);
//        }
//        throw new RuntimeException("无法解析: " + mediaUrl);
//    }
//}
