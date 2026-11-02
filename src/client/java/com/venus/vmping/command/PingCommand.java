package com.venus.vmping.command;

import com.mojang.brigadier.CommandDispatcher;
import com.venus.vmping.VMPingClient;
import com.venus.vmping.config.ModConfig;
import com.venus.vmping.core.ClientContext;
import com.venus.vmping.core.PingData;
import com.venus.vmping.core.PingKind;
import com.venus.vmping.core.RoomKey;
import com.venus.vmping.gui.PingWheelScreen;
import com.venus.vmping.keybind.KeyBindings;
import com.venus.vmping.net.PingRelay;
import com.venus.vmping.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

/**
 * 客户端命令 {@code /vmping ...}，只在本地执行，不会发给服务器。
 *
 * <p>子命令：
 * <ul>
 *   <li>{@code status} —— 中继状态 / 房间 / 维度 / token / 标点数；</li>
 *   <li>{@code ping [kind]} —— 不打开轮盘，直接按准星方向标一个点（kind 缺省 default）；</li>
 *   <li>{@code open} —— 打开轮盘界面（等同按键）；</li>
 *   <li>{@code config reload} —— 重读 config.json 并重启中继；</li>
 *   <li>{@code debug} —— 打印诊断信息（房间键、后端地址、错误详情）；</li>
 *   <li>{@code help} —— 帮助。</li>
 * </ul>
 */
public final class PingCommand {

    private PingCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(PingCommand::build);
    }

    private static void build(CommandDispatcher<FabricClientCommandSource> dispatcher,
                              CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommands.literal("vmping")
                .executes(ctx -> help())
                .then(ClientCommands.literal("status").executes(ctx -> status()))
                .then(ClientCommands.literal("open").executes(ctx -> open()))
                .then(ClientCommands.literal("settings").executes(ctx -> settings()))
                .then(ClientCommands.literal("help").executes(ctx -> help()))
                .then(ClientCommands.literal("debug").executes(ctx -> debug()))
                .then(ClientCommands.literal("ping")
                        .executes(ctx -> ping(PingKind.DEFAULT))
                        .then(ClientCommands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> ping(PingKind.fromId(
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind"))))))
                .then(ClientCommands.literal("config")
                        .then(ClientCommands.literal("reload").executes(ctx -> configReload()))));
    }

    // ==================== 子命令 ====================

    private static int help() {
        ChatUtil.showMessage(Component.translatable("vmping.command.help.header"));
        ChatUtil.showMessage(Component.translatable("vmping.command.help.status"));
        ChatUtil.showMessage(Component.translatable("vmping.command.help.ping"));
        ChatUtil.showMessage(Component.translatable("vmping.command.help.open", KeyBindings.keyName()));
        ChatUtil.showMessage(Component.translatable("vmping.command.help.settings"));
        ChatUtil.showMessage(Component.translatable("vmping.command.help.config"));
        ChatUtil.showMessage(Component.translatable("vmping.command.help.debug"));
        return 1;
    }

    private static int status() {
        PingRelay relay = VMPingClient.relay();
        ModConfig config = ModConfig.get();
        String room = relay.roomId();
        ChatUtil.showMessage(Component.translatable("vmping.command.status.state",
                Component.translatable(stateKey(relay.state()))));
        ChatUtil.showMessage(Component.translatable("vmping.command.status.room",
                room.isBlank() ? Component.translatable("vmping.command.status.room.none") : Component.literal(room)));
        ChatUtil.showMessage(Component.translatable("vmping.command.status.dimension",
                Component.literal(relay.dimension().isBlank() ? "-" : relay.dimension())));
        ChatUtil.showMessage(Component.translatable("vmping.command.status.pings",
                Component.literal(String.valueOf(relay.store().size()))));
        ChatUtil.showMessage(Component.translatable("vmping.command.status.backend",
                Component.literal(config.apiBaseUrl.isBlank()
                        ? Component.translatable("vmping.command.status.backend.off").getString()
                        : config.apiBaseUrl)));
        if (!relay.lastErrorKey().isBlank()) {
            ChatUtil.showMessage(Component.translatable("vmping.command.status.last_error",
                    Component.translatable(relay.lastErrorKey())));
        }
        return 1;
    }

    private static int open() {
        if (!ModConfig.get().enabled) {
            ChatUtil.showMessage(Component.translatable("vmping.command.disabled"));
            return 0;
        }
        PingWheelScreen.open();
        return 1;
    }

    /** 打开设置界面（Mod Menu 没装时的入口）。 */
    private static int settings() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.gui.setScreen(new com.venus.vmping.gui.SettingsScreen(minecraft.gui.screen()));
        return 1;
    }

    private static int ping(PingKind kind) {
        PingRelay relay = VMPingClient.relay();
        if (!relay.online() && !ModConfig.get().degradeToLocal) {
            ChatUtil.showMessage(Component.translatable("vmping.status.local_only"));
            return 0;
        }
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        // 用准星方向做一次拾取（与轮盘相同的逻辑：方块/实体/视线终点）
        var hit = minecraft.player.pick(ModConfig.get().maxIconDistance, 1.0f, false);
        double x;
        double y;
        double z;
        java.util.UUID targetUuid = null;
        String targetName = null;
        if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY
                && hit instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            var entity = entityHit.getEntity();
            var pos = entity.position();
            x = pos.x;
            y = pos.y + entity.getBbHeight() * 0.5d;
            z = pos.z;
            targetUuid = entity.getUUID();
            targetName = entity.getName().getString();
        } else if (hit != null && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            var location = hit.getLocation();
            x = location.x;
            y = location.y;
            z = location.z;
        } else {
            var eye = minecraft.player.getEyePosition();
            var direction = minecraft.player.getViewVector(1.0f);
            var point = eye.add(direction.scale(Math.min(32d, ModConfig.get().maxIconDistance)));
            x = point.x;
            y = point.y;
            z = point.z;
        }
        PingData ping = relay.sendPing(x, y, z, kind, targetUuid, targetName);
        if (ping != null) {
            VMPingClient.getInstance().onLocalPing(ping);
        }
        return 1;
    }

    private static int configReload() {
        ModConfig loaded = ModConfig.load();
        VMPingClient.getInstance().applyConfig(loaded);
        ChatUtil.showMessage(Component.translatable("vmping.command.config.reloaded",
                Component.literal(ModConfig.configPath().toString())));
        return 1;
    }

    private static int debug() {
        PingRelay relay = VMPingClient.relay();
        ChatUtil.showRaw("state=" + relay.state()
                + " room=" + relay.roomId()
                + " dim=" + relay.dimension()
                + " pings=" + relay.store().size()
                + " lastSeq=" + relay.store().lastSeq()
                + " clockOffset=" + relay.store().clock().offsetMs() + "ms"
                + " errorKey=" + relay.lastErrorKey()
                + " error=" + relay.lastErrorDetail());
        ChatUtil.showRaw("rawServer=" + ClientContext.rawServerAddress()
                + " normalized=" + RoomKey.normalize(ClientContext.rawServerAddress())
                + " secure=" + ModConfig.get().secureBackend()
                + " config=" + ModConfig.configPath());
        return 1;
    }

    private static String stateKey(PingRelay.State state) {
        return switch (state) {
            case OFF -> "vmping.state.off";
            case AUTHENTICATING -> "vmping.state.authenticating";
            case ONLINE -> "vmping.state.online";
            case DEGRADED -> "vmping.state.degraded";
        };
    }
}
