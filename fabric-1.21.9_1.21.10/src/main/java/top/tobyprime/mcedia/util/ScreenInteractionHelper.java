package top.tobyprime.mcedia.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import top.tobyprime.mcedia.agent.PlayerConfigManager;

public class ScreenInteractionHelper {

    /**
     * 检测玩家视线是否落在屏幕的进度条区域 (适配 1.21.6 - 1.21.8)
     * @param entity 实体
     * @param config 配置管理器
     * @param aspectRatio 屏幕宽高比 (halfW * 2 / 2.0f) -> width / height
     * @return 如果击中，返回 0.0~1.0 的进度值；未击中返回 -1。
     */
    public static float getHitProgress(ArmorStand entity, PlayerConfigManager config, float aspectRatio) {
        Minecraft mc = Minecraft.getInstance();
        Entity player = mc.getCameraEntity();
        if (player == null) return -1;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Matrix4f modelMatrix = new Matrix4f();

        Vec3 entityPos = entity.getPosition(partialTick);
        modelMatrix.translate((float) entityPos.x, (float) entityPos.y, (float) entityPos.z);

        modelMatrix.rotateY((float) Math.toRadians(-entity.getYRot()));

        Rotations headPose = entity.getHeadPose();

        float headX = headPose.x();
        float headY = headPose.y();
        float headZ = headPose.z();

        modelMatrix.rotateY((float) Math.toRadians(-headY));
        modelMatrix.rotateX((float) Math.toRadians(-headX));
        modelMatrix.rotateZ((float) Math.toRadians(-headZ));

        modelMatrix.translate(config.offsetX, config.offsetY + 1.02f * entity.getScale(), config.offsetZ + 0.6f * entity.getScale());

        float size = entity.getScale() * config.scale;
        modelMatrix.scale(size, size, size);

        Matrix4f invertModelMatrix = new Matrix4f(modelMatrix).invert();

        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 viewVec = player.getViewVector(partialTick);

        Vector4f localOrigin = new Vector4f((float) eyePos.x, (float) eyePos.y, (float) eyePos.z, 1.0f);
        localOrigin.mul(invertModelMatrix);

        Vector4f localDir = new Vector4f((float) viewVec.x, (float) viewVec.y, (float) viewVec.z, 0.0f);
        localDir.mul(invertModelMatrix);

        if (Math.abs(localDir.z) < 1e-6) return -1;

        float t = -localOrigin.z / localDir.z;
        if (t < 0 || t > 100) return -1;

        float localX = localOrigin.x + t * localDir.x;
        float localY = localOrigin.y + t * localDir.y;

        float halfW = aspectRatio;
        float barTop = -1.0f;
        float barBottom = -1.15f;
        float barExtension = 0.1f;

        if (localX >= -halfW - barExtension && localX <= halfW + barExtension) {
            if (localY <= barTop + 0.05f && localY >= barBottom) {
                float progress = (localX + halfW) / (halfW * 2);
                return Math.max(0, Math.min(1, progress));
            }
        }

        return -1;
    }
}