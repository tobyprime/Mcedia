package top.tobyprime.mcedia.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Rotations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.agent.PlayerConfigManager;

public class ScreenInteractionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenInteractionHelper.class);

    public static float getHitProgress(ArmorStand entity, PlayerConfigManager config, float aspectRatio) {
        Minecraft mc = Minecraft.getInstance();
        Entity player = mc.getCameraEntity();
        if (player == null) return -1;

        // 仅在按下右键时调试，防止 log 刷屏
        boolean debugging = mc.mouseHandler.isRightPressed();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        // 1. 基础属性修正
        // isSmall 决定了基础缩放倍率 (0.5 或 1.0)
        float baseScale = entity.isSmall() ? 0.5f : 1.0f;

        // 2. 构建模型矩阵 (Model Matrix) - 顺序必须与 PlayerRenderer 严格一致
        Matrix4f modelMatrix = new Matrix4f();

        // 2.1 位移到实体位置 (插值)
        Vec3 entityPos = entity.getPosition(partialTick);
        modelMatrix.translate((float) entityPos.x, (float) entityPos.y, (float) entityPos.z);

        // 2.2 应用身体旋转 (Body Y Rot)
        float bodyYRot = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        modelMatrix.rotateY((float) Math.toRadians(-bodyYRot));

        // 2.3 应用头部旋转 (Head Pose)
        Rotations headPose = entity.getHeadPose();
        float headPitch = headPose.getX();
        float headYaw   = headPose.getY();
        float headRoll  = headPose.getZ();

        // JOML rotationYXZ 顺序对应 Y->X->Z，且 Renderer 中使用了负值
        modelMatrix.rotateY((float) Math.toRadians(-headPitch));
        modelMatrix.rotateX((float) Math.toRadians(-headYaw));
        modelMatrix.rotateZ((float) Math.toRadians(-headRoll));

        // 2.4 应用配置偏移 (Offset)
        // Renderer: translate(offX, offY + 1.02 * state.scale, offZ + 0.6 * state.scale)
        float verticalOffset = 1.02f * baseScale;
        float depthOffset = 0.6f * baseScale;

        modelMatrix.translate(
                config.offsetX,
                config.offsetY + verticalOffset,
                config.offsetZ + depthOffset
        );

        // 2.5 应用最终缩放
        float finalScale = baseScale * config.scale;
        modelMatrix.scale(finalScale, finalScale, finalScale);

        // 3. 逆矩阵 (World -> Screen Local)
        Matrix4f invertModelMatrix = new Matrix4f(modelMatrix).invert();

        // 4. 射线检测
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 viewVec = player.getViewVector(partialTick);

        Vector4f localOrigin = new Vector4f((float) eyePos.x, (float) eyePos.y, (float) eyePos.z, 1.0f);
        localOrigin.mul(invertModelMatrix);

        Vector4f localDir = new Vector4f((float) viewVec.x, (float) viewVec.y, (float) viewVec.z, 0.0f);
        localDir.mul(invertModelMatrix);

        if (Math.abs(localDir.z) < 1e-6) {
            if (debugging) LOGGER.info("[Debug Hit] Ray parallel to plane");
            return -1;
        }

        // 计算与 Z=0 平面的交点
        float t = -localOrigin.z / localDir.z;

        // 距离检查
        if (t < 0 || t > 100) {
            if (debugging) LOGGER.info("[Debug Hit] Ray too far or behind: t={}", t);
            return -1;
        }

        float localX = localOrigin.x + t * localDir.x;
        float localY = localOrigin.y + t * localDir.y;

        // 5. 判定进度条区域
        float halfW = aspectRatio;

        // 判定区域定义
        float barTop = -0.95f;
        float barBottom = -1.15f;
        float barExtension = 0.1f;

        boolean inX = localX >= -halfW - barExtension && localX <= halfW + barExtension;
        boolean inY = localY <= barTop && localY >= barBottom;

        if (debugging) {
            LOGGER.info("[Debug Hit] Entity Small: {}, BaseScale: {}, FinalScale: {}", entity.isSmall(), baseScale, finalScale);
            LOGGER.info("[Debug Hit] Local Pos: ({}, {}), HalfW: {}", String.format("%.3f", localX), String.format("%.3f", localY), String.format("%.3f", halfW));
            LOGGER.info("[Debug Hit] Bounds Y: [{} ~ {}], In X: {}, In Y: {}", barBottom, barTop, inX, inY);
        }

        if (inX && inY) {
            float progress = (localX + halfW) / (halfW * 2);
            return Math.max(0, Math.min(1, progress));
        }

        return -1;
    }
}