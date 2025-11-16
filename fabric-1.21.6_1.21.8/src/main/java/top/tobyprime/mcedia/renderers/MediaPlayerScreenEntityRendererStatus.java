package top.tobyprime.mcedia.renderers;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import top.tobyprime.mcedia.VideoTexture;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.interfaces.IMediaPlayerScreenRenderer;

import java.util.ArrayList;

public class MediaPlayerScreenEntityRendererStatus extends EntityRenderState {
    public VideoTexture texture;
    public ArrayList<IMediaPlayerScreenRenderer> screens;
    public MediaPlayer player;
    public Quaternionf rotation;
}
