package net.swofty.type.skyblockgeneric.commands;

import net.minestom.server.command.builder.arguments.ArgumentGroup;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.skyblockgeneric.trading.TradeManager;
import net.swofty.type.skyblockgeneric.user.SkyBlockPlayer;

@CommandParameters(aliases = "trade",
        description = "Trade with another player",
        usage = "/trade <player name> | /trade accept <player name>",
        permission = Rank.DEFAULT,
        allowsConsole = false)
public class TradeCommand extends HypixelCommand {
    @Override
    public void registerUsage(MinestomCommand command) {
        ArgumentString playerArg = ArgumentType.String("player");
        ArgumentGroup acceptGroup = ArgumentType.Group("accept",
                ArgumentType.Literal("accept"),
                ArgumentType.String("player"));

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            sender.sendMessage("§cMissing arguments! Usage: /trade <player name>");
        });

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            SkyBlockPlayer player = (SkyBlockPlayer) sender;
            String targetName = context.get(playerArg);
            SkyBlockPlayer target = TradeManager.findPlayerByName(targetName);
            if (target == null) {
                player.sendMessage("§cThat player is not online!");
                return;
            }
            TradeManager.sendTradeRequest(player, target);
        }, playerArg);

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            SkyBlockPlayer player = (SkyBlockPlayer) sender;
            String targetName = context.get(acceptGroup).get("player");
            SkyBlockPlayer target = TradeManager.findPlayerByName(targetName);
            if (target == null) {
                player.sendMessage("§cThat player is not online!");
                return;
            }
            TradeManager.acceptTradeRequest(player, target);
        }, acceptGroup);
    }
}
