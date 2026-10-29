package dev.stormward.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A bounded, dimension-local snapshot; clients never control simulation. */
public record StormPayload(List<Frame> storms, float exposure, int entityBudget) implements CustomPayload {
    public static final Id<StormPayload> ID = new Id<>(Identifier.of("stormward", "storms"));
    public static final int MAX_STORMS = 8;
    public static final PacketCodec<RegistryByteBuf, StormPayload> CODEC = new PacketCodec<>() {
        public StormPayload decode(RegistryByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_STORMS) throw new IllegalArgumentException("Storm snapshot exceeds cap");
            List<Frame> frames = new ArrayList<>(size);
            for (int i = 0; i < size; i++) frames.add(new Frame(buf.readUuid(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));
            return new StormPayload(List.copyOf(frames), buf.readFloat(), buf.readVarInt());
        }
        public void encode(RegistryByteBuf buf, StormPayload value) {
            if (value.storms.size() > MAX_STORMS) throw new IllegalArgumentException("Storm snapshot exceeds cap");
            buf.writeVarInt(value.storms.size());
            for (Frame f : value.storms) {
                buf.writeUuid(f.id); buf.writeDouble(f.x); buf.writeDouble(f.y); buf.writeDouble(f.z);
                buf.writeDouble(f.velocityX); buf.writeDouble(f.velocityZ); buf.writeFloat(f.radius); buf.writeFloat(f.strength);
                buf.writeVarInt(f.stage); buf.writeVarInt(f.remainingTicks);
            }
            buf.writeFloat(value.exposure); buf.writeVarInt(value.entityBudget);
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
    public record Frame(UUID id, double x, double y, double z, double velocityX, double velocityZ,
                        float radius, float strength, int stage, int remainingTicks) {}
}
