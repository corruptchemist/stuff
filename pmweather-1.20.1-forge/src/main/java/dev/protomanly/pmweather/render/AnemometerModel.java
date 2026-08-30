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

public class AnemometerModel<T extends Entity> extends HierarchicalModel<T> {
   public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(PMWeather.getPath("anemometer"), "main");
   public final ModelPart tower;
   public final ModelPart shaft;

   public AnemometerModel(ModelPart root) {
      this.tower = root.getChild("tower");
      this.shaft = root.getChild("shaft");
   }

   public ModelPart root() {
      return this.shaft;
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition meshdefinition = new MeshDefinition();
      PartDefinition partdefinition = meshdefinition.getRoot();
      PartDefinition tower = partdefinition.addOrReplaceChild(
         "tower",
         CubeListBuilder.create()
            .texOffs(0, 17)
            .addBox(-1.0F, 7.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
            .texOffs(22, 21)
            .addBox(-1.0F, 0.0F, 0.0F, 2.0F, 7.0F, 0.0F, new CubeDeformation(0.0F))
            .texOffs(18, 17)
            .addBox(0.0F, 0.0F, -1.0F, 0.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)),
         PartPose.offset(0.0F, 11.0F, 0.0F)
      );
      PartDefinition shaft = partdefinition.addOrReplaceChild(
         "shaft",
         CubeListBuilder.create()
            .texOffs(22, 17)
            .addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
            .texOffs(-1, 16)
            .addBox(-8.0F, -1.0F, -0.5F, 16.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
            .texOffs(-1, 0)
            .addBox(-0.5F, -1.0F, -8.0F, 1.0F, 0.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(8, 17)
            .addBox(-2.5F, -2.5F, 5.0F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
         PartPose.offset(0.0F, 11.0F, 0.0F)
      );
      PartDefinition cup_r1 = shaft.addOrReplaceChild(
         "cup_r1",
         CubeListBuilder.create().texOffs(8, 17).addBox(-2.5F, -2.0F, 5.0F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
         PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 1.5708F, 0.0F)
      );
      PartDefinition cup_r2 = shaft.addOrReplaceChild(
         "cup_r2",
         CubeListBuilder.create().texOffs(8, 17).addBox(-2.5F, -2.0F, 5.0F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
         PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, -1.5708F, 0.0F)
      );
      PartDefinition cup_r3 = shaft.addOrReplaceChild(
         "cup_r3",
         CubeListBuilder.create().texOffs(8, 17).addBox(-2.5F, -2.0F, 5.0F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
         PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 3.1416F, 0.0F)
      );
      return LayerDefinition.create(meshdefinition, 32, 32);
   }

   public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
   }

   public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
      this.tower.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
      this.shaft.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
   }
}
