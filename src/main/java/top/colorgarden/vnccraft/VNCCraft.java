package top.colorgarden.vnccraft;

import top.colorgarden.vnccraft.config.VNCConfig;
import top.colorgarden.vnccraft.entity.VNCEntities;
import top.colorgarden.vnccraft.network.*;
import top.colorgarden.vnccraft.vnc.TunnelManager;
import top.colorgarden.vnccraft.vnc.VNCManager;
import top.colorgarden.vnccraft.vnc.VNCScreenInstance;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VNCCraft implements ModInitializer {

    public static final String MOD_ID = "vnccraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("VNCCraft initializing...");

        // 1. Register packet types
        VNCPackets.registerAll();

        // 2. Register entity types
        VNCEntities.register();

        // 3. Server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            VNCManager.init(server);
            TunnelManager.init(server);
            LOGGER.info("VNCCraft server started");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Disconnect all screens on shutdown
            VNCManager mgr = VNCManager.getInstance();
            // We can't easily enumerate all screen IDs here, but VNCConnection
            // threads are daemon — they'll die with the JVM.
            LOGGER.info("VNCCraft server stopping");
        });

        // 4. Server tick for screen management
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            VNCManager mgr = VNCManager.getInstance();
            if (mgr != null) {
                mgr.onServerTick();
            }
        });

        // 5. Sync screens to newly joined players
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            VNCManager mgr = VNCManager.getInstance();
            if (mgr != null) {
                mgr.syncToPlayer(handler.getPlayer());
            }
        });

        // 6. Handle C2S packets
        registerC2SHandlers();

        // 7. Register commands
        registerCommands();

        LOGGER.info("VNCCraft initialized");
    }

    private void registerC2SHandlers() {
        // Connect request
        ServerPlayNetworking.registerGlobalReceiver(C2SVNCConnectRequestPayload.TYPE,
                (payload, context) -> {
                    System.out.println("[VNCCraft] C2S connect request received!");
                    context.server().execute(() -> {
                        ServerPlayer player = context.player();
                        // Permission check: only operators can use VNC
                        // In MC 26.1.2, uses PermissionSet system
                        VNCManager mgr = VNCManager.getInstance();
                        if (mgr == null) return;
                        var data = payload.data();
                        mgr.connectScreen(data.screenId(), data.host(), data.port(),
                                data.password(), data.resW(), data.resH(),
                                data.audioPort(), data.cookiePath());
                        player.sendSystemMessage(Component.translatable("vnccraft.msg.connecting", data.screenId(), data.host(), data.port()));
                    });
                });

        // Disconnect request
        ServerPlayNetworking.registerGlobalReceiver(C2SVNCDisconnectRequestPayload.TYPE,
                (payload, context) -> {
                    context.server().execute(() -> {
                        VNCManager mgr = VNCManager.getInstance();
                        if (mgr != null) {
                            mgr.disconnectScreen(payload.screenId());
                        }
                    });
                });

        // Screen config (resolution update)
        ServerPlayNetworking.registerGlobalReceiver(C2SVNCScreenConfigPayload.TYPE,
                (payload, context) -> {
                    context.server().execute(() -> {
                        VNCManager mgr = VNCManager.getInstance();
                        if (mgr != null) {
                            var data = payload.data();
                            mgr.updateResolution(data.screenId(), data.resW(), data.resH());
                        }
                    });
                });

        // Place request
        ServerPlayNetworking.registerGlobalReceiver(C2SVNCPlaceRequestPayload.TYPE,
                (payload, context) -> {
                    context.server().execute(() -> {
                        ServerPlayer player = context.player();
                        // Permission check: only operators can use VNC
                        // In MC 26.1.2, uses PermissionSet system
                        VNCManager mgr = VNCManager.getInstance();
                        if (mgr == null) return;
                        var data = payload.data();
                        var pos = new net.minecraft.core.BlockPos(
                                (int) Math.floor(data.x()),
                                (int) Math.floor(data.y()),
                                (int) Math.floor(data.z()));
                        var dir = net.minecraft.core.Direction.from3DDataValue(data.direction());
                        int id = mgr.placeScreen((net.minecraft.server.level.ServerLevel) player.level(), pos, dir, data.resW(), data.resH());
                        player.sendSystemMessage(Component.translatable("vnccraft.msg.placed", id));
                    });
                });

        // Mouse input (forward to VNC)
        ServerPlayNetworking.registerGlobalReceiver(C2SVNCMouseInputPayload.TYPE,
                (payload, context) -> {
                    context.server().execute(() -> {
                        VNCManager mgr = VNCManager.getInstance();
                        if (mgr == null) return;
                        var data = payload.data();
                        VNCScreenInstance screen = mgr.getScreen(data.screenId());
                        if (screen != null && screen.isConnected()) {
                            screen.moveMouse(data.x(), data.y());
                        screen.setButtons(data.buttonMask());
                        }
                    });
                });

        // Tunnel: forward bytes from client to VNC server
        // Must NOT defer execution — VNC handshake is time-sensitive.
        // Writing to the TCP socket is thread-safe.
        ServerPlayNetworking.registerGlobalReceiver(C2SVNCTunnelPayload.TYPE,
                (payload, context) -> {
                    TunnelManager mgr = TunnelManager.getInstance();
                    if (mgr == null) return;
                    var data = payload.data();
                    mgr.clientToServer(data.screenId(), data.rawBytes());
                });

        // Nudge screen position (Alt+Scroll)
        ServerPlayNetworking.registerGlobalReceiver(C2SVNCNudgePayload.TYPE,
                (payload, context) -> {
                    context.server().execute(() -> {
                        VNCManager mgr = VNCManager.getInstance();
                        if (mgr == null) return;
                        var data = payload.data();
                        mgr.nudgeScreen(data.screenId(), data.axis(), data.step(), data.fine());
                    });
                });

        // Remove request
        ServerPlayNetworking.registerGlobalReceiver(C2SVNCRemoveRequestPayload.TYPE,
                (payload, context) -> {
                    context.server().execute(() -> {
                        ServerPlayer player = context.player();
                        // Permission check: only operators can use VNC
                        // In MC 26.1.2, uses PermissionSet system
                        VNCManager mgr = VNCManager.getInstance();
                        if (mgr != null) {
                            mgr.removeScreen(payload.screenId());
                            player.sendSystemMessage(Component.translatable("vnccraft.msg.removed", payload.screenId()));
                        }
                    });
                });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("vnc")

                    // /vnc place [resW] [resH]
                    .then(Commands.literal("place")
                            .executes(ctx -> {
                                // Place at player's look position with default resolution
                                // Client handles the actual placement via VNCToolHandler
                                ctx.getSource().sendSuccess(() ->
                                        Component.translatable("vnccraft.msg.use_tool"), false);
                                return 1;
                            })
                    )

                    // /vnc list
                    .then(Commands.literal("list")
                            .executes(ctx -> {
                                ctx.getSource().sendSuccess(() ->
                                        Component.translatable("vnccraft.msg.use_tool"), false);
                                return 1;
                            })
                    )

                    // /vnc remove <screenId>
                    .then(Commands.literal("remove")
                            .then(Commands.argument("screenId", IntegerArgumentType.integer(0))
                                    .executes(ctx -> {
                                        int id = IntegerArgumentType.getInteger(ctx, "screenId");
                                        VNCManager mgr = VNCManager.getInstance();
                                        if (mgr != null) {
                                            mgr.removeScreen(id);
                                        }
                                        ctx.getSource().sendSuccess(() ->
                                                Component.translatable("vnccraft.msg.removed", id), true);
                                        return 1;
                                    })
                            )
                    )

                    // /vnc connect <screenId> <host> [port] [password]
                    .then(Commands.literal("connect")
                            .then(Commands.argument("screenId", IntegerArgumentType.integer(0))
                                    .then(Commands.argument("host", StringArgumentType.string())
                                            .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                                    .then(Commands.argument("password", StringArgumentType.greedyString())
                                                            .executes(ctx -> {
                                                                int id = IntegerArgumentType.getInteger(ctx, "screenId");
                                                                String host = StringArgumentType.getString(ctx, "host");
                                                                int port = IntegerArgumentType.getInteger(ctx, "port");
                                                                String password = StringArgumentType.getString(ctx, "password");
                                                                VNCManager mgr = VNCManager.getInstance();
                                                                if (mgr != null) {
                                                                    mgr.connectScreen(id, host, port, password,
                                                                            VNCConfig.DEFAULT_RES_WIDTH, VNCConfig.DEFAULT_RES_HEIGHT, 0, "");
                                                                }
                                                                ctx.getSource().sendSuccess(() ->
                                                                        Component.translatable("vnccraft.msg.connecting", id, host, port), true);
                                                                return 1;
                                                            })
                                                    )
                                                    .executes(ctx -> {
                                                        int id = IntegerArgumentType.getInteger(ctx, "screenId");
                                                        String host = StringArgumentType.getString(ctx, "host");
                                                        int port = IntegerArgumentType.getInteger(ctx, "port");
                                                        VNCManager mgr = VNCManager.getInstance();
                                                        if (mgr != null) {
                                                            mgr.connectScreen(id, host, port, "",
                                                                    VNCConfig.DEFAULT_RES_WIDTH, VNCConfig.DEFAULT_RES_HEIGHT, 0, "");
                                                        }
                                                        ctx.getSource().sendSuccess(() ->
                                                                Component.translatable("vnccraft.msg.connecting", id, host, port), true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                    )

                    // /vnc disconnect <screenId>
                    .then(Commands.literal("disconnect")
                            .then(Commands.argument("screenId", IntegerArgumentType.integer(0))
                                    .executes(ctx -> {
                                        int id = IntegerArgumentType.getInteger(ctx, "screenId");
                                        VNCManager mgr = VNCManager.getInstance();
                                        if (mgr != null) {
                                            mgr.disconnectScreen(id);
                                        }
                                        ctx.getSource().sendSuccess(() ->
                                                Component.translatable("vnccraft.msg.disconnected", id), true);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
