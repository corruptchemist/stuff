package com.corruptchemist.lithic.registry;

import com.corruptchemist.lithic.Lithic;
import com.corruptchemist.lithic.knowledge.Knowledge;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class LithicAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Lithic.MOD_ID);

    /**
     * Knowledge survives death on purpose. Losing a research tree to a creeper is
     * tedium, not difficulty.
     */
    public static final Supplier<AttachmentType<Knowledge>> KNOWLEDGE = ATTACHMENTS.register(
            "knowledge",
            () -> AttachmentType.builder(Knowledge::new)
                    .serialize(Knowledge.CODEC)
                    .copyOnDeath()
                    .build());
    // Deliberately NOT .sync(...): that overload only exists in newer 21.1.x
    // builds, and calling it makes the mod crash on older ones with a
    // NoSuchMethodError. Nothing needs it either -- every bit of UI in the mod
    // is server-generated chat, so the client never reads this attachment.
    // Knowledge.STREAM_CODEC is kept ready for whenever a research GUI wants it.

    private LithicAttachments() {}
}
