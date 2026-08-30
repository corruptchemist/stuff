package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.menu.KnappingSiteMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LithicMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Lithic.MOD_ID);

    public static final Supplier<MenuType<KnappingSiteMenu>> KNAPPING_SITE =
            MENUS.register("knapping_site", () -> IMenuTypeExtension.create(KnappingSiteMenu::forClient));

    private LithicMenus() {}
}
