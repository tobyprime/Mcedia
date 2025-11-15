package top.tobyprime.mcedia.bilibili;

import org.json.JSONObject;

public class BilibiliStreamSelection {
    final JSONObject stream;
    final String qualityDescription;

    BilibiliStreamSelection(JSONObject stream, String qualityDescription) {
        this.stream = stream;
        this.qualityDescription = qualityDescription;
    }
}