package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * 26.3 version of McediaRenderTypes.
 *
 * <p>API difference from 26.2: the render stack moved to {@code com.mojang.renderpearl}
 * (new render engine with a Vulkan backend), and {@code RenderPipelines.register} is no
 * longer public — the built pipeline is used directly without location-map registration.
 */
public final class McediaRenderTypes {

    private McediaRenderTypes() {
    }

    static final RenderPipeline ENTITY_TRANSLUCENT_UNLIT_PIPELINE;

    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_UNLIT;

    static {
        ENTITY_TRANSLUCENT_UNLIT_PIPELINE =
                RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                        .withLocation("pipeline/mcedia_entity_translucent_unlit")
                        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                        .withShaderDefine("NO_CARDINAL_LIGHTING")
                        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                        .withCull(false)
                        .build();
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
