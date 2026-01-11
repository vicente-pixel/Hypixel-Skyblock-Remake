package net.swofty.type.skyblockgeneric.trading;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.type.skyblockgeneric.SkyBlockGenericLoader;
import net.swofty.type.skyblockgeneric.user.SkyBlockPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TradeManager {
    private static final long REQUEST_EXPIRY_SECONDS = 60L;

    private static final Map<UUID, TradeRequest> OUTGOING = new ConcurrentHashMap<>();
    private static final Map<UUID, TradeRequest> INCOMING = new ConcurrentHashMap<>();
    private static final Map<UUID, TradeSession> ACTIVE = new ConcurrentHashMap<>();

    private TradeManager() {
    }

    public static TradeSession getActiveSession(UUID playerId) {
        return ACTIVE.get(playerId);
    }

    public static void removeActiveSession(TradeSession session) {
        ACTIVE.remove(session.getPlayerA().getUuid());
        ACTIVE.remove(session.getPlayerB().getUuid());
    }

    public static void handleDisconnect(SkyBlockPlayer player) {
        TradeSession session = ACTIVE.get(player.getUuid());
        if (session != null) {
            session.cancel(player, "§c" + player.getUsername() + " cancelled the trade!");
        }

        TradeRequest outgoing = OUTGOING.remove(player.getUuid());
        if (outgoing != null) {
            INCOMING.remove(outgoing.getTarget());
            outgoing.cancelExpiry();
        }

        TradeRequest incoming = INCOMING.remove(player.getUuid());
        if (incoming != null) {
            OUTGOING.remove(incoming.getSender());
            incoming.cancelExpiry();
        }
    }

    public static void sendTradeRequest(SkyBlockPlayer sender, SkyBlockPlayer target) {
        if (sender == null || target == null) return;
        if (sender.getUuid().equals(target.getUuid())) {
            sender.sendMessage("§cYou cannot trade with yourself!");
            return;
        }

        if (!isTradeableDistance(sender, target)) {
            sender.sendMessage("§cYou must be within 9 blocks of that player to trade.");
            return;
        }

        if (ACTIVE.containsKey(sender.getUuid()) || ACTIVE.containsKey(target.getUuid())) {
            sender.sendMessage("§cThat player is already trading.");
            return;
        }

        TradeRequest existingOutgoing = OUTGOING.get(sender.getUuid());
        if (existingOutgoing != null) {
            sender.sendMessage("§cYou already have a pending trade request!");
            return;
        }

        TradeRequest existingIncomingForTarget = INCOMING.get(target.getUuid());
        if (existingIncomingForTarget != null) {
            sender.sendMessage("§cThat player already has a pending trade request!");
            return;
        }

        TradeRequest existingIncoming = INCOMING.get(sender.getUuid());
        if (existingIncoming != null && existingIncoming.getSender().equals(target.getUuid())) {
            acceptTradeRequest(sender, target);
            return;
        }

        TradeRequest request = new TradeRequest(sender.getUuid(), target.getUuid());
        OUTGOING.put(sender.getUuid(), request);
        INCOMING.put(target.getUuid(), request);

        Task task = MinecraftServer.getSchedulerManager().buildTask(() -> expireRequest(request))
                .delay(TaskSchedule.seconds(REQUEST_EXPIRY_SECONDS))
                .schedule();
        request.setExpiryTask(task);

        sendTradeRequestMessage(sender, target);
    }

    public static void acceptTradeRequest(SkyBlockPlayer target, SkyBlockPlayer sender) {
        if (sender == null || target == null) return;

        TradeRequest request = INCOMING.get(target.getUuid());
        if (request == null || !request.getSender().equals(sender.getUuid())) {
            target.sendMessage("§cYou do not have a pending trade request from that player.");
            return;
        }

        if (ACTIVE.containsKey(sender.getUuid()) || ACTIVE.containsKey(target.getUuid())) {
            target.sendMessage("§cThat player is already trading.");
            return;
        }

        if (!isTradeableDistance(sender, target)) {
            target.sendMessage("§cYou must be within 9 blocks of that player to trade.");
            return;
        }

        OUTGOING.remove(request.getSender());
        INCOMING.remove(request.getTarget());
        request.cancelExpiry();

        TradeSession session = new TradeSession(sender, target);
        ACTIVE.put(sender.getUuid(), session);
        ACTIVE.put(target.getUuid(), session);
        session.open();
    }

    public static SkyBlockPlayer findPlayerByName(String username) {
        if (username == null) return null;
        for (SkyBlockPlayer player : SkyBlockGenericLoader.getLoadedPlayers()) {
            if (player.getUsername().equalsIgnoreCase(username)) {
                return player;
            }
        }
        return null;
    }

    private static boolean isTradeableDistance(SkyBlockPlayer sender, SkyBlockPlayer target) {
        if (sender.getInstance() == null || target.getInstance() == null) return false;
        if (!sender.getInstance().equals(target.getInstance())) return false;
        return sender.getPosition().distance(target.getPosition()) <= 9.0;
    }

    private static void sendTradeRequestMessage(SkyBlockPlayer sender, SkyBlockPlayer target) {
        String senderName = sender.getShortenedDisplayName();
        Component component = Component.text(senderName + " §ahas sent you a trade request. §bClick here §ato")
                .hoverEvent(Component.text("§6Click to trade!"))
                .clickEvent(ClickEvent.runCommand("/tradeaccept " + sender.getUsername()));

        target.sendMessage(component);
        target.sendMessage("§aaccept!");
    }

    private static void expireRequest(TradeRequest request) {
        OUTGOING.remove(request.getSender());
        INCOMING.remove(request.getTarget());

        SkyBlockPlayer sender = SkyBlockGenericLoader.getFromUUID(request.getSender());
        SkyBlockPlayer target = SkyBlockGenericLoader.getFromUUID(request.getTarget());

        if (sender != null) {
            sender.sendMessage("§cYour trade request to " + (target != null ? target.getUsername() : "that player") + " §cexpired!");
        }
        if (target != null) {
            target.sendMessage("§cYour trade request from " + (sender != null ? sender.getUsername() : "that player") + " §cexpired!");
        }
    }
}
