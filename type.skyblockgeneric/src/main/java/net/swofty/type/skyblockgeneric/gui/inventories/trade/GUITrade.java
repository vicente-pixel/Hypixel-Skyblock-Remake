package net.swofty.type.skyblockgeneric.gui.inventories.trade;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.inventory.click.Click;
import net.swofty.commons.StringUtility;
import net.swofty.type.generic.gui.HypixelSignGUI;
import net.swofty.type.generic.gui.inventory.HypixelInventoryGUI;
import net.swofty.type.generic.gui.inventory.ItemStackCreator;
import net.swofty.type.generic.gui.inventory.item.GUIClickableItem;
import net.swofty.type.generic.gui.inventory.item.GUIItem;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.skyblockgeneric.data.SkyBlockDataHandler;
import net.swofty.type.skyblockgeneric.data.datapoints.DatapointTradeData;
import net.swofty.type.skyblockgeneric.item.ItemAttributeHandler;
import net.swofty.type.skyblockgeneric.item.SkyBlockItem;
import net.swofty.type.skyblockgeneric.item.updater.PlayerItemUpdater;
import net.swofty.type.skyblockgeneric.levels.CustomLevelAward;
import net.swofty.type.skyblockgeneric.trading.TradeSession;
import net.swofty.type.skyblockgeneric.user.SkyBlockPlayer;

import java.util.ArrayList;
import java.util.List;

public class GUITrade extends HypixelInventoryGUI {
    private static final int[] SELF_SLOTS = {
            0, 1, 2, 3,
            9, 10, 11, 12,
            18, 19, 20, 21,
            27, 28, 29, 30
    };
    private static final int[] OTHER_SLOTS = {
            5, 6, 7, 8,
            14, 15, 16, 17,
            23, 24, 25, 26,
            32, 33, 34, 35
    };
    private static final int[] DIVIDER_SLOTS = {4, 13, 22, 31, 40};

    private static final int COIN_SLOT_SELF = 36;
    private static final int ACCEPT_SLOT = 39;
    private static final int STATUS_SLOT = 41;
    private static final int COIN_SLOT_OTHER = 44;

    private static final String COIN_TEXTURE_SMALL = "16b90f4fa3ec106bfef21f3b75f541a18e4757674f7d58250fa7e74952f087dc";
    private static final String COIN_TEXTURE_LARGE = "c9b77999fed3a2758bfeaf0793e52283817bea64044bf43ef29433f954bb52f6";

    private final TradeSession session;
    private final SkyBlockPlayer viewer;

    public GUITrade(TradeSession session, SkyBlockPlayer viewer) {
        super(formatTitle(session, viewer), InventoryType.CHEST_5_ROW);
        this.session = session;
        this.viewer = viewer;
        session.registerGui(viewer, this);
    }

    @Override
    public boolean allowHotkeying() {
        return false;
    }

    @Override
    public void setItems(InventoryGUIOpenEvent e) {
        refresh();
    }

    public void refresh() {
        synchronized (items) {
            items.clear();
        }

        for (int slot : DIVIDER_SLOTS) {
            set(slot, createDivider());
        }

        set(new GUIClickableItem(COIN_SLOT_SELF) {
            @Override
            public void run(InventoryPreClickEvent event, HypixelPlayer player) {
                openCoinInput();
            }

            @Override
            public ItemStack.Builder getItem(HypixelPlayer player) {
                return createCoinItem(viewer, true);
            }
        });

        set(new GUIClickableItem(ACCEPT_SLOT) {
            @Override
            public void run(InventoryPreClickEvent event, HypixelPlayer player) {
                session.toggleAccept(viewer);
            }

            @Override
            public ItemStack.Builder getItem(HypixelPlayer player) {
                return createAcceptItem();
            }
        });

        set(new GUIItem(STATUS_SLOT) {
            @Override
            public ItemStack.Builder getItem(HypixelPlayer player) {
                return createStatusItem();
            }
        });

        set(COIN_SLOT_OTHER, null);

        fillOffers(SELF_SLOTS, session.getOffers(viewer), true);
        fillOffers(OTHER_SLOTS, session.getOffers(getOther()), false);

        if (getInventory() != null) {
            updateItemStacks(getInventory(), viewer);
        }
    }

    @Override
    public void onBottomClick(InventoryPreClickEvent e) {
        if (!(e.getPlayer() instanceof SkyBlockPlayer player)) return;
        if (!(e.getInventory() instanceof net.minestom.server.inventory.PlayerInventory)) return;
        if (session.isClosed()) return;

        ItemStack clicked = e.getClickedItem();
        if (clicked == null || clicked.material() == Material.AIR) return;

        if (session.hasAccepted(player)) {
            e.setCancelled(true);
            return;
        }

        if (session.getOffers(player).size() >= SELF_SLOTS.length) {
            player.sendMessage("§cYou cannot offer any more items.");
            e.setCancelled(true);
            return;
        }

        SkyBlockItem skyBlockItem = new SkyBlockItem(clicked);
        if (!isTradeable(skyBlockItem)) {
            player.sendMessage("§cYou cannot trade that item!");
            e.setCancelled(true);
            return;
        }

        int slot = e.getSlot();
        player.getInventory().setItemStack(slot, ItemStack.AIR);
        session.addOfferItem(player, skyBlockItem);
        playVillagerIdle();
        e.setCancelled(true);
    }

    @Override
    public void onClose(InventoryCloseEvent e, CloseReason reason) {
        if (session.isClosed()) {
            return;
        }
        if (reason == CloseReason.SIGN_OPENED) {
            return;
        }
        if (HypixelSignGUI.signGUIs.containsKey(viewer)) {
            return;
        }
        session.cancel(viewer, "§c" + viewer.getUsername() + " cancelled the trade!");
    }

    private void fillOffers(int[] slots, List<SkyBlockItem> items, boolean isSelf) {
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            if (i >= items.size()) {
                set(slot, null);
                continue;
            }
            SkyBlockItem item = items.get(i);
            int index = i;
            ItemStack.Builder builder = PlayerItemUpdater.playerUpdate(viewer, item.getItemStack(), isSelf);
            set(new GUIClickableItem(slot) {
                @Override
                public void run(InventoryPreClickEvent e, HypixelPlayer p) {
                    if (!isSelf) return;
                    if (session.hasAccepted(viewer)) return;
                    if (e.getClick() instanceof Click.RightShift || e.getClick() instanceof Click.LeftShift) return;
                    session.removeOfferItem(viewer, index);
                    playVillagerIdle();
                }

                @Override
                public ItemStack.Builder getItem(HypixelPlayer player) {
                    return builder;
                }
            });
        }
    }

    private ItemStack.Builder createDivider() {
        return ItemStackCreator.getStack("§7⇦ Your stuff", Material.GRAY_STAINED_GLASS_PANE, 1,
                "§7Their stuff ⇨");
    }

    private ItemStack.Builder createCoinItem(SkyBlockPlayer owner, boolean clickable) {
        long amount = session.getCoins(owner);
        String texture = amount >= 100_000 ? COIN_TEXTURE_LARGE : COIN_TEXTURE_SMALL;
        List<String> lore = new ArrayList<>();

        var tradeDatapoint = owner.getSkyblockDataHandler()
                .get(SkyBlockDataHandler.Data.TRADE_DATA, DatapointTradeData.class);
        DatapointTradeData.TradeData tradeData = tradeDatapoint.getValue();
        tradeData.resetIfNeeded();
        tradeDatapoint.setValue(tradeData);

        long limit = getDailyLimit(owner);
        String limitDisplay = shortenLimit(limit);
        String tradedDisplay = shortenLimit(tradeData.getCoinsTradedToday());

        if (amount <= 0) {
            lore.add("");
            lore.add("§7Daily limit: §a" + tradedDisplay + "§7/" + limitDisplay);
            lore.add("");
            if (clickable) {
                lore.add("§eClick to add gold!");
            }
            return ItemStackCreator.getStackHead("§6Coins transaction", texture, 1, lore);
        }

        lore.add("§7Lump-sum amount");
        lore.add("");
        lore.add("§6Total Coins Offered:");
        lore.add("§7" + StringUtility.commaify(amount));
        return ItemStackCreator.getStackHead("§6" + StringUtility.commaify(amount) + " coins", texture, 1, lore);
    }

    private ItemStack.Builder createAcceptItem() {
        if (session.hasAccepted(viewer)) {
            return ItemStackCreator.getStack("§aDeal accepted!", Material.GREEN_TERRACOTTA, 1,
                    "§7You accepted the trade. Wait",
                    "§7for the other party to accept.");
        }

        boolean viewerEmpty = session.getOffers(viewer).isEmpty() && session.getCoins(viewer) == 0;
        boolean otherEmpty = session.getOffers(getOther()).isEmpty() && session.getCoins(getOther()) == 0;

        if (!viewerEmpty && otherEmpty) {
            return ItemStackCreator.getStack("§eWarning!", Material.ORANGE_TERRACOTTA, 1,
                    "§7You are offering items without",
                    "§7getting anything in return!",
                    "",
                    "§eClick to accept anyway!");
        }

        if (viewerEmpty && !otherEmpty) {
            return ItemStackCreator.getStack("§bGift!", Material.LIGHT_BLUE_TERRACOTTA, 1,
                    "§7You are receiving items without",
                    "§7offering anything in return!",
                    "",
                    "§eClick to accept!");
        }

        return ItemStackCreator.getStack("§aTrading!", Material.GREEN_TERRACOTTA, 1,
                "§7Click an item in your inventory",
                "§7to offer it for trade.");
    }

    private ItemStack.Builder createStatusItem() {
        if (session.hasAccepted(getOther())) {
            return ItemStackCreator.getStack("§aOther player confirmed!", Material.LIME_DYE, 1,
                    "§7Trading with " + getOther().getShortenedDisplayName() + "§7.",
                    "§7Waiting for you to confirm...");
        }

        return ItemStackCreator.getStack("§eNew deal", Material.LIGHT_GRAY_DYE, 1,
                "§7Trading with " + getOther().getShortenedDisplayName() + "§7.");
    }

    private SkyBlockPlayer getOther() {
        return viewer.getUuid().equals(session.getPlayerA().getUuid())
                ? session.getPlayerB()
                : session.getPlayerA();
    }

    private boolean isTradeable(SkyBlockItem item) {
        ItemAttributeHandler handler = item.getAttributeHandler();
        return handler.getSoulBoundData() == null;
    }

    private long getDailyLimit(SkyBlockPlayer player) {
        if (player.hasCustomLevelAward(CustomLevelAward.DAILY_COINS_TRADE_LIMIT_OF_10B)) {
            return 10_000_000_000L;
        }
        if (player.hasCustomLevelAward(CustomLevelAward.DAILY_COINS_TRADE_LIMIT_OF_1B)) {
            return 1_000_000_000L;
        }
        return 1_000_000_000L;
    }

    private String shortenLimit(long limit) {
        String shortValue = StringUtility.shortenNumber(limit).replace(".0", "");
        return shortValue.endsWith("B") ? shortValue : StringUtility.commaify(limit);
    }

    private void openCoinInput() {
        if (session.hasAccepted(viewer)) return;
        new HypixelSignGUI(viewer).open(new String[]{"Enter amount", "---------------"})
                .thenAccept(input -> {
                    if (input == null || input.isBlank()) {
                        reopen();
                        return;
                    }

                    Double parsed = StringUtility.parseNumberWithSuffix(input);
                    if (parsed == null) {
                        viewer.sendMessage("§cInvalid amount!");
                        playVillagerNo();
                        reopen();
                        return;
                    }

                    long amount = Math.max(0L, parsed.longValue());
                    if (amount <= 0) {
                        viewer.sendMessage("§cYou cannot scam with negative gold!");
                        playVillagerNo();
                        reopen();
                        return;
                    }

                    if (viewer.getCoins() < amount) {
                        viewer.sendMessage("§cYou don't have enough coins!");
                        playVillagerNo();
                        reopen();
                        return;
                    }

                    var tradeDatapoint = viewer.getSkyblockDataHandler()
                            .get(SkyBlockDataHandler.Data.TRADE_DATA, DatapointTradeData.class);
                    DatapointTradeData.TradeData tradeData = tradeDatapoint.getValue();
                    tradeData.resetIfNeeded();
                    tradeDatapoint.setValue(tradeData);

                    long limit = getDailyLimit(viewer);
                    long remaining = Math.max(0L, limit - tradeData.getCoinsTradedToday());
                    if (amount > remaining) {
                        viewer.sendMessage("§cYou have reached your daily coins trading limit!");
                        playVillagerNo();
                        reopen();
                        return;
                    }

                    session.setCoins(viewer, amount);
                    reopen();
                });
    }

    private void reopen() {
        if (session.isClosed()) return;
        new GUITrade(session, viewer).open(viewer);
    }

    private void playVillagerNo() {
        viewer.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.PLAYER, 1f, 1f));
    }

    private void playVillagerIdle() {
        viewer.playSound(Sound.sound(Key.key("entity.villager.ambient"), Sound.Source.PLAYER, 1f, 1f));
    }

    private static String formatTitle(TradeSession session, SkyBlockPlayer viewer) {
        String left = viewer.getUsername();
        String right = viewer.getUuid().equals(session.getPlayerA().getUuid())
                ? session.getPlayerB().getUsername()
                : session.getPlayerA().getUsername();
        return left + "          " + right;
    }
}
