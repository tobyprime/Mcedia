package top.tobyprime.mcedia.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import top.tobyprime.mcedia.entities.MediaPlayerAgentEntity;
import top.tobyprime.mcedia.renderers.MediaPlayerAgentEntityRenderer;

public class McediaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MediaPlayerAgentEntity.TYPE, MediaPlayerAgentEntityRenderer::new);
    }
}
