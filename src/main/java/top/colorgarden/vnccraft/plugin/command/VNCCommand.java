package top.colorgarden.vnccraft.plugin.command;

import top.colorgarden.vnccraft.plugin.vnc.VNCScreenManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class VNCCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        VNCScreenManager mgr = VNCScreenManager.getInstance();
        if (args.length == 0) {
            sender.sendMessage("§e/vnc place [resW] [resH]");
            sender.sendMessage("§e/vnc remove <id>");
            sender.sendMessage("§e/vnc connect <id> <host> [port] [password]");
            sender.sendMessage("§e/vnc disconnect <id>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "place" -> {
                sender.sendMessage("§eUse the Fabric client (wooden shovel) to place screens.");
            }
            case "remove" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /vnc remove <id>"); return true; }
                int id = Integer.parseInt(args[1]);
                mgr.removeScreen(id);
                sender.sendMessage("§aScreen #" + id + " removed.");
            }
            case "connect" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /vnc connect <id> <host> [port] [password]"); return true; }
                int id = Integer.parseInt(args[1]);
                String host = args[2];
                int port = args.length > 3 ? Integer.parseInt(args[3]) : 5900;
                String pw = args.length > 4 ? args[4] : "";
                mgr.connectScreen(id, host, port, pw, 1920, 1080, 0, "");
                sender.sendMessage("§aConnecting screen #" + id + " to " + host + ":" + port);
            }
            case "disconnect" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /vnc disconnect <id>"); return true; }
                mgr.disconnectScreen(Integer.parseInt(args[1]));
                sender.sendMessage("§aDisconnected.");
            }
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }
}
