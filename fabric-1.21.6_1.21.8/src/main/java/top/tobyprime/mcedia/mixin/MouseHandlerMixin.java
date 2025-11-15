package top.tobyprime.mcedia.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia.manager.PipManager;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMousePress(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null) {
            double mouseX = client.mouseHandler.xpos();
            double mouseY = client.mouseHandler.ypos();

            if (action == GLFW.GLFW_PRESS) { // 使用 GLFW 常量更清晰
                if (PipManager.getInstance().mouseClicked(mouseX, mouseY, button)) {
                    ci.cancel();
                }
            } else if (action == GLFW.GLFW_RELEASE) {
                PipManager.getInstance().mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void onMouseMove(long window, double xpos, double ypos, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        // [关键修复] 检查鼠标左键是否被按下
        if (client.screen != null && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS) {
            PipManager.getInstance().mouseDragged(xpos, ypos, 0, xpos - client.mouseHandler.xpos(), ypos - client.mouseHandler.ypos());
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Minecraft.getInstance().screen != null) {
            double mouseX = Minecraft.getInstance().mouseHandler.xpos();
            double mouseY = Minecraft.getInstance().mouseHandler.ypos();
            if (PipManager.getInstance().mouseScrolled(mouseX, mouseY, vertical)) {
                ci.cancel();
            }
        }
    }
}