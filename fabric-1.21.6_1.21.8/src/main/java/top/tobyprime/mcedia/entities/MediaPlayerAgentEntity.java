package top.tobyprime.mcedia.entities;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.VideoTexture;
import top.tobyprime.mcedia.core.AudioSourceInstance;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.interfaces.IMediaPlayerScreenRenderer;

import java.util.ArrayList;

public class MediaPlayerAgentEntity extends Entity {

    public static Logger LOGGER = LoggerFactory.getLogger(MediaPlayerAgentEntity.class);
    public static final EntityType<MediaPlayerAgentEntity> TYPE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("mcedia", "player_agent"),
            EntityType.Builder.of(MediaPlayerAgentEntity::new, MobCategory.MISC).build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("mcedia", "player_agent")))
    );

    public final ArrayList<IMediaPlayerScreenRenderer> screens = new ArrayList<>();
    public final MediaPlayer player = new MediaPlayer();
    public final ArrayList<AudioSourceInstance> audioSources = new ArrayList<>();
    public @Nullable String playingUrl;
    public VideoTexture texture = new VideoTexture(ResourceLocation.fromNamespaceAndPath("mcedia", "player_agent" + this.stringUUID));
    public Quaternionf rotation = new Quaternionf();

    public MediaPlayerAgentEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);

        player.bindTexture(texture);
    }

    public void addScreen(@Nullable IMediaPlayerScreenRenderer screen) {
        this.screens.add(screen);
    }

    public void addAudioSource(AudioSourceInstance audioSource) {
        player.bindAudioSource(audioSource.audioSource);
        audioSources.add(audioSource);
    }

    public void removeAudioSource(AudioSourceInstance audioSource) {
        player.unbindAudioSource(audioSource.audioSource);
        audioSources.remove(audioSource);
    }

    public void clearAudioSources() {
        audioSources.forEach(i -> player.unbindAudioSource(i.audioSource));
        audioSources.clear();
    }

    public void clearScreens() {
        screens.clear();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {

    }

    public MediaPlayer getPlayer() {
        return player;
    }


    @Override
    public void onRemoval(RemovalReason removalReason) {
        super.onRemoval(removalReason);
        clearScreens();
        clearAudioSources();
        player.close();
    }

    @Override
    public void tick() {
        super.tick();
        this.audioSources.forEach(i -> {
            var audioOffsetRotated = new Vector3f(i.offsetX, i.offsetY, i.offsetZ).rotateX(this.getXRot()).rotateY(this.getYRot());

            i.audioSource.setPos(((float) this.getX() + audioOffsetRotated.x), ((float)this.getY() + audioOffsetRotated.y), ((float) this.getZ()  + audioOffsetRotated.z));
        });

    }
}
