package com.example.farmingseasons;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FarmingSeasons extends JavaPlugin implements Listener {

    private Season currentSeason;
    private int seasonDay;
    private File dataFile;
    private FileConfiguration dataConfig;
    private BossBar seasonBossBar;

    private final Map<Season, Double> growthRates = new HashMap<>();
    private final Map<Season, List<String>> seasonEvents = new HashMap<>();
    private final Map<Season, List<Material>> allowedCrops = new HashMap<>();

    @Override
    public void onEnable() {
        setupConfig();
        setupDataFile();
        loadSeasonData();
        initBossBar();

        getServer().getPluginManager().registerEvents(this, this);
        startSeasonScheduler();
        startBossBarUpdater();
    }

    private void setupConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        for (String seasonKey : config.getConfigurationSection("seasons").getKeys(false)) {
            Season season = Season.valueOf(seasonKey.toUpperCase());
            growthRates.put(season, config.getDouble("seasons." + seasonKey + ".growth_rate"));
            seasonEvents.put(season, config.getStringList("seasons." + seasonKey + ".events"));
            allowedCrops.put(season, config.getStringList("seasons." + seasonKey + ".crops")
                    .stream()
                    .map(Material::valueOf)
                    .toList());
        }
    }

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) saveResource("data.yml", false);
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadSeasonData() {
        currentSeason = Season.valueOf(dataConfig.getString("currentSeason", "SPRING"));
        seasonDay = dataConfig.getInt("seasonDay", 1);
    }

    private void initBossBar() {
        if (getConfig().getBoolean("bossbar.enabled")) {
            seasonBossBar = Bukkit.createBossBar(
                    getSeasonDisplayText(),
                    currentSeason.getBarColor(),
                    BarStyle.SEGMENTED_20
            );
            seasonBossBar.setVisible(true);
            Bukkit.getOnlinePlayers().forEach(seasonBossBar::addPlayer);
        }
    }

    private void startSeasonScheduler() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            seasonDay++;
            int seasonDuration = getConfig().getInt("seasons." + currentSeason.name().toLowerCase() + ".duration");

            if (seasonDay > seasonDuration) {
                changeSeason(currentSeason.next());
            }
        }, 0L, 24000L); // 하루 (20틱 * 1200 = 1일)
    }

    private void startBossBarUpdater() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (seasonBossBar != null) {
                seasonBossBar.setTitle(getSeasonDisplayText());
                if (getConfig().getBoolean("bossbar.color_by_season")) {
                    seasonBossBar.setColor(currentSeason.getBarColor());
                }
            }
        }, 0L, getConfig().getLong("bossbar.update_interval"));
    }

    private void changeSeason(Season newSeason) {
        currentSeason = newSeason;
        seasonDay = 1;
        saveSeasonData();

        String message = getConfig().getString("seasons." + currentSeason.name().toLowerCase() + ".message");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        triggerSeasonEvents();
    }

    private void triggerSeasonEvents() {
        List<String> events = seasonEvents.get(currentSeason);

        for (String event : events) {
            triggerSingleEvent(event);
        }
    }

    private void triggerSingleEvent(String eventName) {
        World world = Bukkit.getWorlds().get(0);

        switch (eventName) {
            case "METEOR_SHOWER" -> {
                Bukkit.broadcastMessage(ChatColor.RED + "[ 시스템 ] 하늘에서 운석이 떨어집니다!");
                for (int i = 0; i < 5; i++) {
                    Location randomLocation = world.getHighestBlockAt(new Random().nextInt(1000) - 500,
                            new Random().nextInt(1000) - 500).getLocation();
                    randomLocation.add(0, 20, 0);
                    world.spawnParticle(Particle.EXPLOSION_LARGE, randomLocation, 10);
                    world.playSound(randomLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.0F);
                    world.getBlockAt(randomLocation).setType(Material.TNT);
                }
            }

            case "SANTA_VISIT" -> {
                Bukkit.broadcastMessage(ChatColor.GOLD + "[ 시스템 ] 산타가 농장에 등장했습니다!");
                Bukkit.getScheduler().runTask(this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 2));
                    }
                });
            }

            case "DRAGON_FLIGHT" -> {
                Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "[ 시스템 ] 하늘에 드래곤이 나타났습니다!");
                EnderDragon dragon = (EnderDragon) world.spawnEntity(world.getSpawnLocation().add(0, 50, 0), EntityType.ENDER_DRAGON);
                Bukkit.getScheduler().runTaskLater(this, dragon::remove, 600L);
            }

            case "INVISIBLE_CREEPER_RAID" -> {
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "[ 시스템 ] 투명 크리퍼가 농장을 습격합니다!");
                for (int i = 0; i < 10; i++) {
                    Location spawnLocation = world.getHighestBlockAt(new Random().nextInt(1000) - 500,
                            new Random().nextInt(1000) - 500).getLocation();
                    Creeper creeper = (Creeper) world.spawnEntity(spawnLocation, EntityType.CREEPER);
                    creeper.setInvisible(true);
                }
            }

            case "BLACKOUT" -> {
                Bukkit.broadcastMessage(ChatColor.DARK_BLUE + "[ 시스템 ] 밤이 길어집니다! 몹이 더 강력해집니다.");
                world.setTime(18000); // 밤 시간 설정
                Bukkit.getScheduler().runTaskLater(this, () -> world.setTime(0), 600L); // 30초 후 낮으로 복귀
            }
        }
    }

    @EventHandler
    public void onCropGrowth(BlockGrowEvent event) {
        Material cropType = event.getBlock().getType();
        if (!allowedCrops.get(currentSeason).contains(cropType)) {
            event.setCancelled(true);
            return;
        }

        double growthRate = growthRates.get(currentSeason);
        if (Math.random() > growthRate) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 명령어 구현
        return true;
    }

    private void saveSeasonData() {
        dataConfig.set("currentSeason", currentSeason.name());
        dataConfig.set("seasonDay", seasonDay);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("데이터 저장 실패: " + e.getMessage());
        }
    }

    private String getSeasonDisplayText() {
        return currentSeason.getDisplayName() + ChatColor.WHITE + " - Day " + seasonDay;
    }

    @Override
    public void onDisable() {
        if (seasonBossBar != null) {
            seasonBossBar.removeAll();
        }
        saveSeasonData();
    }

    public enum Season {
        SPRING("봄", BarColor.GREEN),
        SUMMER("여름", BarColor.RED),
        AUTUMN("가을", BarColor.YELLOW),
        WINTER("겨울", BarColor.BLUE);

        private final String name;
        private final BarColor barColor;

        Season(String name, BarColor barColor) {
            this.name = name;
            this.barColor = barColor;
        }

        public String getDisplayName() {
            return ChatColor.BOLD + name;
        }

        public BarColor getBarColor() {
            return barColor;
        }

        public Season next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
