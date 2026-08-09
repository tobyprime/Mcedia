package top.tobyprime.mcedia_platforms.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BilibiliCollectionResolverTest {
    private final BilibiliCollectionResolver resolver = new BilibiliCollectionResolver();

    @Test
    void detectsBangumiSeasonUrls() {
        assertTrue(resolver.isCollectionTarget("https://www.bilibili.com/bangumi/play/ss47561"));
        assertTrue(resolver.isCollectionTarget("https://www.bilibili.com/bangumi/play/ss47561?from_spmid=666.4.mylist.0"));
        assertTrue(resolver.isCollectionTarget("https://m.bilibili.com/bangumi/play/ss123"));
    }

    @Test
    void detectsFavoritesListUrls() {
        assertTrue(resolver.isCollectionTarget("https://www.bilibili.com/list/ml316269010"));
        assertTrue(resolver.isCollectionTarget("https://www.bilibili.com/list/ml316269010?spm_id_from=333.999.0.0"));
    }

    @Test
    void detectsBangumiEpisodeUrls() {
        assertTrue(resolver.isCollectionTarget("https://www.bilibili.com/bangumi/play/ep199933"));
        assertTrue(resolver.isCollectionTarget("https://www.bilibili.com/bangumi/play/ep199933?from_spmid=666.25.episode.0"));
        assertTrue(resolver.isCollectionTarget("https://www.bilibili.com/bangumi/play/ep249469?share_source=copy_web"));
    }

    @Test
    void doesNotDetectSingleMediaUrls() {
        assertFalse(resolver.isCollectionTarget("https://www.bilibili.com/video/BV1pRVF6kEPh"));
        assertFalse(resolver.isCollectionTarget("https://live.bilibili.com/12345"));
        assertFalse(resolver.isCollectionTarget("https://www.bilibili.com/"));
        assertFalse(resolver.isCollectionTarget(""));
        assertFalse(resolver.isCollectionTarget(null));
    }

    @Test
    void tryResolveReturnsEmptyForSingleMediaUrls() {
        assertTrue(resolver.tryResolveCollection("https://www.bilibili.com/video/BV1pRVF6kEPh").isEmpty());
        assertTrue(resolver.tryResolveCollection("https://example.com/not-bilibili").isEmpty());
    }
}
