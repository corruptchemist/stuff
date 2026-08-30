package dev.protomanly.pmweather.entity;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, "pmweather");
   public static final Supplier<EntityType<MovingBlock>> MOVING_BLOCK = ENTITY_TYPES.register(
      "moving_block", () -> Builder.of(MovingBlock::new, MobCategory.MISC).sized(1.0F, 1.0F).build("moving_block")
   );

   public ModEntities() {
   }
}
