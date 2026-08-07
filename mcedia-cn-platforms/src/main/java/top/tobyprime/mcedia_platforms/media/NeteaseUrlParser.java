package top.tobyprime.mcedia_platforms.media;

import top.tobyprime.mcedia.api.media.Media;
import top.tobyprime.mcedia.api.resolver.MediaResolvers;
import top.tobyprime.mcedia.api.resolver.MediaUrlParser;

import java.util.Optional;
import java.util.regex.Pattern;

public class NeteaseUrlParser implements MediaUrlParser {
    private static final Pattern NETEASE_DOMAIN_PATTERN = Pattern.compile(
            "^(https?://)?([\\w-]+\\.)?163\\.com/.*"
    );
    private static final Pattern SONG_ID_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern NETEASE_URL_IN_TEXT_PATTERN = Pattern.compile(
            "(https?://[\\w-]*\\.?163\\.com/\\S+)"
    );
    private static final Pattern NETEASE_SONG_ID_IN_TEXT_PATTERN = Pattern.compile(
            "(?:music\\.163\\.com/(?:#/)?song(?:\\?id=|/))(\\d+)"
    );

    @Override
    public Optional<Media> tryParse(String input) {
        var normalizedInput = extractSupportedInput(input);
        return normalizedInput != null
                ? Optional.of(MediaResolvers.resolveByPlatform("netease", normalizedInput))
                : Optional.empty();
    }

    private static String extractSupportedInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        var trimmed = input.trim();
        if (NETEASE_DOMAIN_PATTERN.matcher(trimmed).matches()
                || SONG_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        var songIdMatcher = NETEASE_SONG_ID_IN_TEXT_PATTERN.matcher(trimmed);
        if (songIdMatcher.find()) {
            return songIdMatcher.group(1);
        }

        var urlMatcher = NETEASE_URL_IN_TEXT_PATTERN.matcher(trimmed);
        if (urlMatcher.find()) {
            return urlMatcher.group(1);
        }

        return null;
    }
}
