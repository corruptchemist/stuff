package dev.protomanly.pmweather.compat.sable;

import com.simibubi.create.AllTags.AllBlockTags;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.util.Util;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider.LiftProviderContext;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class SableHandler {
   public SableHandler() {
   }

   public void initialize() {
   }

   public Vec3 getPos(Level level, BlockPos pos) {
      return Sable.HELPER.projectOutOfSubLevel(level, pos.getCenter());
   }

   public double getDistance(Level level, Vec3 pos, Vec3 to) {
      return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(level, pos, to));
   }

   public Vector3d getRelative(Vector3d a, Vector3d b) {
      return new Vector3d(a).sub(b);
   }

   public Util.RelativeMotion getRelativeWind(BlockEntity blockEntity, Vec3 wind, Vec3 pos) {
      SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);
      Vector3d jomlWind = JOMLConversion.toJOML(wind);
      Util.RelativeMotion fallback = new Util.RelativeMotion(jomlWind, new Vector3d(0.0), jomlWind);
      if (subLevel == null) {
         return fallback;
      } else if (subLevel.isRemoved()) {
         return fallback;
      } else if (subLevel instanceof ServerSubLevel serverSubLevel) {
         RigidBodyHandle rigidBodyHandle = RigidBodyHandle.of(serverSubLevel);
         Vector3d velocity = rigidBodyHandle.getLinearVelocity(new Vector3d());
         return new Util.RelativeMotion(jomlWind, velocity, this.getRelative(jomlWind, velocity));
      } else {
         return fallback;
      }
   }

   public void tick(ServerLevel level) {
      double universalDrag = DimensionPhysicsData.getUniversalDrag(level);
      ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
      if (container != null) {
         List<ServerSubLevel> subLevels = container.getAllSubLevels();
         Iterator var6 = subLevels.iterator();

         while (true) {
            ServerSubLevel subLevel;
            ObjectCollection<LiftProviderContext> liftProviders;
            Vector3d wind;
            double m;
            while (true) {
               if (!var6.hasNext()) {
                  return;
               }

               subLevel = (ServerSubLevel)var6.next();
               if (!subLevel.isRemoved()) {
                  liftProviders = subLevel.getPlot().getLiftProviders();
                  Vec3 pos = JOMLConversion.toMojang(subLevel.logicalPose().position());
                  wind = JOMLConversion.toJOML(WindEngine.getWind(pos, level, false, false, false, true));
                  double windspeed = wind.mul(1.0, 0.0, 1.0).length();
                  m = 1.0;
                  if (!liftProviders.isEmpty()) {
                     break;
                  }

                  if (windspeed > 100.0) {
                     m = Math.clamp((windspeed - 100.0) / 50.0, 0.0, 1.0);
                     break;
                  }
               }
            }

            Quaterniond subLevelRotation = subLevel.logicalPose().orientation();
            Vector3d force = new Vector3d(wind).mul(2.0);
            ObjectIterator mass = liftProviders.iterator();

            while (mass.hasNext()) {
               LiftProviderContext liftProvider = (LiftProviderContext)mass.next();
               Vector3d liftDir = JOMLConversion.toJOML(liftProvider.dir());
               liftDir.rotate(subLevelRotation);
               double alignment = wind.dot(liftDir);
               Vector3d affectDir = new Vector3d(liftDir).mul(Math.signum(alignment));
               double affectAmount = 0.05;
               BlockState state = liftProvider.state();
               if (state.is(AllBlockTags.WINDMILL_SAILS.tag)) {
                  affectAmount = 1.0;
               }

               force.add(affectDir.mul(Math.abs(alignment) * affectAmount));
            }

            double massx = subLevel.getMassTracker().getMass() * 8.0;
            double liftDampen = 75.0 / (75.0 + massx);
            force.mul(ServerConfig.sableWindMult * 0.75 * m);
            force.mul(0.44704 * liftDampen);
            RigidBodyHandle rigidBodyHandle = RigidBodyHandle.of(subLevel);
            double inertia = 100.0 / (100.0 + massx);
            Vector3d accel = new Vector3d(force);
            accel.mul(inertia);
            accel.mul(0.35);
            accel.mul(ServerConfig.sableInertiaMod);
            accel.mul(0.05);
            accel.mul(Math.pow(1.0 - universalDrag, 10.0));
            if (!subLevel.isRemoved()) {
               rigidBodyHandle.addLinearAndAngularVelocity(accel, new Vector3d(0.0));
            }
         }
      }
   }
}
