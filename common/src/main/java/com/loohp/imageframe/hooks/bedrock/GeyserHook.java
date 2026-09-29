package com.loohp.imageframe.hooks.bedrock;

import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;

public class GeyserHook {

    public static boolean isBedrockPlayer(Player player) {
        return GeyserApi.api().isBedrockPlayer(player.getUniqueId());
    }

}
