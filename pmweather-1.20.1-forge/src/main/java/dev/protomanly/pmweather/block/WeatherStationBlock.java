package dev.protomanly.pmweather.block;

import dev.protomanly.pmweather.config.ClientConfig;
import dev.protomanly.pmweather.data.DataAttachments;
import dev.protomanly.pmweather.event.GameBusEvents;
import dev.protomanly.pmweather.networking.ModNetworking;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.RiskEngine;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WindEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

public class WeatherStationBlock extends Block {
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public Map<UUID, Long> lastInteractions = new HashMap<>();
   public Map<BlockPos, List<RiskEngine.Risk>> riskCache = new HashMap<>();
   private int daysOut = 3;

   protected WeatherStationBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)this.defaultBlockState().setValue(FACING, Direction.NORTH));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(new Property[]{FACING});
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockState blockstate = super.getStateForPlacement(context);
      if (blockstate == null) {
         blockstate = this.defaultBlockState();
      }

      return (BlockState)blockstate.setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
      super.playerDestroy(level, player, pos, state, blockEntity, tool);
      this.riskCache.remove(pos);
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (!level.isClientSide) {
         UUID uuid = player.getUUID();
         long lastInteration = this.lastInteractions.getOrDefault(uuid, -10000L);
         long curTime = level.getGameTime();
         if (curTime - lastInteration > 20L) {
            this.lastInteractions.put(uuid, curTime);
            WeatherHandler weatherHandler = GameBusEvents.MANAGERS.get(level.dimension());
            Vec3 wind = WindEngine.getWind(pos, level);
            int windAngle = Math.floorMod((int)Math.toDegrees(Math.atan2(-wind.x, wind.z)), 360);
            double windspeed = wind.length();
            ThermodynamicEngine.AtmosphericDataPoint sfc = ThermodynamicEngine.samplePoint(weatherHandler, pos.getCenter(), level, null, 0);
            List<RiskEngine.Risk> risks = this.riskCache.getOrDefault(pos, new ArrayList<>());
            boolean needsRefresh = risks.size() != this.daysOut;

            for (RiskEngine.Risk risk : risks) {
               if (level.getDayTime() > risk.validUntil() || level.getDayTime() < risk.validUntil() - 24000L) {
                  needsRefresh = true;
                  break;
               }
            }

            if (needsRefresh) {
               risks = new ArrayList<>();

               for (int i = 0; i < this.daysOut; i++) {
                  risks.add(RiskEngine.getRisk(weatherHandler, pos.getCenter(), i));
               }

               this.riskCache.put(pos, risks);
            }

            float temperature = sfc.temperature();
            float dew = sfc.dewpoint();
            CompoundTag data = new CompoundTag();
            data.putString("packetCommand", "Metar");
            data.putString("command", "sendData");
            float moisture = 0.0F;
            ChunkAccess chunkAccess = level.getChunk(pos);
            if (chunkAccess.hasData(DataAttachments.MOISTURE)) {
               moisture = (Float)chunkAccess.getData(DataAttachments.MOISTURE);
            }

            data.putFloat("temp", temperature);
            data.putFloat("dew", dew);
            data.putFloat("moisture", moisture);
            int offset = risks.getFirst().peakRiskTick();

            for (int i = 0; i < this.daysOut && i < risks.size(); i++) {
               float riskx = risks.get(i).risk();
               data.putFloat("day" + (i + 1), riskx);
            }

            data.putFloat("peakOffset", (float)offset);
            data.putFloat("windAngle", (float)windAngle);
            data.putDouble("windspeed", windspeed);
            ModNetworking.serverSendToClientPlayer(data, player);
         }
      }

      return InteractionResult.SUCCESS_NO_ITEM_USED;
   }

   @OnlyIn(Dist.CLIENT)
   public static void sendMessage(CompoundTag data) {
      Level level = Minecraft.getInstance().level;
      if (Minecraft.getInstance().player != null && level != null) {
         String strForFormat = "Wind: %s MPH from %s°\nTemp: %s°F\nDew: %s°F\nHumidity: %s\nSoil Moisture: %s\nToday's Risk: %s\nDay 2 Risk: %s\nDay 3 Risk: %s\nToday's Risk Peak: %s";
         double windspeed = data.getDouble("windspeed");
         float windAngle = data.getFloat("windAngle");
         float temperature = data.getFloat("temp");
         float dew = data.getFloat("dew");
         float moisture = data.getFloat("moisture");
         float humidity = (float)ThermodynamicEngine.getHumidity(temperature, dew);
         if (ClientConfig.metric) {
            strForFormat = "Wind: %s km/h from %s°\nTemp: %s°C\nDew: %s°C\nHumidity: %s\nSoil Moisture: %s\nToday's Risk: %s\nDay 2 Risk: %s\nDay 3 Risk: %s\nToday's Risk Peak: %s";
            windspeed *= 1.609;
         } else {
            temperature = Util.celsiusToFahrenheit(temperature);
            dew = Util.celsiusToFahrenheit(dew);
         }

         float riskV = data.getFloat("day1");
         float risk2 = data.getFloat("day2");
         float risk3 = data.getFloat("day3");
         risk2 *= 0.925F;
         risk3 = Math.min(risk3 * 0.85F, 1.4F);
         float peakRiskOffset = data.getFloat("peakOffset");
         int curTime = (int)((level.getDayTime() + 1000L) % 24000L);
         peakRiskOffset -= (float)curTime;
         humidity *= 100.0F;
         float minutes = peakRiskOffset / 20.0F / 60.0F;
         int val = (int)Math.floor((double)minutes);
         String peakString = String.format(val == 1 ? "In %s minute" : "In %s minutes", val);
         if (minutes < 1.0F) {
            val = (int)Math.floor((double)(minutes * 60.0F));
            peakString = String.format(val == 1 ? "In %s second" : "In %s seconds", val);
         }

         if (minutes <= 0.0F) {
            peakString = "Now";
         }

         if (minutes <= -1.0F) {
            val = (int)Math.floor((double)(-minutes));
            peakString = String.format(val == 1 ? "%s minute ago" : "%s minutes ago", val);
         }

         String str = String.format(
            strForFormat,
            (int)windspeed,
            windAngle,
            (int)temperature,
            (int)dew,
            (int)humidity + "%",
            (int)moisture + "%",
            Util.riskToString(riskV),
            Util.riskToString(risk2),
            Util.riskToString(risk3),
            peakString
         );
         Minecraft.getInstance().player.sendSystemMessage(Component.literal(str));
      }
   }
}
