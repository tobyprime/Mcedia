package top.tobyprime.mcedia.bilibili;

import org.json.JSONArray;
import org.json.JSONObject;
import top.tobyprime.mcedia.core.MediaInfo;

import java.util.ArrayList;
import java.util.List;

public class BilibiliBangumiInfo {
    public final String seasonId;
    public final String title;
    public final List<Episode> episodes = new ArrayList<>();
    private Episode currentEpisode;
    private int currentEpisodeIndex = -1;
    private MediaInfo mediaInfo;

    private BilibiliBangumiInfo(String seasonId, String title) {
        this.seasonId = seasonId;
        this.title = title;
    }

    public static BilibiliBangumiInfo fromJson(JSONObject result, String currentEpId) {
        String seasonId = String.valueOf(result.getInt("season_id"));
        String title = result.getString("title");
        BilibiliBangumiInfo info = new BilibiliBangumiInfo(seasonId, title);

        JSONArray episodesArray = result.getJSONArray("episodes");
        for (int i = 0; i < episodesArray.length(); i++) {
            JSONObject epJson = episodesArray.getJSONObject(i);
            String epId = String.valueOf(epJson.getInt("id"));
            String cid = String.valueOf(epJson.getLong("cid"));
            String epTitle = epJson.getString("share_copy");

            Episode episode = new Episode(epId, cid, epTitle);
            info.episodes.add(episode);

            if (epId.equals(currentEpId)) {
                info.currentEpisode = episode;
                info.currentEpisodeIndex = i;
            }
        }
        return info;
    }

    public Episode getCurrentEpisode() {
        return currentEpisode;
    }

    public int getCurrentEpisodeIndex() {
        return currentEpisodeIndex;
    }

    public Episode getNextEpisode() {
        if (currentEpisodeIndex != -1 && currentEpisodeIndex + 1 < episodes.size()) {
            return episodes.get(currentEpisodeIndex + 1);
        }
        return null;
    }

    public MediaInfo getMediaInfo() {
        return mediaInfo;
    }

    public void setMediaInfo(MediaInfo mediaInfo) {
        this.mediaInfo = mediaInfo;
    }

    public static class Episode {
        public final String epId;
        public final String cid;
        public final String title;

        public Episode(String epId, String cid, String title) {
            this.epId = epId;
            this.cid = cid;
            this.title = title;
        }
    }
}