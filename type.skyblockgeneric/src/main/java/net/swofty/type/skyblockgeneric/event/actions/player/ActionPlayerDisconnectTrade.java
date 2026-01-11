package net.swofty.type.skyblockgeneric.event.actions.player;

import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEvent;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.skyblockgeneric.trading.TradeManager;
import net.swofty.type.skyblockgeneric.user.SkyBlockPlayer;

public class ActionPlayerDisconnectTrade implements HypixelEventClass {
    @HypixelEvent(node = EventNodes.PLAYER, requireDataLoaded = false)
    public void run(PlayerDisconnectEvent event) {
        SkyBlockPlayer player = (SkyBlockPlayer) event.getPlayer();
        TradeManager.handleDisconnect(player);
    }
}
