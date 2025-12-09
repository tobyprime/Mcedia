package top.tobyprime.mcedia.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Rotations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import top.tobyprime.mcedia.agent.PlayerConfigManager;

public class ScreenInteractionHelper {

    public static float getHitProgress(ArmorStand entity, PlayerConfigManager config, float aspectRatio) {
        Minecraft mc = Minecraft.getInstance();
        Entity player = mc.getCameraEntity();
        if (player == null) return -1;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        // 基础属性修正
        float baseScale = entity.isSmall() ? 0.5f : 1.0f;

        // 构建模型矩阵
        Matrix4f modelMatrix = new Matrix4f();

        // 位移到实体位置 (插值)
        Vec3 entityPos = entity.getPosition(partialTick);
        modelMatrix.translate((float) entityPos.x, (float) entityPos.y, (float) entityPos.z);

        // 应用身体旋转 (Body Y Rot)
        float bodyYRot = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        modelMatrix.rotateY((float) Math.toRadians(-bodyYRot));

        // 应用头部旋转 (Head Pose)
        Rotations headPose = entity.getHeadPose();
        float headPitch = headPose.getX();
        float headYaw   = headPose.getY();
        float headRoll  = headPose.getZ();

        // JOML rotationYXZ 顺序对应 Y->X->Z，且 Renderer 中使用了负值
        modelMatrix.rotateY((float) Math.toRadians(-headPitch));
        modelMatrix.rotateX((float) Math.toRadians(-headYaw));
        modelMatrix.rotateZ((float) Math.toRadians(-headRoll));

        // 应用配置偏移 (Offset)
        // Renderer: translate(offX, offY + 1.02 * state.scale, offZ + 0.6 * state.scale)
        float verticalOffset = 1.02f * baseScale;
        float depthOffset = 0.6f * baseScale;

        modelMatrix.translate(
                config.offsetX,
                config.offsetY + verticalOffset,
                config.offsetZ + depthOffset
        );

        // 应用最终缩放
        float finalScale = baseScale * config.scale;
        modelMatrix.scale(finalScale, finalScale, finalScale);

        // 逆矩阵
        Matrix4f invertModelMatrix = new Matrix4f(modelMatrix).invert();

        // 射线检测
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 viewVec = player.getViewVector(partialTick);

        Vector4f localOrigin = new Vector4f((float) eyePos.x, (float) eyePos.y, (float) eyePos.z, 1.0f);
        localOrigin.mul(invertModelMatrix);

        Vector4f localDir = new Vector4f((float) viewVec.x, (float) viewVec.y, (float) viewVec.z, 0.0f);
        localDir.mul(invertModelMatrix);

        if (Math.abs(localDir.z) < 1e-6) {
            return -1;
        }

        // 计算与 Z=0 平面的交点
        float t = -localOrigin.z / localDir.z;

        // 距离检查
        if (t < 0 || t > 100) {
            return -1;
        }

        float localX = localOrigin.x + t * localDir.x;
        float localY = localOrigin.y + t * localDir.y;

        // 判定进度条区域
        float halfW = aspectRatio;

        // 判定区域定义
        float barTop = -0.95f;
        float barBottom = -1.15f;
        float barExtension = 0.1f;

        boolean inX = localX >= -halfW - barExtension && localX <= halfW + barExtension;
        boolean inY = localY <= barTop && localY >= barBottom;

        if (inX && inY) {
            float progress = (localX + halfW) / (halfW * 2);
            return Math.max(0, Math.min(1, progress));
        }

        return -1;
    }
}