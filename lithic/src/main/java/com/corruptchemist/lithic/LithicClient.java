package com.corruptchemist.lithic;

import com.corruptchemist.lithic.client.KnappingSiteScreen;
import com.corruptchemist.lithic.registry.LithicMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = Lithic.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Lithic.MOD_ID, value = Dist.CLIENT)
public class LithicClient {

    public LithicClient() {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LithicMenus.KNAPPING_SITE.get(), KnappingSiteScreen::new);
    }
}
