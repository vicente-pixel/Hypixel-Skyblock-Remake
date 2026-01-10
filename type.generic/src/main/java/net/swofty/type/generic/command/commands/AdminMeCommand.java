package net.swofty.type.generic.command.commands;

import net.minestom.server.utils.mojang.MojangUtils;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.data.HypixelDataHandler;
import net.swofty.type.generic.data.datapoints.DatapointRank;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CommandParameters(aliases = "forceadmin",
        description = "Literally just gives me admin",
        usage = "/adminme",
        permission = Rank.DEFAULT,
        allowsConsole = false)
public class AdminMeCommand extends HypixelCommand {

    private static final List<String> ADMIN_LIST = List.of(
            "Swofty",
            "Foodzz",
            "Hamza_dev",
            "ItzKatze"
    );

    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;

            HypixelPlayer player = (HypixelPlayer) sender;
            player.getDataHandler().get(HypixelDataHandler.Data.RANK, DatapointRank.class).setValue(Rank.STAFF);

            sender.sendMessage("§aSuccessfully set rank to " + Rank.STAFF.getPrefix() + "§a.");
        });
    }
}
