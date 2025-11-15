package top.tobyprime.mcedia.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;

/**
 * 存放 Mcedia Mod 自定义 RenderType 的工具类。
 * [修复] 这个类不应该继承任何东西，它只是一个静态常量的容器。
 */
public final class McediaRenderTypes {

    /**
     * 私有构造函数，防止这个工具类被实例化。
     */
    private McediaRenderTypes() {
    }

    /**
     * 在 1.21.x 中，核心渲染状态（如透明、深度测试、剔除）被捆绑在 RenderPipeline 中。
     * 我们选用 {@link RenderPipelines#LIGHTNING}，因为它天生就是为渲染半透明、无光照、
     * 无纹理的彩色几何体而设计的。
     */
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