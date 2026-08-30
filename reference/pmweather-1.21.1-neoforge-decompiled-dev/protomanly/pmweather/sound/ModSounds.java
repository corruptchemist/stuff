package dev.protomanly.pmweather.sound;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, "pmweather");
   public static final Holder<SoundEvent> SIREN = SOUND_EVENTS.register("siren", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> HAIL = SOUND_EVENTS.register("hail", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> CALM_AMBIENCE = SOUND_EVENTS.register("calm_ambience", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> WIND_STRONG = SOUND_EVENTS.register("wind_strong", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> WIND_MED = SOUND_EVENTS.register("wind_med", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> WIND_CALM = SOUND_EVENTS.register("wind_calm", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> TORNADIC_WIND = SOUND_EVENTS.register("tornadic_wind", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> TORNADIC_DAMAGE = SOUND_EVENTS.register("tornadic_damage", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> SUPERCELL_WIND = SOUND_EVENTS.register("supercell_wind", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> EYEWALL_WIND = SOUND_EVENTS.register("eyewall_wind", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> UNDERGROUND_WIND = SOUND_EVENTS.register("underground_wind", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> WSR88D_COMPLETED = SOUND_EVENTS.register("wsr88d_completed", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> WSR88D_DISMANTLED = SOUND_EVENTS.register("wsr88d_dismantled", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> RAIN = SOUND_EVENTS.register("rain", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> SLEET = SOUND_EVENTS.register("sleet", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> POSITIVE_NEXT = SOUND_EVENTS.register("positive_next", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> POSITIVE_NEAR = SOUND_EVENTS.register("positive_near", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> POSITIVE_FAR = SOUND_EVENTS.register("positive_far", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> NEGATIVE_NEXT = SOUND_EVENTS.register("negative_next", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> NEGATIVE_NEAR = SOUND_EVENTS.register("negative_near", SoundEvent::createVariableRangeEvent);
   public static final Holder<SoundEvent> NEGATIVE_FAR = SOUND_EVENTS.register("negative_far", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> METAL_HUM = SOUND_EVENTS.register("metal_hum", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> POWERFLASH = SOUND_EVENTS.register("powerflash", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> SLEET_BREAK = SOUND_EVENTS.register("sleet_break", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> SLEET_STEP = SOUND_EVENTS.register("sleet_step", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> SLEET_PLACE = SOUND_EVENTS.register("sleet_place", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> SLEET_HIT = SOUND_EVENTS.register("sleet_hit", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> SLEET_FALL = SOUND_EVENTS.register("sleet_fall", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> FIRE_CRACKLE = SOUND_EVENTS.register("fire_crackle", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> FIRE_LOW = SOUND_EVENTS.register("fire_ambient_low", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> FIRE_MID = SOUND_EVENTS.register("fire_ambient_mid", SoundEvent::createVariableRangeEvent);
   public static final Supplier<SoundEvent> FIRE_HIGH = SOUND_EVENTS.register("fire_ambient_high", SoundEvent::createVariableRangeEvent);
   public static final DeferredSoundType SLEET_BLOCK = new DeferredSoundType(1.0F, 1.0F, SLEET_BREAK, SLEET_STEP, SLEET_PLACE, SLEET_HIT, SLEET_FALL);

   public ModSounds() {
   }

   @OnlyIn(Dist.CLIENT)
   public static void playBlockSound(Level level, BlockState block, BlockPos blockPos, SoundEvent soundEvent, float volume, float pitch, float cutOffRange) {
      MovingSoundStreamingSource sound = new MovingSoundStreamingSource(level, block, blockPos, soundEvent, SoundSource.WEATHER, volume, pitch, cutOffRange);
      Minecraft.getInstance().getSoundManager().play(sound);
   }

   @OnlyIn(Dist.CLIENT)
   public static void playPlayerLockedSound(Vec3 pos, SoundEvent soundEvent, float volume, float pitch) {
      MovingSoundStreamingSource sound = new MovingSoundStreamingSource(pos, soundEvent, SoundSource.WEATHER, volume, pitch, true);
      Minecraft.getInstance().getSoundManager().play(sound);
   }
}
