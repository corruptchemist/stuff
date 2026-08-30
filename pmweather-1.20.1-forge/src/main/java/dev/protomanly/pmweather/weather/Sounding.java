package dev.protomanly.pmweather.weather;

import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.util.Util;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class Sounding {
   public static final int STORM_FORMATION_ADVANCE_OFFSET = 5400;
   public Map<Integer, ThermodynamicEngine.AtmosphericDataPoint> data = new HashMap<>();
   Vec3 position;
   public WeatherHandler weatherHandler;

   public Sounding(WeatherHandler weatherHandler, Vec3 pos, int res, int height) {
      this(weatherHandler, pos, weatherHandler.getWorld(), res, height);
   }

   public Sounding(WeatherHandler weatherHandler, Vec3 pos, int res, int height, int advance) {
      this(weatherHandler, pos, weatherHandler.getWorld(), res, height, advance);
   }

   public Sounding(WeatherHandler weatherHandler, Vec3 pos, Level level, int res, int height) {
      this.weatherHandler = weatherHandler;
      this.position = pos;
      int base = level.getHeight(Types.MOTION_BLOCKING, (int)pos.x, (int)pos.z);
      int y = base;

      while (y <= height + base) {
         Vec3 p = new Vec3(pos.x, (double)y, pos.z);
         ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(weatherHandler, p, level, null, 0);
         this.data.put(y, dataPoint);
         y += res;
      }
   }

   public Sounding(WeatherHandler weatherHandler, Vec3 pos, Level level, int res, int height, RadarBlockEntity radarBlockEntity) {
      this.weatherHandler = weatherHandler;
      this.position = pos;
      int base = radarBlockEntity.getBlockPos().getY();
      int y = base;

      while (y <= height + base) {
         Vec3 p = new Vec3(pos.x, (double)y, pos.z);
         ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(
            weatherHandler, p, level, radarBlockEntity, 0, radarBlockEntity.getBlockPos().getY()
         );
         this.data.put(y, dataPoint);
         y += res;
      }
   }

   public Sounding(WeatherHandler weatherHandler, Vec3 pos, Level level, int res, int height, int advance) {
      this.weatherHandler = weatherHandler;
      this.position = pos;
      int base = level.getHeight(Types.MOTION_BLOCKING, (int)pos.x, (int)pos.z);
      int y = base;

      while (y <= height + base) {
         Vec3 p = new Vec3(pos.x, (double)y, pos.z);
         ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(weatherHandler, p, level, null, advance);
         this.data.put(y, dataPoint);
         y += res;
      }
   }

   public Sounding(WeatherHandler weatherHandler, Vec3 pos, Level level, int res, int height, RadarBlockEntity radarBlockEntity, int advance) {
      this.weatherHandler = weatherHandler;
      this.position = pos;
      int base = radarBlockEntity.getBlockPos().getY();
      int y = base;

      while (y <= height + base) {
         Vec3 p = new Vec3(pos.x, (double)y, pos.z);
         ThermodynamicEngine.AtmosphericDataPoint dataPoint = ThermodynamicEngine.samplePoint(
            weatherHandler, p, level, radarBlockEntity, 0, radarBlockEntity.getBlockPos().getY()
         );
         this.data.put(y, dataPoint);
         y += res;
      }
   }

   public Sounding(WeatherHandler weatherHandler, Vec3 pos) {
      this.weatherHandler = weatherHandler;
      this.position = pos;
   }

   public Sounding(WeatherHandler weatherHandler, CompoundTag compoundTag, Vec3 pos) {
      this.weatherHandler = weatherHandler;
      this.position = pos;

      for (String key : compoundTag.getAllKeys()) {
         int height = Integer.parseInt(key);
         CompoundTag layer = compoundTag.getCompound(key);
         this.data.put(height, ThermodynamicEngine.deserializeDataPoint(layer));
      }
   }

   public Sounding.CAPE getCAPE(Sounding.Parcel parcel) {
      float CAPE = 0.0F;
      float CINH = 0.0F;
      float CAPE3 = 0.0F;
      List<Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint>> set = this.data.entrySet().stream().sorted(Entry.comparingByKey()).toList();
      float delta = 0.0F;
      if (set.size() > 1) {
         delta = (float)(set.get(1).getKey() - set.getFirst().getKey());
      }

      for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry : set) {
         int h = entry.getKey();
         ThermodynamicEngine.AtmosphericDataPoint dataPoint = entry.getValue();
         Float parcelTemp = parcel.profile.getOrDefault(dataPoint.pressure(), null);
         if (parcelTemp != null && set.size() > 1) {
            if (h <= 3000 && dataPoint.pressure() <= parcel.lfcP) {
               float lCAPE = (Util.celsiusToKelvin(parcelTemp) - Util.celsiusToKelvin(dataPoint.virtualTemperature()))
                  / Util.celsiusToKelvin(dataPoint.virtualTemperature())
                  * 9.807F;
               lCAPE *= delta;
               CAPE3 += lCAPE;
            }

            if (dataPoint.pressure() >= parcel.elP && dataPoint.pressure() <= parcel.lfcP) {
               float lCAPE = (Util.celsiusToKelvin(parcelTemp) - Util.celsiusToKelvin(dataPoint.virtualTemperature()))
                  / Util.celsiusToKelvin(dataPoint.virtualTemperature())
                  * 9.807F;
               lCAPE *= delta;
               CAPE += Math.max(lCAPE, 0.0F);
            }

            if (dataPoint.pressure() > parcel.lfcP) {
               float lCINH = (Util.celsiusToKelvin(parcelTemp) - Util.celsiusToKelvin(dataPoint.virtualTemperature()))
                  / Util.celsiusToKelvin(dataPoint.virtualTemperature())
                  * 9.807F;
               lCINH *= delta;
               lCINH *= 0.75F;
               CINH += Math.min(lCINH, 0.0F);
            }
         }
      }

      return new Sounding.CAPE(CAPE, CINH, CAPE3);
   }

   @Nullable
   public ThermodynamicEngine.AtmosphericDataPoint getFromPressure(float p) {
      for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry : this.data.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
         if (entry.getValue().pressure() < p) {
            return entry.getValue();
         }
      }

      return null;
   }

   public float getRisk(int advance) {
      ThermodynamicEngine.AtmosphericDataPoint sfc = this.getFromHeight(0);
      Sounding.CAPE CAPE = this.getCAPE(this.getSBParcel());
      float risk = 1.0F;
      risk *= Mth.clamp(CAPE.CAPE() / 2000.0F, 0.0F, 2.0F);
      risk *= Mth.clamp((CAPE.CAPE3() - 25.0F) / 50.0F, 0.0F, 1.25F);
      Float lr = this.getLapseRate(0, 3000);
      if (lr == null) {
         lr = 0.0F;
      }

      risk *= Mth.clamp((lr - 5.0F) / 1.5F, 0.75F, 1.25F);
      risk *= 1.0F - Mth.clamp(CAPE.CINH() / -500.0F, 0.0F, 1.0F);
      if (sfc != null) {
         risk *= Mth.clamp((sfc.dewpoint() - 7.0F) / 11.0F, 0.15F, 1.25F);
      }

      risk = Mth.clamp(risk, 0.0F, 1.75F);
      if (this.position != null && ThermodynamicEngine.noise != null) {
         float SRH = (
               (float)ThermodynamicEngine.noise
                     .getValue(
                        this.position.x / 5000.0,
                        (double)((float)(this.weatherHandler.getWorld().getDayTime() + (long)advance) / 15000.0F),
                        this.position.z / 5000.0
                     )
                  + 1.0F
            )
            / 2.0F;
         SRH = (float)Math.pow((double)SRH, 1.5);
         SRH *= 400.0F;
         risk *= Mth.clamp(SRH / 325.0F, 0.0F, 1.25F);
         risk = Mth.clamp(risk, 0.0F, 1.75F);
      }

      return (float)Math.pow((double)Mth.clamp(risk / 1.75F, 0.0F, 1.0F), ServerConfig.riskCurve + 0.1F) * 1.75F;
   }

   @Nullable
   public Float getLapseRate(int lower, int upper) {
      ThermodynamicEngine.AtmosphericDataPoint dataPointLower = this.getFromHeight(lower);
      ThermodynamicEngine.AtmosphericDataPoint dataPointUpper = this.getFromHeight(upper);
      if (dataPointLower != null && dataPointUpper != null) {
         float delta = (float)(upper - lower);
         return (dataPointLower.temperature() - dataPointUpper.temperature()) / (delta / 1000.0F);
      } else {
         return null;
      }
   }

   @Nullable
   public ThermodynamicEngine.AtmosphericDataPoint getFromHeight(int h) {
      ThermodynamicEngine.AtmosphericDataPoint dataPoint = this.data.getOrDefault(h, null);
      if (dataPoint != null) {
         return dataPoint;
      } else {
         for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry : this.data.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
            if (entry.getKey() >= h) {
               return entry.getValue();
            }
         }

         return null;
      }
   }

   @Nullable
   public Sounding.Parcel getSBParcel() {
      List<Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint>> set = this.data.entrySet().stream().sorted(Entry.comparingByKey()).toList();
      Iterator var2 = set.iterator();
      if (var2.hasNext()) {
         Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry = (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint>)var2.next();
         return new Sounding.Parcel(this, entry.getValue());
      } else {
         return null;
      }
   }

   public CompoundTag serializeNBT() {
      CompoundTag compoundTag = new CompoundTag();

      for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry : this.data.entrySet().stream().sorted(Entry.comparingByKey()).toList().reversed()) {
         compoundTag.put(String.valueOf(entry.getKey()), entry.getValue().serializeNBT());
      }

      return compoundTag;
   }

   @Override
   public String toString() {
      StringBuilder str = new StringBuilder();

      for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry : this.data.entrySet().stream().sorted(Entry.comparingByKey()).toList().reversed()) {
         str.append(String.format("%s m: %s", entry.getKey(), entry.getValue())).append("\n");
      }

      return str.toString();
   }

   public static Vec2 getPosition(float temp, float pressure, float minPressure, float maxPressure, float tempRange) {
      temp -= tempRange / 4.0F;
      float tPerc = temp / tempRange;
      float y = (float)(
         1.0 - (Math.log10((double)pressure) - Math.log10((double)minPressure)) / (Math.log10((double)maxPressure) - Math.log10((double)minPressure))
      );
      float heightPerc = -1.0F + y * 2.0F;
      tPerc += (heightPerc + 1.0F) / 1.5F;
      return new Vec2(tPerc, heightPerc);
   }

   public static record CAPE(float CAPE, float CINH, float CAPE3) {
   }

   public static class Parcel {
      public Sounding sounding;
      public ThermodynamicEngine.AtmosphericDataPoint parcel;
      public Map<Float, Float> profile = new HashMap<>();
      public float lclP;
      public float lfcP = -1.0F;
      public float elP = -1.0F;

      public Parcel(Sounding sounding, ThermodynamicEngine.AtmosphericDataPoint parcel) {
         this.sounding = sounding;
         this.parcel = parcel;
         Sounding.Parcel.LCL lcl = DryLift(parcel.pressure(), parcel.temperature(), parcel.dewpoint());
         this.lclP = lcl.pressure();
         List<Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint>> set = sounding.data.entrySet().stream().toList();
         Map<Integer, ThermodynamicEngine.AtmosphericDataPoint> lower = new HashMap<>();
         Map<Integer, ThermodynamicEngine.AtmosphericDataPoint> upper = new HashMap<>();
         float lowMinP = 10000.0F;

         for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry : set) {
            int height = entry.getKey();
            ThermodynamicEngine.AtmosphericDataPoint dataPoint = entry.getValue();
            if (!(dataPoint.pressure() > parcel.pressure())) {
               if (dataPoint.pressure() >= lcl.pressure()) {
                  lower.put(height, dataPoint);
                  lowMinP = Math.min(dataPoint.pressure(), lowMinP);
               } else {
                  upper.put(height, dataPoint);
               }
            }
         }

         Map<Float, Float> tLower = this.GetDryLapse(lower, parcel.virtualTemperature(), parcel.dewpoint(), parcel.pressure());
         Map<Float, Float> tUpper = this.GetMoistLapse(upper, tLower.getOrDefault(lowMinP, lcl.temp()), lowMinP);
         this.profile.putAll(tLower);
         this.profile.putAll(tUpper);
         List<Entry<Float, Float>> profileSetAsc = this.profile.entrySet().stream().sorted(Entry.comparingByKey()).toList();
         List<Entry<Float, Float>> profileSetDesc = profileSetAsc.reversed();
         List<Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint>> soundingSetAsc = sounding.data
            .entrySet()
            .stream()
            .sorted(Entry.comparingByKey())
            .toList();
         List<Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint>> soundingSetDesc = soundingSetAsc.reversed();

         for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entryx : soundingSetAsc) {
            ThermodynamicEngine.AtmosphericDataPoint dataPoint = entryx.getValue();
            float p = dataPoint.pressure();
            float t = this.profile.getOrDefault(p, -100.0F);
            if (!(p > this.lclP) && t >= dataPoint.virtualTemperature()) {
               this.lfcP = p;
               break;
            }
         }

         for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entryxx : soundingSetDesc) {
            ThermodynamicEngine.AtmosphericDataPoint dataPoint = entryxx.getValue();
            float p = dataPoint.pressure();
            float t = this.profile.getOrDefault(p, -100.0F);
            if (p >= this.lclP || this.lfcP > 0.0F && p > this.lfcP) {
               break;
            }

            if (t >= dataPoint.virtualTemperature()) {
               this.elP = p;
               break;
            }
         }
      }

      public Map<Float, Float> GetDryLapse(Map<Integer, ThermodynamicEngine.AtmosphericDataPoint> l, float t, float dp, float p) {
         Sounding.Parcel.LCL lcl = DryLift(p, t, dp);
         Map<Float, Float> r = new HashMap<>();

         for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry : l.entrySet().stream().toList()) {
            ThermodynamicEngine.AtmosphericDataPoint dataPoint = entry.getValue();
            r.put(dataPoint.pressure(), Mth.lerp(1.0F - (dataPoint.pressure() - lcl.pressure()) / (p - lcl.pressure()), t, lcl.temp()));
         }

         return r;
      }

      public Map<Float, Float> GetMoistLapse(Map<Integer, ThermodynamicEngine.AtmosphericDataPoint> l, float t, float p) {
         Map<Float, Float> r = new HashMap<>();

         for (Entry<Integer, ThermodynamicEngine.AtmosphericDataPoint> entry : l.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
            ThermodynamicEngine.AtmosphericDataPoint dataPoint = entry.getValue();
            r.put(dataPoint.pressure(), WetLift(p, t, dataPoint.pressure()));
         }

         return r;
      }

      public static float LCLTemp(float t, float dp) {
         float s = t - dp;
         float dlt = s * (1.2185F + 0.001278F * t + s * (-0.00219F + 1.173E-5F * s - 5.2E-6F * t));
         return t - dlt * 0.6F;
      }

      public static float Theta(float p, float t, @Nullable Float p2) {
         if (p2 == null) {
            p2 = 1000.0F;
         }

         return Util.kelvinToCelsius(Util.celsiusToKelvin(t) * (float)Math.pow((double)(p2 / p), (double)Util.ROCP));
      }

      public static float ThaLVL(float theta, float t) {
         return 1000.0F / (float)Math.pow((double)(Util.celsiusToKelvin(theta) / Util.celsiusToKelvin(t)), (double)(1.0F / Util.ROCP));
      }

      public static float SatLift(float p, float thetam, @Nullable Float conv) {
         if (conv == null) {
            conv = 100.0F;
         }

         if (Math.abs(p - 1000.0F) - 0.001F <= 0.0F) {
            return thetam;
         } else {
            float eor = 999.0F;
            float pwrp = 0.0F;
            float t1 = 0.0F;
            float t2 = 0.0F;
            float e1 = 0.0F;
            float e2 = 0.0F;

            while (Math.abs(eor) - conv > 0.0F) {
               float rate;
               if (eor == 999.0F) {
                  pwrp = (float)Math.pow((double)(p / 1000.0F), (double)Util.ROCP);
                  t1 = Util.kelvinToCelsius(Util.celsiusToKelvin(thetam) * pwrp);
                  e1 = Wobf(t1) - Wobf(thetam);
                  rate = 1.0F;
               } else {
                  rate = (t2 - t1) / (e2 - e1);
                  t1 = t2;
                  e1 = e2;
               }

               t2 = t1 - e1 * rate;
               float var10 = Util.kelvinToCelsius(Util.celsiusToKelvin(t2) / pwrp);
               e2 = var10 + (Wobf(t2) - Wobf(var10) - thetam);
               eor = e2 * rate;
            }

            return t2 - eor;
         }
      }

      public static float Wobf(float t) {
         t -= 20.0F;
         if (t <= 0.0F) {
            float npol = 1.0F + t * (-0.008841661F + t * (1.4714143E-4F + t * (-9.671988E-7F + t * (-3.260722E-8F + t * -3.8598072E-10F))));
            return 15.13F / (float)Math.pow((double)npol, 4.0);
         } else {
            float ppol = t * (4.961892E-7F + t * (-6.1059366E-9F + t * (3.940155E-11F + t * (-1.258813E-13F + t * 1.668828E-16F))));
            ppol = 1.0F + t * (0.0036182988F + t * (-1.3603273E-5F + ppol));
            return 29.93F / (float)Math.pow((double)ppol, 4.0) + 0.96F * t - 14.8F;
         }
      }

      public static float WetLift(float p, float t, float p2) {
         float thta = Theta(p, t, null);
         float thetam = thta - Wobf(thta) + Wobf(t);
         return SatLift(p2, thetam, null);
      }

      public static Sounding.Parcel.LCL DryLift(float p, float t, float dp) {
         float t2 = LCLTemp(t, dp);
         float p2 = ThaLVL(Theta(p, t, null), t2);
         return new Sounding.Parcel.LCL(p2, t2);
      }

      public static record LCL(float pressure, float temp) {
      }
   }
}
