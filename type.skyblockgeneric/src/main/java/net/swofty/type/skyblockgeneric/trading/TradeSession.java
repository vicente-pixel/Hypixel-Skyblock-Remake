package net.swofty.type.skyblockgeneric.trading;

import lombok.Getter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.commons.StringUtility;
import net.swofty.type.skyblockgeneric.data.SkyBlockDataHandler;
import net.swofty.type.skyblockgeneric.data.datapoints.DatapointTradeData;
import net.swofty.type.skyblockgeneric.gui.inventories.trade.GUITrade;
import net.swofty.type.skyblockgeneric.item.SkyBlockItem;
import net.swofty.type.skyblockgeneric.user.SkyBlockPlayer;

import java.util.*;

@Getter
public class TradeSession {
    private final SkyBlockPlayer playerA;
    private final SkyBlockPlayer playerB;
    private final Map<UUID, List<SkyBlockItem>> offers = new HashMap<>();
    private final Map<UUID, Long> coins = new HashMap<>();
    private final Map<UUID, Boolean> accepted = new HashMap<>();
    private final Map<UUID, GUITrade> guis = new HashMap<>();
    private boolean closed;

    public TradeSession(SkyBlockPlayer playerA, SkyBlockPlayer playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
        offers.put(playerA.getUuid(), new ArrayList<>());
        offers.put(playerB.getUuid(), new ArrayList<>());
        coins.put(playerA.getUuid(), 0L);
        coins.put(playerB.getUuid(), 0L);
        accepted.put(playerA.getUuid(), false);
        accepted.put(playerB.getUuid(), false);
    }

    public void open() {
        new GUITrade(this, playerA).open(playerA);
        new GUITrade(this, playerB).open(playerB);
    }

    public void registerGui(SkyBlockPlayer player, GUITrade gui) {
        guis.put(player.getUuid(), gui);
    }

    public void updateDisplays() {
        for (GUITrade gui : guis.values()) {
            gui.refresh();
        }
    }

    public void addOfferItem(SkyBlockPlayer player, SkyBlockItem item) {
        if (closed) return;
        offers.get(player.getUuid()).add(item);
        resetAcceptances();
        updateDisplays();
    }

    public void removeOfferItem(SkyBlockPlayer player, int index) {
        if (closed) return;
        List<SkyBlockItem> list = offers.get(player.getUuid());
        if (index < 0 || index >= list.size()) return;
        SkyBlockItem removed = list.remove(index);
        player.addAndUpdateItem(removed);
        resetAcceptances();
        updateDisplays();
    }

    public void setCoins(SkyBlockPlayer player, long amount) {
        if (closed) return;
        coins.put(player.getUuid(), Math.max(0L, amount));
        resetAcceptances();
        updateDisplays();
    }

    public long getCoins(SkyBlockPlayer player) {
        return coins.getOrDefault(player.getUuid(), 0L);
    }

    public List<SkyBlockItem> getOffers(SkyBlockPlayer player) {
        return offers.getOrDefault(player.getUuid(), List.of());
    }

    public boolean hasAccepted(SkyBlockPlayer player) {
        return accepted.getOrDefault(player.getUuid(), false);
    }

    public void toggleAccept(SkyBlockPlayer player) {
        if (closed) return;
        if (Boolean.TRUE.equals(accepted.get(player.getUuid()))) return;
        accepted.put(player.getUuid(), true);
        updateDisplays();

        if (Boolean.TRUE.equals(accepted.get(playerA.getUuid()))
                && Boolean.TRUE.equals(accepted.get(playerB.getUuid()))) {
            finalizeTrade();
        }
    }

    public void cancel(SkyBlockPlayer canceller, String otherMessage) {
        if (closed) return;
        closed = true;

        returnOffers(playerA);
        returnOffers(playerB);

        playerA.sendMessage(playerA.getUuid().equals(canceller.getUuid())
                ? "§cYou cancelled the trade!"
                : otherMessage);
        playerB.sendMessage(playerB.getUuid().equals(canceller.getUuid())
                ? "§cYou cancelled the trade!"
                : otherMessage);

        playVillagerNo(playerA);
        playVillagerNo(playerB);

        closeInventories();
        TradeManager.removeActiveSession(this);
    }

    private void resetAcceptances() {
        accepted.put(playerA.getUuid(), false);
        accepted.put(playerB.getUuid(), false);
    }

    private void returnOffers(SkyBlockPlayer player) {
        for (SkyBlockItem item : offers.getOrDefault(player.getUuid(), List.of())) {
            player.addAndUpdateItem(item);
        }
        offers.get(player.getUuid()).clear();
    }

    private void finalizeTrade() {
        if (closed) return;

        long coinsA = coins.get(playerA.getUuid());
        long coinsB = coins.get(playerB.getUuid());

        if (playerA.getCoins() < coinsA || playerB.getCoins() < coinsB) {
            cancel(playerA, "§c" + playerA.getUsername() + " cancelled the trade!");
            return;
        }

        closed = true;
        TradeManager.removeActiveSession(this);

        playerA.removeCoins(coinsA);
        playerB.removeCoins(coinsB);
        playerA.addCoins(coinsB);
        playerB.addCoins(coinsA);

        for (SkyBlockItem item : offers.get(playerA.getUuid())) {
            playerB.addAndUpdateItem(item);
        }
        for (SkyBlockItem item : offers.get(playerB.getUuid())) {
            playerA.addAndUpdateItem(item);
        }

        updateTradeData(playerA, coinsA);
        updateTradeData(playerB, coinsB);

        sendTradeSummary(playerA, playerB, coinsA, coinsB, offers.get(playerB.getUuid()));
        sendTradeSummary(playerB, playerA, coinsB, coinsA, offers.get(playerA.getUuid()));

        playVillagerYes(playerA);
        playVillagerYes(playerB);
        playAcceptSequence(playerA);
        playAcceptSequence(playerB);

        playerA.getAchievementHandler().addProgressByTrigger("skyblock.businessman_trigger", 1);
        playerB.getAchievementHandler().addProgressByTrigger("skyblock.businessman_trigger", 1);

        closeInventories();
    }

    private void sendTradeSummary(SkyBlockPlayer receiver, SkyBlockPlayer other, long coinsPaid,
                                  long coinsReceived, List<SkyBlockItem> receivedItems) {
        receiver.sendMessage("§6Trade completed with " + other.getShortenedDisplayName() + "§6!");
        if (coinsPaid > 0) {
            receiver.sendMessage("§c- §6" + StringUtility.commaify(coinsPaid) + " coins");
        }
        if (coinsReceived > 0) {
            receiver.sendMessage("§a§l+ §r§6Coins");
        }
        for (SkyBlockItem item : receivedItems) {
            receiver.sendMessage("§a§l+ §r" + item.getDisplayName());
        }
    }

    private void updateTradeData(SkyBlockPlayer player, long coinsSent) {
        DatapointTradeData.TradeData tradeData = player.getSkyblockDataHandler()
                .get(SkyBlockDataHandler.Data.TRADE_DATA, DatapointTradeData.class)
                .getValue();
        tradeData.addCoinsTraded(coinsSent);
        player.getSkyblockDataHandler()
                .get(SkyBlockDataHandler.Data.TRADE_DATA, DatapointTradeData.class)
                .setValue(tradeData);
    }

    private void closeInventories() {
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            playerA.closeInventory();
            playerB.closeInventory();
        });
    }

    private void playVillagerNo(SkyBlockPlayer player) {
        player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.PLAYER, 1f, 1f));
    }

    private void playVillagerYes(SkyBlockPlayer player) {
        player.playSound(Sound.sound(Key.key("entity.villager.yes"), Sound.Source.PLAYER, 1f, 1f));
    }

    private void playAcceptSequence(SkyBlockPlayer player) {
        for (int i = 0; i < 8; i++) {
            int delayTicks = i + 1;
            MinecraftServer.getSchedulerManager().scheduleTask(() -> {
                player.playSound(Sound.sound(Key.key("block.note_block.pling"), Sound.Source.PLAYER, 0.6f, 0.5f));
            }, TaskSchedule.tick(delayTicks), TaskSchedule.stop());
        }
    }
}
