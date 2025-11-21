package top.tobyprime.mcedia.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.interfaces.IAudioSource;

import java.lang.reflect.Method;

public class SoundPhysicsRemasteredCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoundPhysicsRemasteredCompat.class);
    public static Method processSoundMethod;
    public static boolean loaded = false;
    private final static String ID_STR = "mcedia:video_sound";

    public static void SoundPhysicsProcessSource(IAudioSource audioSource) {
        try {
            processSoundMethod.invoke(null, audioSource.getId(), audioSource.getLastX(), audioSource.getLastY(), audioSource.getLastZ(), ID_STR);
        } catch (Exception e) {
            loaded = false;
            Configs.AUDIO_SOURCE_CONSUMER = null;
        }
    }

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("sound_physics_remastered")) return;
        try {
            // 获取类
            Class<?> soundPhysicsClass = Class.forName("com.sonicether.soundphysics.SoundPhysics"); // 替换为实际包名

            // 获取方法
            processSoundMethod = soundPhysicsClass.getMethod(
                    "processSound",
                    int.class, // ChannelAccessor.getSource() 返回类型
                    double.class, // x
                    double.class, // y
                    double.class, // z
                    SoundSource.class, // sound.getSource()
                    String.class  // sound.getLocation().toString()
            );
            loaded = true;
            Configs.AUDIO_SOURCE_CONSUMER = SoundPhysicsRemasteredCompat::SoundPhysicsProcessSource;
            LOGGER.info("已启用物理声效兼容");

        } catch (ClassNotFoundException | NoSuchMethodException e) {
            LOGGER.warn("无法加载物理音效" , e);
        }
    }
}
