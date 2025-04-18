package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.User;
import com.earth2me.essentials.textreader.SimpleTextInput;
import com.earth2me.essentials.textreader.TextPager;
import com.earth2me.essentials.utils.AdventureUtil;
import com.earth2me.essentials.utils.EnumUtil;
import com.earth2me.essentials.utils.NumberUtil;
import com.earth2me.essentials.utils.VersionUtil;
import com.google.common.collect.Lists;
import net.essentialsx.api.v2.services.BalanceTop;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.command.BlockCommandSender;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.earth2me.essentials.I18n.tlLiteral;

public class Commandbalancetop extends EssentialsCommand {
    public static final int MINUSERS = 50;
    private static final int CACHETIME = 2 * 60 * 1000;
    private static SimpleTextInput cache = new SimpleTextInput();

    public Commandbalancetop() {
        super("balancetop");
    }

    private void outputCache(final CommandSource sender, final int page) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ess.getBalanceTop().getCacheAge());
        final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        final Runnable runnable = () -> {
            sender.sendTl("balanceTop", format.format(cal.getTime()));
            new TextPager(cache).showPage(Integer.toString(page), null, "balancetop", sender);
        };
        if (sender.getSender() instanceof BlockCommandSender) {
            ess.scheduleSyncDelayedTask(runnable);
        } else {
            runnable.run();
        }
    }

    @Override
    protected void run(final Server server, final CommandSource sender, final String commandLabel, final String[] args) throws Exception {
        int page = 0;
        boolean force = false;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (final NumberFormatException ex) {
                if (args[0].equalsIgnoreCase("force") && (!sender.isPlayer() || ess.getUser(sender.getPlayer()).isAuthorized("essentials.balancetop.force"))) {
                    force = true;
                }
            }
        }

        if (!force && ess.getBalanceTop().getCacheAge() > System.currentTimeMillis() - CACHETIME) {
            outputCache(sender, page);
            return;
        }

        // If there are less than 50 users in our usermap, there is no need to display a warning as these calculations should be done quickly
        if (ess.getUsers().getUserCount() > MINUSERS) {
            sender.sendTl("orderBalances", ess.getUsers().getUserCount());
        }

        ess.runTaskAsynchronously(new Viewer(sender, page, force));
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final CommandSource sender, final String commandLabel, final String[] args) {
        if (args.length == 1) {
            final List<String> options = Lists.newArrayList("1");
            if (!sender.isPlayer() || ess.getUser(sender.getPlayer()).isAuthorized("essentials.balancetop.force")) {
                options.add("force");
            }
            return options;
        } else {
            return Collections.emptyList();
        }
    }

    private class Viewer implements Runnable {
        private final transient CommandSource sender;
        private final transient int page;
        private final transient boolean force;

        Viewer(final CommandSource sender, final int page, final boolean force) {
            this.sender = sender;
            this.page = page;
            this.force = force;
        }

        @Override
        public void run() {
            if (ess.getSettings().isEcoDisabled()) {
                if (ess.getSettings().isDebug()) {
                    ess.getLogger().info("Internal economy functions disabled, aborting baltop.");
                }
                return;
            }

            final boolean fresh = force || ess.getBalanceTop().isCacheLocked() || ess.getBalanceTop().getCacheAge() <= System.currentTimeMillis() - CACHETIME;
            final CompletableFuture<Void> future = fresh ? ess.getBalanceTop().calculateBalanceTopMapAsync() : CompletableFuture.completedFuture(null);
            future.thenRun(() -> {
                if (fresh) {
                    final SimpleTextInput newCache = new SimpleTextInput();
                    newCache.getLines().add(AdventureUtil.miniToLegacy(tlLiteral("serverTotal", AdventureUtil.parsed(NumberUtil.displayCurrency(ess.getBalanceTop().getBalanceTopTotal(), ess)))));
                    int pos = 1;
                    for (final Map.Entry<UUID, BalanceTop.Entry> entry : ess.getBalanceTop().getBalanceTopCache().entrySet()) {
                        final BigDecimal balance = entry.getValue().getBalance();
                        final User user = ess.getUser(entry.getKey());

                        final Statistic PLAY_ONE_TICK = EnumUtil.getStatistic("PLAY_ONE_MINUTE", "PLAY_ONE_TICK");
                        final boolean offlineStatisticSupported = VersionUtil.getServerBukkitVersion().isHigherThanOrEqualTo(VersionUtil.v1_15_2_R01);
                        final long playtime;
                        if (user.getBase() == null || !user.getBase().isOnline()) {
                            if (offlineStatisticSupported) {
                                playtime = Bukkit.getServer().getOfflinePlayer(entry.getKey()).getStatistic(PLAY_ONE_TICK);
                            } else {
                                playtime = -1;
                            }
                        } else {
                            playtime = user.getBase().getStatistic(PLAY_ONE_TICK);
                        }
                        // Play time in seconds
                        final long playTimeSecs = Math.max(playtime / 20, 0);

                        // Checking if player meets the requirements of minimum balance and minimum playtime to be listed in baltop list
                        if ((ess.getSettings().showZeroBaltop() || balance.compareTo(BigDecimal.ZERO) > 0)
                                && balance.compareTo(ess.getSettings().getBaltopMinBalance()) >= 0 &&
                                // Skip playtime check for offline players on versions below 1.15.2
                                (playtime == -1 || playTimeSecs >= ess.getSettings().getBaltopMinPlaytime())) {
                            newCache.getLines().add(AdventureUtil.miniToLegacy(tlLiteral("balanceTopLine", pos, entry.getValue().getDisplayName(), AdventureUtil.parsed(NumberUtil.displayCurrency(balance, ess)))));
                        }
                        pos++;
                    }
                    cache = newCache;
                }
                outputCache(sender, page);
            });
        }
    }
}
