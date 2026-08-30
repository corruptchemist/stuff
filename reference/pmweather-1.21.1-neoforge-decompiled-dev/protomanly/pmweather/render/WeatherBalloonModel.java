package dev.protomanly.pmweather.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.protomanly.pmweather.PMWeather;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Entity;

public class WeatherBalloonModel<T extends Entity> extends HierarchicalModel<T> {
   public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(PMWeather.getPath("weather_balloon"), "main");
   public final ModelPart root;

   public WeatherBalloonModel(ModelPart root) {
      this.root = root.getChild("root");
   }

   public ModelPart root() {
      return this.root;
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition meshdefinition = new MeshDefinition();
      PartDefinition partdefinition = meshdefinition.getRoot();
      PartDefinition root = partdefinition.addOrReplaceChild(
         "root",
         CubeListBuilder.create()
            .texOffs(64, 64)
            .addBox(-8.0F, 37.0F, -8.0F, 16.0F, 9.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(128, 39)
            .addBox(-1.5F, 14.0F, -1.5F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(0, 64)
            .addBox(-8.0F, 7.0F, -8.0F, 16.0F, 30.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(0, 110)
            .addBox(-8.0F, 0.0F, -8.0F, 16.0F, 7.0F, 16.0F, new CubeDeformation(0.2F))
            .texOffs(64, 112)
            .addBox(-8.0F, 37.0F, -8.0F, 16.0F, 4.0F, 16.0F, new CubeDeformation(0.2F))
            .texOffs(0, 0)
            .addBox(-16.0F, -32.0F, -16.0F, 32.0F, 32.0F, 32.0F, new CubeDeformation(0.0F))
            .texOffs(64, 89)
            .addBox(-8.0F, 0.0F, -8.0F, 16.0F, 7.0F, 16.0F, new CubeDeformation(0.0F)),
         PartPose.offset(0.0F, 24.0F, 0.0F)
      );
      PartDefinition knot_r1 = root.addOrReplaceChild(
         "knot_r1",
         CubeListBuilder.create()
            .texOffs(128, 71)
            .addBox(0.0F, -2.5F, -3.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
            .texOffs(128, 0)
            .addBox(0.0F, -2.5F, -11.5F, 0.0F, 8.0F, 23.0F, new CubeDeformation(0.0F))
            .texOffs(128, 31)
            .addBox(-11.5F, -2.5F, 0.0F, 23.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
         PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, 0.7854F, 0.0F)
      );
      PartDefinition knot_r2 = root.addOrReplaceChild(
         "knot_r2",
         CubeListBuilder.create().texOffs(128, 62).addBox(0.0F, -2.5F, -3.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)),
         PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, -0.7854F, 0.0F)
      );
      return LayerDefinition.create(meshdefinition, 176, 176);
   }

   public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
   }

   public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
      this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
   }
}
