package net.swofty.type.skyblockgeneric.gui.inventories.bazaar;

import lombok.Getter;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.swofty.commons.StringUtility;
import net.swofty.commons.skyblock.item.ItemType;
import net.swofty.type.generic.gui.inventory.HypixelInventoryGUI;
import net.swofty.type.generic.gui.inventory.ItemStackCreator;
import net.swofty.type.generic.gui.inventory.RefreshingGUI;
import net.swofty.type.generic.gui.inventory.item.GUIClickableItem;
import net.swofty.type.generic.gui.inventory.item.GUIItem;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.utility.MathUtility;
import net.swofty.type.skyblockgeneric.bazaar.BazaarCategories;
import net.swofty.type.skyblockgeneric.bazaar.BazaarItemSet;
import net.swofty.type.skyblockgeneric.user.SkyBlockPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.swofty.type.skyblockgeneric.data.SkyBlockDataHandler;
import net.swofty.type.skyblockgeneric.data.datapoints.DatapointCompletedBazaarTransactions;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import net.swofty.type.generic.gui.HypixelSignGUI;
import net.minestom.server.MinecraftServer;

@Getter
public class GUIBazaar extends HypixelInventoryGUI implements RefreshingGUI {
    private static final int[] SLOTS = {
            11, 12, 13, 14, 15, 16,
            20, 21, 22, 23, 24, 25,
            29, 30, 31, 32, 33, 34,
            38, 39, 40, 41, 42, 43
    };
    private static final long CACHE_TTL_MS = 30_000L;

    private static final Map<BazaarCategories, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private final BazaarCategories category;

    public GUIBazaar(BazaarCategories category) {
        super("Bazaar → " + StringUtility.toNormalCase(category.name()),
                InventoryType.CHEST_6_ROW);
        this.category = category;

        fill(ItemStackCreator.createNamedItemStack(category.getGlassItem()));
        
        // Bottom Row
        set(new GUIClickableItem(45) {
            @Override
            public void run(InventoryPreClickEvent e, HypixelPlayer p) {
                new HypixelSignGUI(p).open(new String[]{"", "Enter Query"})
                        .thenAccept(query -> {
                            if (query == null || query.isBlank()) return;

                            List<ItemType> matches = new ArrayList<>();
                            for (BazaarCategories cat : BazaarCategories.values()) {
                                for (BazaarItemSet set : cat.getItems()) {
                                    for (ItemType type : set.items) {
                                        if (type.getDisplayName().toLowerCase().contains(query.toLowerCase())) {
                                            matches.add(type);
                                        }
                                    }
                                }
                            }

                            if (matches.isEmpty()) {
                                p.sendMessage("§cNo items found matching \"" + query + "\"");
                                return;
                            }

                            if (matches.size() > 10) {
                                matches = matches.subList(0, 10);
                                p.sendMessage("§eFound many items! Showing top 10 results.");
                            }

                            BazaarItemSet searchSet = new BazaarItemSet(
                                    ItemType.OAK_SIGN,
                                    "Search: " + query,
                                    matches.toArray(new ItemType[0])
                            );

                            MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                                new GUIBazaarItemSet(category, searchSet).open(p);
                            });
                        });
            }

            @Override
            public ItemStack.Builder getItem(HypixelPlayer p) {
                return ItemStackCreator.getStack("§aSearch", Material.OAK_SIGN, 1,
                        "§7Find products by name!", "", "§eClick to search!");
            }
        });

        set(new GUIClickableItem(47) {
            @Override
            public void run(InventoryPreClickEvent e, HypixelPlayer p) {
                // Sell inventory logic
            }

            @Override
            public ItemStack.Builder getItem(HypixelPlayer p) {
                return ItemStackCreator.getStack("§aSell Inventory Now", Material.CHEST, 1,
                        "§7Instantly sell any items in your",
                        "§7inventory that can be sold on the",
                        "§7Bazaar.",
                        "",
                        "§7§cYou don't have anything to sell!");
            }
        });

        set(GUIClickableItem.getCloseItem(49));

        set(new GUIClickableItem(50) {
            @Override
            public void run(InventoryPreClickEvent e, HypixelPlayer p) {
                SkyBlockPlayer player = (SkyBlockPlayer) p;
                new GUIBazaarOrders().open(p);
            }

            @Override
            public ItemStack.Builder getItem(HypixelPlayer p) {
                SkyBlockPlayer player = (SkyBlockPlayer) p;
                List<String> lore = new ArrayList<>();
                
                // This would ideally be fetched once and cached, but for now we'll use a placeholder 
                // or fetch it if we can do it synchronously from a cache.
                // For the prompt requirement, we'll try to use the most accurate info we have.
                lore.add("§7You don't have any ongoing orders.");
                lore.add("");
                lore.add("§eClick to manage!");
                
                return ItemStackCreator.getStack("§aManage Orders",
                        Material.BOOK, 1, lore);
            }
        });

        set(new GUIClickableItem(51) {
            @Override
            public void run(InventoryPreClickEvent e, HypixelPlayer p) {
            }

            @Override
            public ItemStack.Builder getItem(HypixelPlayer p) {
                SkyBlockPlayer player = (SkyBlockPlayer) p;
                List<String> lore = new ArrayList<>();
                
                var transactions = player.getSkyblockDataHandler()
                        .get(SkyBlockDataHandler.Data.COMPLETED_BAZAAR_TRANSACTIONS, DatapointCompletedBazaarTransactions.class)
                        .getValue().getTransactions();

                if (transactions.isEmpty()) {
                    lore.add("§7You haven't touched the Bazaar yet!");
                } else {
                    transactions.stream()
                        .sorted(Comparator.comparing(DatapointCompletedBazaarTransactions.CompletedBazaarTransaction::getTimestamp).reversed())
                        .limit(10)
                        .forEach(tx -> {
                            long diff = System.currentTimeMillis() - tx.getTimestamp().toEpochMilli();
                            long days = TimeUnit.MILLISECONDS.toDays(diff);
                            long hours = TimeUnit.MILLISECONDS.toHours(diff) % 24;
                            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
                            long seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60;
                            
                            String timeAgo;
                            if (days > 0) timeAgo = days + "d";
                            else if (hours > 0) timeAgo = hours + "h";
                            else if (minutes > 0) timeAgo = minutes + "m";
                            else timeAgo = Math.max(1, seconds) + "s";
                            
                            String typeStr = "";
                            String typeColor = "";
                            switch(tx.getType()) {
                                case BUY_COMPLETED: typeStr = "Instant Buy"; typeColor = "§a"; break;
                                case SELL_COMPLETED: typeStr = "Instant Sell"; typeColor = "§6"; break;
                                case REFUND: typeStr = "Cancelled Buy Order"; typeColor = "§7"; break; // Best guess mapping
                                default: typeStr = "Transaction"; typeColor = "§7"; break;
                            }
                            
                            if (tx.getType() == DatapointCompletedBazaarTransactions.TransactionType.REFUND) {
                                lore.add("§8" + timeAgo + " ago §7Cancelled §cBuy Order §7of " + "§a" + StringUtility.shortenNumber(tx.getQuantity()) + "§7x " + tx.getItemName());
                            } else {
                                lore.add("§8" + timeAgo + " ago " + typeColor + typeStr + " §7of " + typeColor + StringUtility.shortenNumber(tx.getQuantity()) + "§7x " + tx.getItemName());
                                lore.add(" §7for §6" + StringUtility.shortenNumber(tx.getPricePerUnit() * tx.getQuantity()) + " coins §7by " + player.getFullDisplayName());
                            }
                            lore.add("");
                        });
                }

                return ItemStackCreator.getStack("§aBazaar History",
                        Material.FILLED_MAP, 1, lore);
            }
        });

        set(new GUIClickableItem(52) {
            @Override
            public void run(InventoryPreClickEvent e, HypixelPlayer p) {
                // Settings logic
            }

            @Override
            public ItemStack.Builder getItem(HypixelPlayer p) {
                return ItemStackCreator.getStack("§aBazaar Settings", Material.REDSTONE_TORCH, 1,
                        "§7View and edit your Bazaar settings.", "", "§eClick to open!");
            }
        });
    }

    @Override
    public void onOpen(InventoryGUIOpenEvent e) {
        SkyBlockPlayer player = (SkyBlockPlayer) e.player();
        
        // Tabs at top
        renderCategoryTabs();

        // Update Manage Orders dynamically
        player.getBazaarConnector().getPendingOrders().thenAccept(orders -> {
            int active = orders.size();
            int expired = 0; // Need a way to track expired orders separately if possible
            double escrow = orders.stream().mapToDouble(net.swofty.type.skyblockgeneric.bazaar.BazaarConnector.BazaarOrder::getTotalValue).sum();
            
            set(new GUIClickableItem(50) {
                @Override
                public void run(InventoryPreClickEvent e, HypixelPlayer p) {
                    new GUIBazaarOrders().open(p);
                }

                @Override
                public ItemStack.Builder getItem(HypixelPlayer p) {
                    List<String> lore = new ArrayList<>();
                    if (active == 0) {
                        lore.add("§7You don't have any ongoing orders.");
                    } else {
                        lore.add("§7Orders: §e" + active + (expired > 0 ? " §8(" + expired + " expired)" : ""));
                        lore.add("");
                        lore.add("§7Escrow: §6" + StringUtility.commaify(escrow) + " coins");
                    }
                    lore.add("");
                    lore.add("§eClick to manage!");
                    
                    return ItemStackCreator.getStack("§aManage Orders", Material.BOOK, 1, lore);
                }
            });
            updateItemStacks(getInventory(), player);
        });

        long now = System.currentTimeMillis();
        CacheEntry entry = CACHE.get(category);

        if (entry != null && now - entry.timestamp <= CACHE_TTL_MS) {
            // fresh cache: render immediately
            renderSlots(entry.slots);
        } else {
            // expired or missing: clear cache & show placeholders
            CACHE.remove(category);
            renderPlaceholders();
            // async rebuild & render
            CompletableFuture.runAsync(() -> rebuildCacheAndRender((SkyBlockPlayer) e.player()));
        }
    }

    private void renderCategoryTabs() {
        int row = 0;
        for (BazaarCategories cat : BazaarCategories.values()) {
            int slot = row * 9;
            
            set(new GUIClickableItem(slot) {
                @Override
                public void run(InventoryPreClickEvent e, HypixelPlayer p) {
                    SkyBlockPlayer player = (SkyBlockPlayer) p;
                    new GUIBazaar(cat).open(p);
                }

                @Override
                public ItemStack.Builder getItem(HypixelPlayer p) {
                    SkyBlockPlayer player = (SkyBlockPlayer) p;
                    var b = ItemStackCreator.getStack(
                            cat.getColor() + StringUtility.toNormalCase(cat.name()),
                            cat.getDisplayItem(), 1,
                            "§8Category", " ",
                            category == cat
                                    ? "§aCurrently viewing!"
                                    : "§eClick to view!"
                    );
                    if (category == cat) b = ItemStackCreator.enchant(b);
                    return b;
                }
            });
            row++;
        }
        updateItemStacks(getInventory(), getPlayer());
    }

    private void renderPlaceholders() {
        for (int slot : SLOTS) {
            set(new GUIItem(slot) {
                @Override
                public ItemStack.Builder getItem(HypixelPlayer p) {
                    SkyBlockPlayer player = (SkyBlockPlayer) p;
                    return ItemStackCreator.getStack("§7Loading...", Material.GRAY_STAINED_GLASS_PANE, 1);
                }
            });
        }
        updateItemStacks(getInventory(), getPlayer());
    }

    private void rebuildCacheAndRender(SkyBlockPlayer player) {
        List<BazaarItemSet> sets = category.getItems().stream().toList();

        // Create a thread-safe map to collect results
        Map<BazaarItemSet, Map<ItemType, PriceData>> setDataMap = new ConcurrentHashMap<>();

        // Initialize the map
        for (BazaarItemSet set : sets) {
            setDataMap.put(set, new ConcurrentHashMap<>());
        }

        // Collect all futures for price fetching
        List<CompletableFuture<Void>> allFutures = new ArrayList<>();

        for (BazaarItemSet set : sets) {
            for (ItemType type : set.items) {
                CompletableFuture<Void> future = player.getBazaarConnector().getItemStatistics(type)
                        .thenAccept(stats -> {
                            // Store the price data thread-safely
                            setDataMap.get(set).put(type, new PriceData(
                                    type,
                                    stats.averageAsk(),  // Average sell price
                                    stats.averageBid()    // Average buy price
                            ));
                        })
                        .exceptionally(throwable -> {
                            System.err.println("Failed to fetch bazaar data for " + type.name() + ": " + throwable.getMessage());
                            // Store empty data on failure
                            setDataMap.get(set).put(type, new PriceData(type, 0, 0));
                            return null;
                        });
                allFutures.add(future);
            }
        }

        // When all price data is collected, build the cache entry
        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    List<CacheEntry.CachedSlot> slotData = new ArrayList<>();

                    // Build slot records
                    for (int i = 0; i < sets.size() && i < SLOTS.length; i++) {
                        int slot = SLOTS[i];
                        BazaarItemSet set = sets.get(i);

                        // Build lore safely
                        List<String> lore = new ArrayList<>();
                        lore.add("§8" + set.items.size() + " products");
                        lore.add(" ");

                        // Add price data for each item in the set
                        Map<ItemType, PriceData> priceDataMap = setDataMap.get(set);
                        for (ItemType type : set.items) {
                            PriceData priceData = priceDataMap.get(type);
                            if (priceData != null) {
                                lore.add(type.rarity.getColor()
                                        + "▶ §7" + type.getDisplayName()
                                        + " §c" + StringUtility.shortenNumber(priceData.sellPrice())
                                        + " §8| §a" + StringUtility.shortenNumber(priceData.buyPrice()));
                            }
                        }

                        lore.add(" ");
                        lore.add("§eClick to view products!");
                        slotData.add(new CacheEntry.CachedSlot(slot, set, lore));
                    }

                    // Cache it
                    CACHE.put(category, new CacheEntry(System.currentTimeMillis(), slotData));

                    // Schedule UI update on main thread
                    MathUtility.delay(() -> {
                        renderSlots(slotData);
                    }, 1);
                })
                .exceptionally(throwable -> {
                    System.err.println("Failed to rebuild bazaar cache: " + throwable.getMessage());

                    // Fallback: render with "Error loading" placeholders
                    MathUtility.delay(() -> {
                        for (int slot : SLOTS) {
                            set(new GUIItem(slot) {
                                @Override
                                public ItemStack.Builder getItem(HypixelPlayer p) {
                                    SkyBlockPlayer player = (SkyBlockPlayer) p;
                                    return ItemStackCreator.getStack("§cError Loading", Material.BARRIER, 1,
                                            "§7Failed to load bazaar data",
                                            "§7Please try again later");
                                }
                            });
                        }
                        updateItemStacks(getInventory(), getPlayer());
                    }, 1);
                    return null;
                });
    }

    private void renderSlots(List<CacheEntry.CachedSlot> slots) {
        // Clear existing slots first
        for (int slot : SLOTS) {
            set(new GUIItem(slot) {
                @Override
                public ItemStack.Builder getItem(HypixelPlayer p) {
                    SkyBlockPlayer player = (SkyBlockPlayer) p;
                    return ItemStack.builder(Material.AIR);
                }
            });
        }

        // Set the new slots
        for (CacheEntry.CachedSlot cs : slots) {
            set(new GUIClickableItem(cs.slot()) {
                @Override
                public void run(InventoryPreClickEvent e, HypixelPlayer p) {
                    SkyBlockPlayer player = (SkyBlockPlayer) p;
                    new GUIBazaarItemSet(category, cs.itemSet()).open(p);
                }

                @Override
                public ItemStack.Builder getItem(HypixelPlayer p) {
                    SkyBlockPlayer player = (SkyBlockPlayer) p;
                    return ItemStackCreator.getStack(
                            category.getColor() + cs.itemSet().displayName,
                            cs.itemSet().displayMaterial.material,
                            1,
                            cs.lore()
                    );
                }
            });
        }
        updateItemStacks(getInventory(), getPlayer());
    }

    /**
     * Thread-safe price data holder
     */
    private record PriceData(ItemType itemType, double sellPrice, double buyPrice) {
    }

    @Override
    public boolean allowHotkeying() {
        return false;
    }

    @Override
    public void onBottomClick(InventoryPreClickEvent e) {
        e.setCancelled(true);
    }

    @Override
    public int refreshRate() {
        return 10;
    }

    @Override
    public void refreshItems(HypixelPlayer p) {
        SkyBlockPlayer player = (SkyBlockPlayer) p;
        player.getBazaarConnector().isOnline().thenAccept(online -> {
            if (!online) {
                player.sendMessage("§cThe Bazaar is currently offline!");
                player.closeInventory();
            } else {
                player.getBazaarConnector().processAllPendingTransactions();
            }
        });
    }

    private record CacheEntry(long timestamp, List<CachedSlot> slots) {
        record CachedSlot(int slot, BazaarItemSet itemSet, List<String> lore) {
        }
    }
}