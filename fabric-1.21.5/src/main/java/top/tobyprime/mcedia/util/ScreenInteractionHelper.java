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

    public static float getHitProgress(ArmorStand entity, PlayerConfigManager config, float aspectRatio) {
        Minecraft mc = Minecraft.getInstance();
        Entity player = mc.getCameraEntity();
        if (player == null) return -1;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Matrix4f modelMatrix = new Matrix4f();

        // 基础位移
        Vec3 entityPos = entity.getPosition(partialTick);
        modelMatrix.translate((float) entityPos.x, (float) entityPos.y, (float) entityPos.z);

        // 旋转
        modelMatrix.rotateY((float) Math.toRadians(-entity.getYRot()));

        // 获取头部姿态
        Rotations headPose = entity.getHeadPose();

        float headX = headPose.x();
        float headY = headPose.y();
        float headZ = headPose.z();

        modelMatrix.rotateY((float) Math.toRadians(-headY));
        modelMatrix.rotateX((float) Math.toRadians(-headX));
        modelMatrix.rotateZ((float) Math.toRadians(-headZ));

        // 屏幕偏移配置
        modelMatrix.translate(config.offsetX, config.offsetY + 1.02f * entity.getScale(), config.offsetZ + 0.6f * entity.getScale());

        // 应用缩放
        float size = entity.getScale() * config.scale;
        modelMatrix.scale(size, size, size);

        // 构建逆矩阵
        Matrix4f invertModelMatrix = new Matrix4f(modelMatrix).invert();

        // 获取玩家视线
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 viewVec = player.getViewVector(partialTick);

        // 射线起点
        Vector4f localOrigin = new Vector4f((float) eyePos.x, (float) eyePos.y, (float) eyePos.z, 1.0f);
        localOrigin.mul(invertModelMatrix);

        // 射线方向
        Vector4f localDir = new Vector4f((float) viewVec.x, (float) viewVec.y, (float) viewVec.z, 0.0f);
        localDir.mul(invertModelMatrix);

        // 计算射线与 Z=0 平面的交点
        if (Math.abs(localDir.z) < 1e-6) return -1; // 平行，无交点

        float t = -localOrigin.z / localDir.z;
        if (t < 0 || t > 100) return -1;

        // 交点坐标
        float localX = localOrigin.x + t * localDir.x;
        float localY = localOrigin.y + t * localDir.y;

        // 判定是否在进度条区域内
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