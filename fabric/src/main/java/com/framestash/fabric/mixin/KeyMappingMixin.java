package com.framestash.fabric.mixin;

import com.framestash.client.FrameStashClient;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.6 - 1.21.8 lets only one key mapping own a key, and vanilla's "Quick Actions" also
 * defaults to G. After vanilla rebuilds its key table, Frame Stash's own mappings take their keys
 * back, so the default G stashes (1.21.9+ fires both mappings anyway).
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Shadow @Final private static Map<InputConstants.Key, KeyMapping> MAP;
    @Shadow private InputConstants.Key key;

    @Inject(method = "resetMapping", at = @At("TAIL"))
    private static void frameStash$claimKeys(CallbackInfo ci) {
        for (KeyMapping mapping : FrameStashClient.keys()) {
            InputConstants.Key bound = ((KeyMappingMixin) (Object) mapping).key;
            if (bound != null && bound != InputConstants.UNKNOWN) {
                MAP.put(bound, mapping);
            }
        }
    }
}
