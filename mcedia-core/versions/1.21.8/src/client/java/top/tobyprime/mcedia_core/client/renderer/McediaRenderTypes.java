package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.8 version of McediaRenderTypes.
 *
 * <p>Same pipeline with {@code NO_CARDINAL_LIGHTING} to bypass angle-dependent
 * entity lighting, but uses the older {@code RenderType.CompositeState} API
 * instead of {@code RenderSetup}.
 */
public final class McediaRenderTypes {

    private McediaRenderTypes() {
    }

    static final RenderPipeline ENTITY_TRANSLUCENT_UNLIT_PIPELINE;

    private static final Function<ResourceLocation, RenderType> ENTITY_TRANSLUCENT_UNLIT;

    static {
        ENTITY_TRANSLUCENT_UNLIT_PIPELINE = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                        .withLocation("pipeline/mcedia_entity_translucent_unlit")
                        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                        .withShaderDefine("NO_CARDINAL_LIGHTING")
                        .withSampler("Sampler1")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withCull(false)
                        .build()
        );
        ENTITY_TRANSLUCENT_UNLIT = Util.memoize(
                texture -> {
                    RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false))
                            .setLightmapState(RenderStateShard.LIGHTMAP)
                            .setOverlayState(RenderStateShard.OVERLAY)
                            .createCompositeState(true);
                    return RenderType.create("mcedia_entity_translucent_unlit", 1536, true, true,
                            ENTITY_TRANSLUCENT_UNLIT_PIPELINE, compositeState);
                }
        );
    }

    public static RenderType entityTranslucentUnlit(ResourceLocation textureId) {
        return ENTITY_TRANSLUCENT_UNLIT.apply(textureId);
    }
}
