package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.User;
import com.earth2me.essentials.utils.AdventureUtil;
import com.earth2me.essentials.utils.NumberUtil;
import org.bukkit.Server;

import java.util.Collections;
import java.util.List;

public class Commandbalance extends EssentialsCommand {
    public Commandbalance() {
        super("balance");
    }

    @Override
    protected void run(final Server server, final CommandSource sender, final String commandLabel, final String[] args) throws Exception {
        if (args.length == 0) {
            throw new NotEnoughArgumentsException();
        }

        final User target = getPlayer(server, args, 0, false, true);
        sender.sendTl("balanceOther", target.isHidden() ? target.getName() : target.getDisplayName(), AdventureUtil.parsed(NumberUtil.displayCurrencyExactly(target.getMoney(), ess)));
    }

    @Override
    public void run(final Server server, final User user, final String commandLabel, final String[] args) throws Exception {
        if (args.length == 1 && user.isAuthorized("essentials.balance.others")) {
            final User target = getPlayer(server, args, 0, true, true);
            user.sendTl("balanceOther", target.isHidden() ? target.getName() : target.getDisplayName(), AdventureUtil.parsed(NumberUtil.displayCurrencyExactly(target.getMoney(), ess)));
        } else if (args.length < 2) {
            user.sendTl("balance", AdventureUtil.parsed(NumberUtil.displayCurrencyExactly(user.getMoney(), ess)));
        } else {
            throw new NotEnoughArgumentsException();
        }
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final CommandSource sender, final String commandLabel, final String[] args) {
        if (args.length == 1 && sender.isAuthorized("essentials.balance.others")) {
            return getPlayers(server, sender);
        } else {
            return Collections.emptyList();
        }
    }
}
