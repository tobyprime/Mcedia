package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * 26.1 version of McediaRenderTypes.
 *
 * <p>API difference from 1.21.11: {@code RenderPipeline.Builder.withColorTargetState}
 * replaces {@code withBlend}, and {@code ColorTargetState} is a dedicated class
 * rather than a direct {@code BlendFunction} parameter.
 */
public final class McediaRenderTypes {

    private McediaRenderTypes() {
    }

    static final RenderPipeline ENTITY_TRANSLUCENT_UNLIT_PIPELINE;

    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_UNLIT;

    static {
        ENTITY_TRANSLUCENT_UNLIT_PIPELINE = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                        .withLocation("pipeline/mcedia_entity_translucent_unlit")
                        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                        .withShaderDefine("NO_CARDINAL_LIGHTING")
                        .withSampler("Sampler1")
                        .withColorTargetState(new ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
                        .withCull(false)
                        .build()
        );
        ENTITY_TRANSLUCENT_UNLIT = Util.memoize(
                identifier -> {
                    RenderSetup renderSetup = RenderSetup.builder(ENTITY_TRANSLUCENT_UNLIT_PIPELINE)
                            .withTexture("Sampler0", identifier)
                            .useLightmap()
                            .useOverlay()
                            .affectsCrumbling()
                            .sortOnUpload()
                            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                            .createRenderSetup();
                    return RenderType.create("mcedia_entity_translucent_unlit", renderSetup);
                }
        );
    }

    public static RenderType entityTranslucentUnlit(Identifier textureId) {
        return ENTITY_TRANSLUCENT_UNLIT.apply(textureId);
    }
}
