package com.earth2me.essentials.economy.vault;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.OfflinePlayerStub;
import com.earth2me.essentials.api.NoLoanPermittedException;
import com.earth2me.essentials.api.UserDoesNotExistException;
import com.earth2me.essentials.config.EssentialsUserConfiguration;
import com.earth2me.essentials.utils.AdventureUtil;
import com.earth2me.essentials.utils.NumberUtil;
import com.earth2me.essentials.utils.StringUtil;
import com.google.common.base.Charsets;
import net.ess3.api.MaxMoneyException;
import com.github.adminoid.vault.economy.Economy;
import com.github.adminoid.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A goddamn Vault adapter, what more do you want?
 * Provides access to the EssentialsX economy for plugins that use the Vault API.
 * <p>
 * Developer note: for accessing Essentials/Vault economy functions from EssentialsX code, see
 * {@link com.earth2me.essentials.User}.
 */
public class VaultEconomyProvider implements Economy {
    private static final String WARN_NPC_RECREATE_1 = "Account creation was requested for NPC user {0}, but an account file with UUID {1} already exists.";
    private static final String WARN_NPC_RECREATE_2 = "Essentials will create a new account as requested by the other plugin, but this is almost certainly a bug and should be reported.";

    private final Essentials ess;

    public VaultEconomyProvider(Essentials essentials) {
        this.ess = essentials;
    }

    @Override
    public boolean isEnabled() {
        return ess.isEnabled();
    }

    @Override
    public String getName() {
        return "EssentialsX Economy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return -1;
    }

    @Override
    public String format(BigDecimal amount) {
        return AdventureUtil.miniToLegacy(NumberUtil.displayCurrency(amount, ess));
    }

    @Override
    public String currencyNamePlural() {
        return currencyNameSingular();
    }

    @Override
    public String currencyNameSingular() {
        return ess.getSettings().getCurrencySymbol();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean hasAccount(String playerName) {
        if (com.earth2me.essentials.api.Economy.playerExists(playerName)) {
            return true;
        }
        // We may not have the player name in the usermap, let's double check an NPC account with this name doesn't exist.
        return com.earth2me.essentials.api.Economy.playerExists(UUID.nameUUIDFromBytes(("NPC:" + StringUtil.safeString(playerName)).getBytes(Charsets.UTF_8)));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return com.earth2me.essentials.api.Economy.playerExists(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @SuppressWarnings("deprecation")
    @Override
    public BigDecimal getBalance(String playerName) {
        try {
            return com.earth2me.essentials.api.Economy.getMoneyExact(playerName);
        } catch (UserDoesNotExistException e) {
            createPlayerAccount(playerName);
            return ess.getSettings().getStartingBalance();
        }
    }

    @Override
    public BigDecimal getBalance(OfflinePlayer player) {
        try {
            return com.earth2me.essentials.api.Economy.getMoneyExact(player.getUniqueId());
        } catch (UserDoesNotExistException e) {
            createPlayerAccount(player);
            return ess.getSettings().getStartingBalance();
        }
    }

    @Override
    public BigDecimal getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public BigDecimal getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean has(String playerName, BigDecimal amount) {
        try {
            return com.earth2me.essentials.api.Economy.hasEnough(playerName, amount);
        } catch (UserDoesNotExistException e) {
            return false;
        }
    }

    @Override
    public boolean has(OfflinePlayer player, BigDecimal amount) {
        try {
            return com.earth2me.essentials.api.Economy.hasEnough(player.getUniqueId(), amount);
        } catch (UserDoesNotExistException e) {
            return false;
        }
    }

    @Override
    public boolean has(String playerName, String worldName, BigDecimal amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, BigDecimal amount) {
        return has(player, amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse withdrawPlayer(String playerName, BigDecimal amount) {
        if (playerName == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Player name cannot be null!");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative funds!");
        }

        try {
            com.earth2me.essentials.api.Economy.subtract(playerName, amount);
            return new EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (UserDoesNotExistException e) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "User does not exist!");
        } catch (NoLoanPermittedException e) {
            return new EconomyResponse(BigDecimal.ZERO, getBalance(playerName), EconomyResponse.ResponseType.FAILURE, "Loan was not permitted!");
        } catch (MaxMoneyException e) {
            return new EconomyResponse(BigDecimal.ZERO, getBalance(playerName), EconomyResponse.ResponseType.FAILURE, "User goes over maximum money limit!");
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, BigDecimal amount) {
        if (player == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Player cannot be null!");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative funds!");
        }

        try {
            com.earth2me.essentials.api.Economy.subtract(player.getUniqueId(), amount);
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (UserDoesNotExistException e) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "User does not exist!");
        } catch (NoLoanPermittedException e) {
            return new EconomyResponse(BigDecimal.ZERO, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Loan was not permitted!");
        } catch (MaxMoneyException e) {
            return new EconomyResponse(BigDecimal.ZERO, getBalance(player), EconomyResponse.ResponseType.FAILURE, "User goes over maximum money limit!");
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, BigDecimal amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, BigDecimal amount) {
        return withdrawPlayer(player, amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse depositPlayer(String playerName, BigDecimal amount) {
        if (playerName == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Player name can not be null.");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative funds");
        }

        try {
            com.earth2me.essentials.api.Economy.add(playerName, amount);
            return new EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (UserDoesNotExistException e) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "User does not exist!");
        } catch (NoLoanPermittedException e) {
            return new EconomyResponse(BigDecimal.ZERO, getBalance(playerName), EconomyResponse.ResponseType.FAILURE, "Loan was not permitted!");
        } catch (MaxMoneyException e) {
            return new EconomyResponse(BigDecimal.ZERO, getBalance(playerName), EconomyResponse.ResponseType.FAILURE, "User goes over maximum money limit!");
        }
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, BigDecimal amount) {
        if (player == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Player can not be null.");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative funds");
        }

        try {
            com.earth2me.essentials.api.Economy.add(player.getUniqueId(), amount);
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (UserDoesNotExistException e) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "User does not exist!");
        } catch (NoLoanPermittedException e) {
            return new EconomyResponse(BigDecimal.ZERO, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Loan was not permitted!");
        } catch (MaxMoneyException e) {
            return new EconomyResponse(BigDecimal.ZERO, getBalance(player), EconomyResponse.ResponseType.FAILURE, "User goes over maximum money limit!");
        }
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, BigDecimal amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, BigDecimal amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        if (hasAccount(playerName)) {
            return false;
        }
        // Assume we're creating an NPC here? If not, it's a lost cause anyway!
        return com.earth2me.essentials.api.Economy.createNPC(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (hasAccount(player)) {
            return false;
        }

        // String based UUIDs are version 3 and are used for NPC and OfflinePlayers
        // Citizens uses v2 UUIDs, yeah I don't know either!
        if (player.getUniqueId().version() == 3 || player.getUniqueId().version() == 2) {
            final File folder = new File(ess.getDataFolder(), "userdata");
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    throw new RuntimeException("Error while creating userdata directory!");
                }
            }
            final File npcFile = new File(folder, player.getUniqueId() + ".yml");
            if (npcFile.exists()) {
                ess.getLogger().log(Level.SEVERE, MessageFormat.format(WARN_NPC_RECREATE_1, player.getName(), player.getUniqueId().toString()), new RuntimeException());
                ess.getLogger().log(Level.SEVERE, WARN_NPC_RECREATE_2);
            }
            final EssentialsUserConfiguration npcConfig = new EssentialsUserConfiguration(player.getName(), player.getUniqueId(), npcFile);
            npcConfig.load();
            npcConfig.setProperty("npc", true);
            npcConfig.setProperty("last-account-name", player.getName());
            npcConfig.setProperty("money", ess.getSettings().getStartingBalance());
            npcConfig.blockingSave();
            // This will load the NPC into the UserMap + UUID cache
            ess.getUsers().addCachedNpcName(player.getUniqueId(), player.getName());
            ess.getUsers().getUser(player.getUniqueId());
            return true;
        }

        // Loading a v4 UUID that we somehow didn't track, mark it as a normal player and hope for the best, vault sucks :/
        if (ess.getSettings().isDebug()) {
            ess.getLogger().info("Vault requested a player account creation for a v4 UUID: " + player);
        }

        final Player userPlayer;
        if (player instanceof Player) {
            userPlayer = (Player) player;
        } else {
            final OfflinePlayerStub essPlayer = new OfflinePlayerStub(player.getUniqueId(), ess.getServer());
            essPlayer.setName(player.getName());
            userPlayer = essPlayer;
        }
        ess.getUsers().getUser(userPlayer);
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse bankHas(String name, BigDecimal amount) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, BigDecimal amount) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse bankDeposit(String name, BigDecimal amount) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "EssentialsX does not support bank accounts!");
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }
}
