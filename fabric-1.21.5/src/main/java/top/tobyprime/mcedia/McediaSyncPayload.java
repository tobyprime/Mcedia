package top.tobyprime.mcedia;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record McediaSyncPayload(UUID uuid, long timestamp) implements CustomPacketPayload {

    // 1. 定义 ID
    public static final Type<McediaSyncPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath("mcedia", "sync"));

    // 2. 定义编解码器 (手动处理 UUID)
    public static final StreamCodec<FriendlyByteBuf, McediaSyncPayload> CODEC = StreamCodec.composite(
            // UUID 的编解码器：写入两个 Long，读取两个 Long
            StreamCodec.of(
                    (buf, val) -> {
                        buf.writeLong(val.getMostSignificantBits());
                        buf.writeLong(val.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            ),
            McediaSyncPayload::uuid,

            // Timestamp 的编解码器
            StreamCodec.of(FriendlyByteBuf::writeLong, FriendlyByteBuf::readLong),
            McediaSyncPayload::timestamp,

            // 构造函数引用
            McediaSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}