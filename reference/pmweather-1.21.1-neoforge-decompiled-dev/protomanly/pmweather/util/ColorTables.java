package dev.protomanly.pmweather.util;

import java.awt.Color;
import net.minecraft.util.Mth;

public class ColorTables {
   public ColorTables() {
   }

   public static Color getReflectivity(float val, Color startColor) {
      Color color = lerp(Math.clamp(val / 19.0F, 0.0F, 1.0F), startColor, new Color(6069678));
      color = lerp(Math.clamp((val - 19.0F) / 8.0F, 0.0F, 1.0F), color, new Color(746505));
      color = lerp(Math.clamp((val - 27.0F) / 13.0F, 0.0F, 1.0F), color, new Color(12956416));
      if (val >= 40.0F) {
         color = new Color(16421888);
      }

      color = lerp(Math.clamp((val - 40.0F) / 10.0F, 0.0F, 1.0F), color, new Color(11688204));
      if (val >= 50.0F) {
         color = new Color(16327435);
      }

      color = lerp(Math.clamp((val - 50.0F) / 10.0F, 0.0F, 1.0F), color, new Color(8529952));
      if (val >= 60.0F) {
         color = new Color(13277620);
      }

      color = lerp(Math.clamp((val - 60.0F) / 10.0F, 0.0F, 1.0F), color, new Color(12721266));
      if (val >= 70.0F) {
         color = new Color(16777215);
      }

      return color;
   }

   public static Color getMixedReflectivity(float val) {
      Color color = new Color(250, 195, 248, 0);
      return lerp(Math.clamp(val / 70.0F, 0.0F, 1.0F), color, new Color(210, 0, 210, 255));
   }

   public static Color getSnowReflectivity(float val) {
      Color color = new Color(255, 255, 255, 0);
      return lerp(Math.clamp(val / 70.0F, 0.0F, 1.0F), color, new Color(0, 111, 255, 255));
   }

   public static Color getVelocity(float velocity) {
      Color color = new Color(150, 150, 150);
      if (velocity > 0.0F) {
         color = new Color(9074294);
         color = lerp(Math.clamp(velocity / 12.0F, 0.0F, 1.0F), color, new Color(8665153));
         if (velocity > 12.0F) {
            color = new Color(7208960);
         }

         color = lerp(Math.clamp((velocity - 12.0F) / 27.0F, 0.0F, 1.0F), color, new Color(15925255));
         if (velocity > 39.0F) {
            color = new Color(16398161);
         }

         color = lerp(Math.clamp((velocity - 39.0F) / 30.0F, 0.0F, 1.0F), color, new Color(16771235));
         color = lerp(Math.clamp((velocity - 69.0F) / 71.0F, 0.0F, 1.0F), color, new Color(6751746));
      } else if (velocity < 0.0F) {
         velocity = Mth.abs(velocity);
         color = new Color(7505264);
         color = lerp(Math.clamp(velocity / 12.0F, 0.0F, 1.0F), color, new Color(5142860));
         if (velocity > 12.0F) {
            color = new Color(353795);
         }

         color = lerp(Math.clamp((velocity - 12.0F) / 69.0F, 0.0F, 1.0F), color, new Color(3203299));
         color = lerp(Math.clamp((velocity - 81.0F) / 25.0F, 0.0F, 1.0F), color, new Color(1442457));
         color = lerp(Math.clamp((velocity - 106.0F) / 34.0F, 0.0F, 1.0F), color, new Color(16711812));
      }

      return color;
   }

   public static Color getWindspeed(float val) {
      Color color = new Color(0, 0, 0);
      color = lerp(val / 65.0F, color, new Color(106, 128, 241));
      color = lerp((val - 65.0F) / 20.0F, color, new Color(117, 243, 224));
      color = lerp((val - 85.0F) / 25.0F, color, new Color(116, 241, 81));
      color = lerp((val - 110.0F) / 25.0F, color, new Color(246, 220, 53));
      color = lerp((val - 135.0F) / 30.0F, color, new Color(246, 127, 53));
      color = lerp((val - 165.0F) / 35.0F, color, new Color(246, 53, 53));
      color = lerp((val - 200.0F) / 50.0F, color, new Color(240, 53, 246));
      return lerp((val - 250.0F) / 50.0F, color, new Color(255, 255, 255));
   }

   public static Color getSST(float val) {
      Color color = new Color(253, 253, 253);
      color = lerp(val / 10.0F, color, new Color(102, 77, 166));
      if (val >= 10.0F) {
         color = new Color(108, 157, 233);
         color = lerp((val - 10.0F) / 10.0F, color, new Color(109, 253, 253));
      }

      if (val >= 20.0F) {
         color = new Color(109, 253, 253);
         color = lerp((val - 20.0F) / 6.0F, color, new Color(11, 163, 50));
      }

      if (val >= 26.0F) {
         color = new Color(253, 253, 0);
         color = lerp((val - 26.0F) / 4.0F, color, new Color(167, 0, 0));
      }

      if (val >= 30.0F) {
         color = new Color(167, 0, 67);
         color = lerp((val - 30.0F) / 3.0F, color, new Color(217, 0, 253));
      }

      return color;
   }

   public static Color getHurricaneWindspeed(float val) {
      Color color = new Color(253, 253, 253);
      color = lerp(val / 39.0F, color, new Color(40, 116, 222));
      if (val >= 39.0F) {
         color = new Color(155, 191, 130);
         color = lerp((val - 39.0F) / 19.0F, color, new Color(118, 163, 87));
      }

      if (val >= 58.0F) {
         color = new Color(69, 114, 42);
         color = lerp((val - 58.0F) / 16.0F, color, new Color(129, 166, 44));
      }

      if (val >= 74.0F) {
         color = new Color(235, 191, 70);
         color = lerp((val - 74.0F) / 22.0F, color, new Color(186, 143, 35));
      }

      if (val >= 96.0F) {
         color = new Color(220, 146, 64);
         color = lerp((val - 96.0F) / 15.0F, color, new Color(171, 97, 22));
      }

      if (val >= 111.0F) {
         color = new Color(190, 38, 0);
         color = lerp((val - 111.0F) / 19.0F, color, new Color(143, 26, 0));
      }

      if (val >= 130.0F) {
         color = new Color(156, 83, 118);
         color = lerp((val - 130.0F) / 27.0F, color, new Color(108, 36, 68));
      }

      if (val >= 157.0F) {
         color = new Color(96, 83, 162);
         color = lerp((val - 157.0F) / 18.0F, color, new Color(47, 36, 113));
      }

      if (val >= 175.0F) {
         color = new Color(28, 23, 74);
         color = lerp((val - 175.0F) / 25.0F, color, new Color(101, 101, 101));
      }

      if (val >= 200.0F) {
         color = new Color(255, 255, 255);
         color = lerp((val - 200.0F) / 100.0F, color, new Color(0, 0, 0));
      }

      return color;
   }

   public static Color lerp(float delta, Color c1, Color c2) {
      delta = Mth.clamp(delta, 0.0F, 1.0F);
      return new Color(
         (int)Mth.lerp(delta, (float)c1.getRed(), (float)c2.getRed()),
         (int)Mth.lerp(delta, (float)c1.getGreen(), (float)c2.getGreen()),
         (int)Mth.lerp(delta, (float)c1.getBlue(), (float)c2.getBlue())
      );
   }
}
