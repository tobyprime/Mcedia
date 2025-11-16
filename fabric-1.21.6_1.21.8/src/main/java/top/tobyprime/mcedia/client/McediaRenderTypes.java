package top.tobyprime.mcedia.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class McediaRenderTypes extends RenderStateShard {

    private McediaRenderTypes(String string, Runnable runnable, Runnable runnable2) {
        super(string, runnable, runnable2);
    }

    public static final RenderType PROGRESS_BAR = RenderType.create(
            "mcedia_progress_bar",
            256, // 缓冲区大小 (默认即可)
            false, // affectsCrumbling: 不受方块破碎效果影响
            true,  // sortOnUpload: 需要排序 (因为是半透明)
            RenderPipelines.LIGHTNING, // [关键] 使用 lightning 渲染管线，它包含了正确的透明度和深度设置
            // CompositeState 现在非常简单，因为管线已经处理了大部分状态。
            // 我们只需要一个不使用任何额外效果的默认状态即可。
            RenderType.CompositeState.builder().createCompositeState(false)
    );
}