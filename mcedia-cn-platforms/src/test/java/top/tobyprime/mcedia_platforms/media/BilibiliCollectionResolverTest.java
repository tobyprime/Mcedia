package top.tobyprime.mcedia_platforms.media;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void tryResolveReturnsEmptyForUnsupportedTargets() {
        assertTrue(resolver.tryResolveCollection("https://example.com/not-bilibili").isEmpty());
        assertTrue(resolver.tryResolveCollection("https://live.bilibili.com/12345").isEmpty());
        assertTrue(resolver.tryResolveCollection("https://www.bilibili.com/").isEmpty());
    }

    // -- video collection (多分P / ugc_season) pure resolution --

    @Test
    void buildVideoCollectionBuildsAllPartsForMultiPageVideo() {
        var data = JsonParser.parseString("""
                {"bvid":"BV1abc1234567","title":"【合集】全三话","pic":"http://i0.hdslb.com/pic.jpg",
                 "pages":[
                    {"cid":1,"page":1,"part":"01"},
                    {"cid":2,"page":2,"part":"02"},
                    {"cid":3,"page":3,"part":"03"}
                 ]}
                """).getAsJsonObject();

        var collection = BilibiliCollectionResolver.buildVideoCollection(data);

        assertTrue(collection.isPresent());
        assertEquals("【合集】全三话", collection.get().getTitle());
        assertEquals("https://i0.hdslb.com/pic.jpg", collection.get().getCoverUrl());
        var items = collection.get().getItems();
        assertEquals(3, items.size());
        assertEquals("01", items.get(0).getTitle());
        assertEquals("https://www.bilibili.com/video/BV1abc1234567?p=1", items.get(0).getResolutionTarget());
        assertEquals("03", items.get(2).getTitle());
        assertEquals("https://www.bilibili.com/video/BV1abc1234567?p=3", items.get(2).getResolutionTarget());
    }

    @Test
    void buildVideoCollectionReturnsEmptyForSinglePartVideo() {
        var data = JsonParser.parseString("""
                {"bvid":"BV1abc1234567","title":"单个视频",
                 "pages":[{"cid":1,"page":1,"part":"唯一分P"}]}
                """).getAsJsonObject();

        assertTrue(BilibiliCollectionResolver.buildVideoCollection(data).isEmpty());
    }

    @Test
    void buildVideoCollectionBuildsCollectionFromUgcSeason() {
        var data = JsonParser.parseString("""
                {"bvid":"BV1abc1234567","title":"视频","pic":"http://i0.hdslb.com/pic.jpg",
                 "ugc_season":{
                    "title":"我做的合集","cover":"http://i1.hdslb.com/cover.jpg",
                    "episodes":[
                        {"bvid":"BV1abc1234567","page":1,"title":"第1集"},
                        {"bvid":"BV2xyz0000000","page":1,"title":"第2集"}
                    ]}}
                """).getAsJsonObject();

        var collection = BilibiliCollectionResolver.buildVideoCollection(data);

        assertTrue(collection.isPresent());
        assertEquals("我做的合集", collection.get().getTitle());
        assertEquals("https://i1.hdslb.com/cover.jpg", collection.get().getCoverUrl());
        var items = collection.get().getItems();
        assertEquals(2, items.size());
        assertEquals("第1集", items.get(0).getTitle());
        assertEquals("https://www.bilibili.com/video/BV1abc1234567?p=1", items.get(0).getResolutionTarget());
        assertEquals("https://www.bilibili.com/video/BV2xyz0000000?p=1", items.get(1).getResolutionTarget());
    }

    @Test
    void buildVideoCollectionBuildsCollectionFromUgcSeasonSections() {
        var data = JsonParser.parseString("""
                {"bvid":"BV1ymMyzqEuU","title":"单个视频","pic":"http://i0.hdslb.com/pic.jpg",
                 "ugc_season":{
                    "title":"服务器宣传","cover":"http://i1.hdslb.com/cover.jpg",
                    "sections":[
                        {"title":"正片","episodes":[
                            {"bvid":"BV1ymMyzqEuU","page":{"cid":1,"page":1,"part":"招新"},"title":"招新视频"},
                            {"bvid":"BV1abctest99","page":{"cid":2,"page":3,"part":"第三页"},"title":"第三页视频"}
                        ]}
                    ]}}
                """).getAsJsonObject();

        var collection = BilibiliCollectionResolver.buildVideoCollection(data);

        assertTrue(collection.isPresent());
        assertEquals("服务器宣传", collection.get().getTitle());
        assertEquals("https://i1.hdslb.com/cover.jpg", collection.get().getCoverUrl());
        var items = collection.get().getItems();
        assertEquals(2, items.size());
        assertEquals("招新视频", items.get(0).getTitle());
        assertEquals("https://www.bilibili.com/video/BV1ymMyzqEuU?p=1", items.get(0).getResolutionTarget());
        assertEquals("第三页视频", items.get(1).getTitle());
        assertEquals("https://www.bilibili.com/video/BV1abctest99?p=3", items.get(1).getResolutionTarget());
    }

    @Test
    void buildVideoCollectionPrefersMultiPageOverSingleVideoSeasonWrapper() {
        var data = JsonParser.parseString("""
                {"bvid":"BV1LXMo6kEDe","title":"正片4K","pic":"http://i0.hdslb.com/pic.jpg",
                 "pages":[
                    {"cid":1,"page":1,"part":"P01"},
                    {"cid":2,"page":2,"part":"P02"},
                    {"cid":3,"page":3,"part":"P03"}
                 ],
                 "ugc_season":{
                    "title":"蜘蛛侠","cover":"http://i1.hdslb.com/cover.jpg",
                    "sections":[{"title":"正片","episodes":[
                        {"bvid":"BV1LXMo6kEDe","page":{"cid":9,"page":1,"part":"P01"},"title":"正片"}
                    ]}]}}
                """).getAsJsonObject();

        var collection = BilibiliCollectionResolver.buildVideoCollection(data);

        assertTrue(collection.isPresent());
        assertEquals("正片4K", collection.get().getTitle());
        var items = collection.get().getItems();
        assertEquals(3, items.size());
        assertEquals("P01", items.get(0).getTitle());
        assertEquals("https://www.bilibili.com/video/BV1LXMo6kEDe?p=1", items.get(0).getResolutionTarget());
        assertEquals("https://www.bilibili.com/video/BV1LXMo6kEDe?p=3", items.get(2).getResolutionTarget());
    }

    @Test
    void buildVideoCollectionReturnsEmptyWhenNoPlayableStructurePresent() {
        assertTrue(BilibiliCollectionResolver.buildVideoCollection(JsonParser.parseString("{}").getAsJsonObject()).isEmpty());
        assertTrue(BilibiliCollectionResolver.buildVideoCollection(
                JsonParser.parseString("{\"bvid\":\"BV1abc1234567\",\"title\":\"x\"}").getAsJsonObject()).isEmpty());
    }
}
