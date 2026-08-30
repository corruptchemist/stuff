package dev.protomanly.pmweather.data;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class DataAttachments {
   public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "pmweather");
   public static final Supplier<AttachmentType<Float>> MOISTURE = ATTACHMENT_TYPES.register(
      "moisture", () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).build()
   );
   public static final Supplier<AttachmentType<Long>> LAST_TICK = ATTACHMENT_TYPES.register(
      "last_tick", () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build()
   );
   public static final Supplier<AttachmentType<Long>> LAST_TIME = ATTACHMENT_TYPES.register(
      "last_time", () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build()
   );
   public static final Supplier<AttachmentType<Float>> FIRE_INTENSITY = ATTACHMENT_TYPES.register(
      "fire_intensity", () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).build()
   );
   public static final Supplier<AttachmentType<Float>> STABLE_FIRE_INTENSITY = ATTACHMENT_TYPES.register(
      "stable_fire_intensity", () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).build()
   );
   public static final Supplier<AttachmentType<Float>> FIRE_AFTERMATH = ATTACHMENT_TYPES.register(
      "fire_aftermath", () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).build()
   );
   public static final Supplier<AttachmentType<BlockDataHandler>> BLOCK_DATA = ATTACHMENT_TYPES.register(
      "block_data", () -> AttachmentType.serializable(BlockDataHandler::new).build()
   );

   public DataAttachments() {
   }
}
