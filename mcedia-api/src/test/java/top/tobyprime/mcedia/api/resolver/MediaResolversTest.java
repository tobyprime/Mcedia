package top.tobyprime.mcedia.api.resolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia.api.media.Media;
import top.tobyprime.mcedia.api.media.MediaInfo;
import top.tobyprime.mcedia.api.media.MediaPlayInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaResolversTest {

    @BeforeEach
    void setUp() {
        MediaResolvers.reset();
    }

    // -- resolveByPlatform tests --

    @Test
    void resolvesByPlatform() {
        String platform = "by-platform-" + UUID.randomUUID();
        MediaResolvers.register(platform, target -> new TestMedia("result-" + target));

        Media resolved = MediaResolvers.resolveByPlatform(platform, "test-input");

        assertEquals("result-test-input", resolved.getPlayInfo().getUrl());
    }

    @Test
    void resolveByPlatformIsCaseInsensitive() {
        String platform = "case-" + UUID.randomUUID();
        MediaResolvers.register(platform, target -> new TestMedia("case-insensitive"));

        Media resolved = MediaResolvers.resolveByPlatform(platform.toUpperCase(), "x");

        assertEquals("case-insensitive", resolved.getPlayInfo().getUrl());
    }

    @Test
    void resolveByPlatformThrowsOnMissing() {
        String platform = "nonexistent-" + UUID.randomUUID();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MediaResolvers.resolveByPlatform(platform, "anything"));
        assertEquals("No media resolver registered for platform: " + platform, ex.getMessage());
    }

    @Test
    void allowsResolverOverwrite() {
        String platform = "overwrite-" + UUID.randomUUID();
        MediaResolvers.register(platform, target -> new TestMedia("first-" + target));
        MediaResolvers.register(platform, target -> new TestMedia("second-" + target));

        Media resolved = MediaResolvers.resolveByPlatform(platform, "abc");

        assertEquals("second-abc", resolved.getPlayInfo().getUrl());
    }

    // -- resolve() without parsers --

    @Test
    void throwsWhenNoParserMatches() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MediaResolvers.resolve("anything-at-all"));
        assertEquals("Unsupported media url: anything-at-all", ex.getMessage());
    }

    // -- parser-based resolution tests --

    @Test
    void resolvesByParser() {
        MediaResolvers.registerParser(input -> {
            if (input.equals("custom://test")) {
                return Optional.of(new TestMedia("parser-resolved"));
            }
            return Optional.empty();
        });

        Media resolved = MediaResolvers.resolve("custom://test");

        assertEquals("parser-resolved", resolved.getPlayInfo().getUrl());
    }

    @Test
    void parserWithEmptyResultContinuesToNextParser() {
        MediaResolvers.registerParser(input -> Optional.empty());
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia("second-wins")), 0);

        Media resolved = MediaResolvers.resolve("anything");

        assertEquals("second-wins", resolved.getPlayInfo().getUrl());
    }

    @Test
    void allParsersReturnEmptyThrows() {
        MediaResolvers.registerParser(input -> Optional.empty());
        MediaResolvers.registerParser(input -> Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MediaResolvers.resolve("foo"));
        assertEquals("Unsupported media url: foo", ex.getMessage());
    }

    @Test
    void parserPriorityRespected() {
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia("second")), 20);
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia("first")), 0);

        Media resolved = MediaResolvers.resolve("anything");

        assertEquals("first", resolved.getPlayInfo().getUrl());
    }

    @Test
    void stripsWrappingQuotesBeforeParsing() {
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia(input)));

        Media resolved = MediaResolvers.resolve("\"D:\\videos\\test.mp4\"");

        assertEquals("D:\\videos\\test.mp4", resolved.getPlayInfo().getUrl());
    }

    @Test
    void higherPriorityParserWinsForSameInput() {
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia("low")), 10);
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia("high")), 5);
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia("highest")), 0);

        Media resolved = MediaResolvers.resolve("anything");

        assertEquals("highest", resolved.getPlayInfo().getUrl());
    }

    @Test
    void parserAddedAfterAnotherStillRespectsPriority() {
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia("b")), 10);
        MediaResolvers.registerParser(input -> Optional.of(new TestMedia("a")), 0);

        Media resolved = MediaResolvers.resolve("anything");

        assertEquals("a", resolved.getPlayInfo().getUrl());
    }

    // -- collection resolver registry tests --

    @Test
    void tryResolveCollectionReturnsEmptyWhenNoCollectionResolverRegistered() {
        assertTrue(MediaResolvers.tryResolveCollection("https://example.com/album").isEmpty());
    }

    @Test
    void tryResolveCollectionUsesFirstMatchingCollectionResolver() {
        MediaResolvers.registerCollectionResolver("platform-a", target -> target.startsWith("a:")
                ? Optional.of(new TestCollection("album-a", List.of()))
                : Optional.empty());
        MediaResolvers.registerCollectionResolver("platform-b", target -> target.startsWith("b:")
                ? Optional.of(new TestCollection("album-b", List.of()))
                : Optional.empty());

        var resolved = MediaResolvers.tryResolveCollection("b:123");

        assertTrue(resolved.isPresent());
        assertEquals("album-b", resolved.get().getTitle());
    }

    @Test
    void tryResolveCollectionSkipsMismatchedResolvers() {
        MediaResolvers.registerCollectionResolver("platform-a", target -> target.startsWith("a:")
                ? Optional.of(new TestCollection("album-a", List.of()))
                : Optional.empty());

        var resolved = MediaResolvers.tryResolveCollection("https://example.com/video");

        assertTrue(resolved.isEmpty());
    }

    @Test
    void collectionResolverOverwriteIsAllowed() {
        MediaResolvers.registerCollectionResolver("platform", target -> Optional.of(new TestCollection("first", List.of())));
        MediaResolvers.registerCollectionResolver("platform", target -> Optional.of(new TestCollection("second", List.of())));

        var resolved = MediaResolvers.tryResolveCollection("anything");

        assertEquals("second", resolved.get().getTitle());
    }

    private static final class TestCollection implements top.tobyprime.mcedia.api.media.MediaCollection {
        private final String title;
        private final List<top.tobyprime.mcedia.api.media.MediaCollectionItem> items;

        private TestCollection(String title, List<top.tobyprime.mcedia.api.media.MediaCollectionItem> items) {
            this.title = title;
            this.items = items;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getCoverUrl() {
            return null;
        }

        @Override
        public List<top.tobyprime.mcedia.api.media.MediaCollectionItem> getItems() {
            return items;
        }
    }

    private static final class TestMedia implements Media {
        private final MediaPlayInfo playInfo;
        private final MediaInfo info;

        private TestMedia(String url) {
            this.playInfo = new MediaPlayInfo(url);
            this.info = new MediaInfo();
        }

        @Override
        public MediaPlayInfo getPlayInfo() {
            return playInfo;
        }

        @Override
        public MediaInfo getInfo() {
            return info;
        }
    }
}
