package cn.kurt6.carpetskyadditions_automultiplayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.literal;

public class Carpetskyadditions_automultiplayer implements ModInitializer {
    private static final String DATA_FILE = "skyisland_players.dat";
    private static MinecraftServer server;
    private static final Map<String, Integer> playerIslands = new HashMap<>();
    private static int nextIslandNumber = 1;  // 默认从1开始

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("new")
                    .then(literal("skyisland")
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                if (player == null) {
                                    context.getSource().sendError(Text.literal("只有玩家可以执行此命令"));
                                    return 0;
                                }

                                String playerName = player.getName().getString();
                                if (playerIslands.containsKey(playerName)) {
                                    player.sendMessage(Text.literal("你已经拥有一个空岛了！编号: " + playerIslands.get(playerName)), false);
                                    return 0;
                                }

                                // 分配新的岛屿编号（自动使用nextIslandNumber当前值）
                                int islandNumber = nextIslandNumber;
                                playerIslands.put(playerName, islandNumber);
                                nextIslandNumber++;  // 使用后递增

                                // 执行创建新岛屿的命令
                                String newCommand = "/skyisland new";
                                server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), newCommand);

                                // 执行传送命令
                                String joinCommand = String.format("/skyisland join %d %s", islandNumber, playerName);
                                server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), joinCommand);

                                player.sendMessage(Text.literal("已为你创建新的空岛！编号: " + islandNumber), false);
                                saveData();

                                return 1;
                            })));
        });
    }

    private void onServerStarting(MinecraftServer server) {
        Carpetskyadditions_automultiplayer.server = server;
        loadData();

        // 加载后，nextIslandNumber应该是文件中存储的最大值+1
        // 如果没有数据，则保持默认值1
    }

    private void onServerStopping(MinecraftServer server) {
        saveData();
    }

    private void loadData() {
        playerIslands.clear();
        Path savePath = server.getSavePath(WorldSavePath.ROOT).resolve(DATA_FILE);

        if (!savePath.toFile().exists()) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(savePath.toFile()))) {
            nextIslandNumber = ois.readInt();
            @SuppressWarnings("unchecked")
            Map<String, Integer> loaded = (Map<String, Integer>) ois.readObject();
            playerIslands.putAll(loaded);

            // 确保nextIslandNumber比现有最大编号大1
            int maxIslandNumber = playerIslands.values().stream().max(Integer::compare).orElse(0);
            nextIslandNumber = Math.max(nextIslandNumber, maxIslandNumber + 1);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("加载空岛玩家数据失败: " + e.getMessage());
            // 出错时重置为1或保持当前值
            nextIslandNumber = 1;
        }
    }

    private void saveData() {
        Path savePath = server.getSavePath(WorldSavePath.ROOT).resolve(DATA_FILE);

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(savePath.toFile()))) {
            oos.writeInt(nextIslandNumber);
            oos.writeObject(playerIslands);
        } catch (IOException e) {
            System.err.println("保存空岛玩家数据失败: " + e.getMessage());
        }
    }
}