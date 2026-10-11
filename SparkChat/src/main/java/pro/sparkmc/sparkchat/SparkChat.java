
package pro.sparkmc.sparkchat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class SparkChat extends JavaPlugin implements Listener {

    private LuckPerms luckPerms;
    private final Map<String, String> colors = new HashMap<>();

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            getLogger().severe("LuckPerms was not found. Disabling SparkChat.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        loadColors();
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("hide-advancement-announcements", true)) {
            Bukkit.getWorlds().forEach(world ->
                    world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false));
        }

        getLogger().info("SparkChat enabled!");
    }

    private void loadColors() {
        if (!getConfig().isConfigurationSection("rank-colors")) return;

        for (String rank : getConfig().getConfigurationSection("rank-colors")
                .getKeys(false)) {
            colors.put(rank.toLowerCase(),
                    getConfig().getString("rank-colors." + rank, "#FFFFFF"));
        }
    }

    private String getRank(Player player) {
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        QueryOptions options = luckPerms.getContextManager()
                .getQueryOptions(user)
                .orElse(luckPerms.getContextManager().getStaticQueryOptions());

        return user.getPrimaryGroup().toLowerCase();
    }

    private String getRankColor(Player player) {
        String rank = getRank(player);
        return colors.getOrDefault(rank, "#FFFFFF");
    }

    private Component colorText(String text, String hex) {
        TextColor color = TextColor.fromHexString(hex);
        if (color == null) color = TextColor.color(0xFFFFFF);

        return Component.text()
                .content(text)
                .color(color)
                .build();
    }

    private String getPrefix(Player player) {
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        QueryOptions options = luckPerms.getContextManager()
                .getQueryOptions(user)
                .orElse(luckPerms.getContextManager().getStaticQueryOptions());

        String prefix = user.getCachedData()
                .getMetaData(options)
                .getPrefix();

        return prefix == null ? "" : prefix;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (getConfig().getBoolean("hide-join-messages", true)) {
            event.joinMessage(null);
        }

        String color = getRankColor(player);
        player.playerListName(colorText(player.getName(), color));

        if (!player.hasPlayedBefore()) {
            String message = getConfig().getString(
                    "first-join-message",
                    "&eWelcome &f{player} &eto SparkMC!"
            ).replace("{player}", player.getName());

            Bukkit.broadcast(LEGACY.deserialize(
                    ChatColor.translateAlternateColorCodes('&', message)
                            .replace("§", "§")
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (getConfig().getBoolean("hide-leave-messages", true)) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        String template = getConfig().getString(
                "chat-format", "{prefix}{name} &8» {message}"
        );

        String prefix = "";
        if (getConfig().getBoolean("include-luckperms-prefix", true)) {
            prefix = getPrefix(player);
        }

        String name = player.getName();
        String message = event.getMessage();
        String color = getRankColor(player);

        String formatted = template
                .replace("{prefix}", prefix)
                .replace("{name}", name)
                .replace("{message}", message);

        Component output = LEGACY.deserialize(
                ChatColor.translateAlternateColorCodes('&', formatted)
        );

        event.setCancelled(true);

        Bukkit.getScheduler().runTask(this, () ->
                Bukkit.getOnlinePlayers().forEach(viewer ->
                        viewer.sendMessage(output))
        );
    }
                             }
