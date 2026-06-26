package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Custom RenderType instances that bypass angle-dependent entity lighting
 * ({@code NO_CARDINAL_LIGHTING} instead of {@code PER_FACE_LIGHTING}).
 *
 * <p>Vanilla {@code RenderTypes.entityTranslucent} uses {@code PER_FACE_LIGHTING},
 * which multiplies vertex color by a dot-product of the face normal with the
 * sun/moon direction. This makes the screen appear darker on the side facing
 * away from the sky, even with maximum lightmap coordinates. The variants here
 * use {@code NO_CARDINAL_LIGHTING} so that only the lightmap (controlled via
 * {@code lightCoords}) determines brightness.
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
                        .withBlend(BlendFunction.TRANSLUCENT)
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

    /**
     * Translucent entity-style RenderType that does <b>not</b> modulate
     * brightness based on the face normal's angle to the sky. Only the
     * lightmap ({@code lightCoords}) controls brightness.
     */
    public static RenderType entityTranslucentUnlit(Identifier textureId) {
        return ENTITY_TRANSLUCENT_UNLIT.apply(textureId);
    }
}
