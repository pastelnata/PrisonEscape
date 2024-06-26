package net.tiagofar78.prisonescape.game.prisonbuilding;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;

import net.tiagofar78.prisonescape.PrisonEscape;
import net.tiagofar78.prisonescape.bukkit.BukkitScheduler;
import net.tiagofar78.prisonescape.bukkit.BukkitTeleporter;
import net.tiagofar78.prisonescape.bukkit.BukkitWorldEditor;
import net.tiagofar78.prisonescape.game.Prisioner;
import net.tiagofar78.prisonescape.game.PrisonEscapeGame;
import net.tiagofar78.prisonescape.game.PrisonEscapePlayer;
import net.tiagofar78.prisonescape.managers.ConfigManager;
import net.tiagofar78.prisonescape.managers.GameManager;

import org.bukkit.Material;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Helicopter {

    private static final String HELICOPTER_SCHEM_NAME = "helicopter.schem";
    private static final int TICKS_PER_SECOND = 20;

    private PrisonEscapeLocation _upperLocation;
    private PrisonEscapeLocation _lowerLocation;
    private List<Prisioner> _players = new ArrayList<>();
    private boolean _isOnGround = false;

    protected Helicopter(PrisonEscapeLocation upperLocation, PrisonEscapeLocation lowerLocation) {
        _upperLocation = upperLocation;
        _lowerLocation = lowerLocation;
    }

    public boolean contains(PrisonEscapeLocation location) {
        int x = location.getX();
        int y = location.getY();
        int z = location.getZ();

        return _lowerLocation.getX() <= x && x <= _upperLocation.getX() && _lowerLocation.getY() <= y &&
                y <= _upperLocation.getY() && _lowerLocation.getZ() <= z && z <= _upperLocation.getZ();
    }

    public boolean isOnGround() {
        return _isOnGround;
    }

    private void buildHelicopter() {
        _isOnGround = true;
        placeOnWorld();
    }

    private void destroyHelicopter() {
        _isOnGround = false;
        removeFromWorld();
    }

    public void call() {
        int helicopterLandDelay = ConfigManager.getInstance().getHelicopterSpawnDelay();
        BukkitScheduler.runSchedulerLater(new Runnable() {

            @Override
            public void run() {
                land();
            }
        }, helicopterLandDelay * TICKS_PER_SECOND);
    }

    private void land() {
        buildHelicopter();

        int helicopterDepartureDelay = ConfigManager.getInstance().getHelicopterDepartureDelay();
        BukkitScheduler.runSchedulerLater(new Runnable() {

            @Override
            public void run() {
                if (isOnGround()) {
                    departed();
                }
            }

        }, helicopterDepartureDelay * TICKS_PER_SECOND);
    }

    public void departed() {
        destroyHelicopter();

        PrisonEscapeGame game = GameManager.getGame();
        for (Prisioner player : _players) {
            game.playerEscaped(player);
        }

        _players.clear();
    }

    public void click(PrisonEscapePlayer player, PrisonEscapeLocation exitLocation, PrisonEscapeLocation joinLocation) {
        if (!isOnGround()) {
            return;
        }

        if (player.isPrisioner()) {
            prisionerClicked((Prisioner) player, joinLocation);
            return;
        }

        policeClicked(exitLocation);
    }

    private void prisionerClicked(Prisioner player, PrisonEscapeLocation joinLocation) {
        if (_players.contains(player)) {
            return;
        }

        _players.add(player);
        BukkitTeleporter.teleport(player, joinLocation);
    }

    private void policeClicked(PrisonEscapeLocation exitLocation) {
        destroyHelicopter();

        for (PrisonEscapePlayer player : _players) {
            BukkitTeleporter.teleport(player, exitLocation);
        }

        _players.clear();
    }

    private void placeOnWorld() {
        File file = new File(PrisonEscape.getPrisonEscape().getDataFolder(), HELICOPTER_SCHEM_NAME);
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        try {

            ClipboardReader reader = format.getReader(new FileInputStream(file));
            Clipboard clipboard = reader.read();

            World world = BukkitAdapter.adapt(BukkitWorldEditor.getWorld());
            EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(world).build();
            Operation operation = new ClipboardHolder(clipboard).createPaste(editSession).to(
                    BlockVector3.at(_lowerLocation.getX(), _lowerLocation.getY(), _lowerLocation.getZ())
            ).ignoreAirBlocks(false).build();
            Operations.complete(operation);
            editSession.commit();
            editSession.close();
        } catch (IOException | WorldEditException e) {
            e.printStackTrace();
        }
    }

    private void removeFromWorld() {
        World world = BukkitAdapter.adapt(BukkitWorldEditor.getWorld());
        BlockVector3 min = BlockVector3.at(_lowerLocation.getX(), _lowerLocation.getY(), _lowerLocation.getZ());
        BlockVector3 max = BlockVector3.at(_upperLocation.getX(), _upperLocation.getY(), _upperLocation.getZ());
        CuboidRegion selection = new CuboidRegion(world, min, max);

        BlockState air = BukkitAdapter.adapt(Material.AIR.createBlockData());
        EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(world).build();
        try {
            editSession.setBlocks(selection, air);
            editSession.commit();
            editSession.close();
        } catch (MaxChangedBlocksException e) {
            e.printStackTrace();
        }
    }

}
