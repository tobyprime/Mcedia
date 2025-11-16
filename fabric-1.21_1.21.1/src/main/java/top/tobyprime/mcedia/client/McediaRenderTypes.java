package top.tobyprime.mcedia.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class McediaRenderTypes extends RenderStateShard {

    private McediaRenderTypes(String string, Runnable runnable, Runnable runnable2) {
        super(string, runnable, runnable2);
    }

    public static final RenderType PROGRESS_BAR = RenderType.create(
            "mcedia_progress_bar",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(NO_TEXTURE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .createCompositeState(true)
    );
}