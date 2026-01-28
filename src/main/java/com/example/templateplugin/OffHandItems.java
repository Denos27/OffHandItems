package com.example.templateplugin;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class OffHandItems extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Set<String> ONE_HAND_PATTERNS = new HashSet<>(Arrays.asList(
            "Lantern",
            "Torch",
            "Candle",
            "Light",
            "Lamp",
            "Wisp",
            "Block",
            "Stone",
            "Wood",
            "Dirt",
            "Sand"
    ));

    private final Set<String> ONE_HAND_CATEGORIES = new HashSet<>(Arrays.asList(
            "Furniture.Lighting",
            "Blocks.Light",
            "Blocks"
    ));

    private final Set<String> BLACKLIST = new HashSet<>();

    private int modifiedItems = 0;
    private int totalScanned = 0;
    private int matchedItems = 0;
    private boolean modificationsApplied = false;

    private ScheduledExecutorService scheduler;

    public OffHandItems(@Nonnull JavaPluginInit init) {
        super(init);
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("OffHandItems Plugin loading...");
    }

    @Override
    protected void setup() {
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("OffHandItems setup called");
    }

    @Override
    protected void start() {
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("===========================================");
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("OFFHAND BLOCKS STARTING!");
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("Patterns: " + ONE_HAND_PATTERNS);
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("===========================================");

        scheduler = Executors.newSingleThreadScheduledExecutor();

        tryModifyAllItems("Immediate attempt");

        scheduleRetries();
    }

    private void scheduleRetries() {
        long[] delaysInSeconds = {1L, 3L, 5L, 10L, 20L};

        for (int i = 0; i < delaysInSeconds.length; i++) {
            final int attemptNumber = i + 2;
            final long delay = delaysInSeconds[i];

            scheduler.schedule(() -> {
                if (!modificationsApplied) {
                    tryModifyAllItems("Attempt " + attemptNumber + " (" + delay + "s)");
                }
            }, delay, TimeUnit.SECONDS);
        }
    }

    private void tryModifyAllItems(String attemptName) {
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(">>> " + attemptName + " - Starting scan...");

        try {
            DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();

            if (itemMap == null) {
                ((HytaleLogger.Api) LOGGER.at(Level.WARNING)).log("⚠ Item map not available (null)");
                return;
            }

            Map<String, Item> assets = itemMap.getAssetMap();

            if (assets == null || assets.isEmpty()) {
                ((HytaleLogger.Api) LOGGER.at(Level.WARNING)).log("⚠ Asset map empty or null");
                return;
            }

            totalScanned = assets.size();
            ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(">>> Total items in map: " + totalScanned);

            int previousModCount = modifiedItems;

            for (Item item : assets.values()) {
                try {
                    if (shouldBeOneHanded(item)) {
                        String itemId = item.getId();
                        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(">>> MATCHED ITEM: " + itemId);
                        matchedItems++;
                        modifyItem(item);
                    }
                } catch (Exception e) {
                    ((HytaleLogger.Api) LOGGER.at(Level.WARNING)).log("Error on item: " + e.getMessage());
                }
            }

            int newlyModified = modifiedItems - previousModCount;

            if (newlyModified > 0) {
                modificationsApplied = true;
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("===========================================");
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("✓ MODIFICATIONS SUCCESSFUL - " + attemptName);
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("  Items scanned: " + totalScanned);
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("  Items matched: " + matchedItems);
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("  Newly modified: " + newlyModified);
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("  Total modified: " + modifiedItems);
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("===========================================");
            } else {
                ((HytaleLogger.Api) LOGGER.at(Level.WARNING)).log("⚠ No modifications - " + attemptName);
            }

        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.at(Level.SEVERE)).log("✗ ERROR - " + attemptName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void modifyItem(Item item) {
        try {
            String itemId = item.getId();

            if (BLACKLIST.contains(itemId)) {
                return;
            }

            if (modifiedItems < 3) {
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(">>> DEBUG - Analyzing: " + itemId);
                Field[] fields = item.getClass().getDeclaredFields();
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(">>> Available fields: " + fields.length);

                for (Field f : fields) {
                    f.setAccessible(true);
                    try {
                        Object value = f.get(item);
                        String valueStr = (value != null) ? value.toString() : "null";
                        if (valueStr.length() > 100) {
                            valueStr = valueStr.substring(0, 100) + "...";
                        }
                        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(
                                ">>>   " + f.getName() + " (" + f.getType().getSimpleName() + ") = " + valueStr
                        );
                    } catch (Exception e) {
                        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(
                                ">>>   " + f.getName() + " (" + f.getType().getSimpleName() + ") - not readable"
                        );
                    }
                }
            }

            boolean modified = false;

            Field playerAnimField = findField(item.getClass(), "playerAnimationsId");
            if (playerAnimField != null) {
                try {
                    playerAnimField.setAccessible(true);
                    Object currentValue = playerAnimField.get(item);

                    if (!"Torch".equals(currentValue)) {
                        playerAnimField.set(item, "Torch");
                        modified = true;
                        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(
                                "  ✓ playerAnimationsId: " + currentValue + " → Torch"
                        );
                    }
                } catch (Exception e) {
                    ((HytaleLogger.Api) LOGGER.at(Level.WARNING)).log(
                            "  ✗ Error playerAnimationsId: " + e.getMessage()
                    );
                }
            }

            Field utilityField = findField(item.getClass(), "utility");
            if (utilityField != null) {
                try {
                    utilityField.setAccessible(true);
                    Object utility = utilityField.get(item);

                    if (utility != null) {
                        Field usableField = findField(utility.getClass(), "usable");
                        if (usableField != null && usableField.getType() == boolean.class) {
                            usableField.setAccessible(true);
                            Boolean currentUsable = (Boolean) usableField.get(utility);

                            if (currentUsable == null || !currentUsable) {
                                usableField.set(utility, true);
                                modified = true;
                                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(
                                        "  ✓ usable: " + currentUsable + " → true"
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    ((HytaleLogger.Api) LOGGER.at(Level.WARNING)).log(
                            "  ✗ Error utility: " + e.getMessage()
                    );
                }
            }

            String[] twoHandedFields = {"twoHanded", "requiresTwoHands", "isTwoHanded", "twoHand"};
            for (String fieldName : twoHandedFields) {
                Field field = findField(item.getClass(), fieldName);
                if (field != null && field.getType() == boolean.class) {
                    try {
                        field.setAccessible(true);
                        Boolean currentValue = (Boolean) field.get(item);

                        if (currentValue != null && currentValue) {
                            field.set(item, false);
                            modified = true;
                            ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log(
                                    "  ✓ " + fieldName + ": true → false"
                            );
                        }
                    } catch (Exception e) {
                        ((HytaleLogger.Api) LOGGER.at(Level.WARNING)).log(
                                "  ✗ Error " + fieldName + ": " + e.getMessage()
                        );
                    }
                }
            }

            if (modified) {
                modifiedItems++;
                ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("✓ Item modified: " + itemId);
            }

        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.at(Level.SEVERE)).log("✗ Error in modifyItem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private boolean shouldBeOneHanded(Item item) {
        try {
            String itemId = item.getId();
            if (itemId == null) {
                return false;
            }

            for (String pattern : ONE_HAND_PATTERNS) {
                if (itemId.contains(pattern)) {
                    return true;
                }
            }

            Field categoriesField = findField(item.getClass(), "categories");
            if (categoriesField != null) {
                try {
                    categoriesField.setAccessible(true);
                    Object categoriesObj = categoriesField.get(item);

                    if (categoriesObj instanceof String[]) {
                        String[] categories = (String[]) categoriesObj;
                        for (String category : categories) {
                            if (category != null && ONE_HAND_CATEGORIES.contains(category)) {
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }

        } catch (Exception e) {
        }

        return false;
    }

    @Override
    protected void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("===========================================");
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("OffHandItems Plugin stopped");
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("Items scanned: " + totalScanned);
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("Items matched: " + matchedItems);
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("Items modified: " + modifiedItems);
        ((HytaleLogger.Api) LOGGER.at(Level.INFO)).log("===========================================");
    }
}