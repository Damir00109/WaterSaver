package com.damir00109;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public class WaterSaverClient implements ClientModInitializer {

    private static boolean loggedServer = false;
    private static boolean active = false;

    private static boolean wasInactive = false;
    private static boolean spaceHeld = false;

    private static int tickCounter = 0;
    private static GameMode lastMode = null;

    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null || client.getWindow() == null)
                return;

            boolean onServer = client.getCurrentServerEntry() != null;

            if (!onServer) {
                deactivate(client);
                return;
            }

            if (!loggedServer) {
                WaterSaver.LOGGER.info("WaterSaver: Connected to server: " +
                        client.getCurrentServerEntry().address);
                loggedServer = true;
                active = true;
            }

            if (!active) return;

            GameMode mode = client.interactionManager.getCurrentGameMode();

            // Проверка смены режима
            if (mode != null) {
                if (lastMode == null) {
                    lastMode = mode; // первое определение режима
                } else if (mode != lastMode) {
                    lastMode = mode;

                    switch (mode) {
                        case SURVIVAL:
                        case ADVENTURE:
                            client.player.sendMessage(Text.translatable("watersaver.enabled"), false);
                            WaterSaver.LOGGER.info("WaterSaver: Mode changed to SURVIVAL/ADVENTURE, mod enabled.");
                            break;

                        default:
                            client.player.sendMessage(Text.translatable("watersaver.disabled"), false);
                            WaterSaver.LOGGER.info("WaterSaver: Mode changed to CREATIVE/SPECTATOR, mod disabled.");
                            releaseSpace(client);
                            break;
                    }
                }
            }

            if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
                releaseSpace(client);
                return;
            }

            boolean minimized = client.getWindow().isMinimized();
            boolean inMenu = client.currentScreen instanceof GameMenuScreen;
            boolean inactive = minimized || inMenu;

            if (inactive != wasInactive) {
                wasInactive = inactive;

                if (inactive) {
                    WaterSaver.LOGGER.info("WaterSaver: Window inactive (minimized/menu).");
                } else {
                    WaterSaver.LOGGER.info("WaterSaver: Window active.");
                    releaseSpace(client);
                }
            }

            if (!inactive) return;

            tickCounter++;

            if (tickCounter >= 20) { // проверка каждую секунду
                tickCounter = 0;

                BlockPos pos = client.player.getBlockPos();
                boolean inWater =
                        client.world.getBlockState(pos).getBlock() == Blocks.WATER ||
                                client.world.getBlockState(pos.up()).getBlock() == Blocks.WATER;

                if (inWater) {
                    if (!spaceHeld) {
                        client.options.jumpKey.setPressed(true); // форсируем пробел через KeyBinding
                        spaceHeld = true;
                        WaterSaver.LOGGER.info("WaterSaver: Player in water, holding SPACE (forced).");
                    }
                } else {
                    if (spaceHeld) {
                        releaseSpace(client);
                        WaterSaver.LOGGER.info("WaterSaver: Player not in water, releasing SPACE.");
                    }
                }
            }
        });
    }

    private void deactivate(net.minecraft.client.MinecraftClient client) {
        active = false;
        loggedServer = false;
        wasInactive = false;
        lastMode = null;
        releaseSpace(client);
        WaterSaver.LOGGER.info("WaterSaver: Deactivated (player left server).");
    }

    private void releaseSpace(net.minecraft.client.MinecraftClient client) {
        if (spaceHeld) {
            client.options.jumpKey.setPressed(false); // отпускаем пробел
            spaceHeld = false;
        }
    }
}
