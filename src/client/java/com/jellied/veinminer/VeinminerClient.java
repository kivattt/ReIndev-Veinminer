package com.jellied.veinminer;

import com.fox2code.foxloader.loader.ClientMod;
import com.fox2code.foxloader.network.NetworkPlayer;
import com.fox2code.foxloader.registry.RegisteredItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.src.client.player.EntityPlayerSP;
import net.minecraft.src.game.block.*;
import net.minecraft.src.game.item.*;
import net.minecraft.src.game.level.World;

import java.util.ArrayList;

public class VeinminerClient extends Veinminer implements ClientMod {
    static final int MAX_VEINMINE_ITERATIONS = 10;
    final Minecraft MINECRAFT = Minecraft.getInstance();

    private static double getMagnitudeBetween(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private static boolean areCoordinatesDuplicate(ArrayList<ArrayList<Integer>> allCoords, ArrayList<Integer> coords) {
        for (int i = 0; i <= allCoords.size() - 1; i++) {
            ArrayList<Integer> theseCoords = allCoords.get(i);
            if (theseCoords.equals(coords)) {
                return true;
            }
        }
        return false;
    }

    private static void checkForBlock(ArrayList<ArrayList<Integer>> allCoords, World world, int targetBlockId, int x, int y, int z, int startX, int startY, int startZ, Integer totalIterations) {
        int blockId = world.getBlockId(x, y, z);
        if (blockId == targetBlockId) {
            if (totalIterations >= MAX_VEINMINE_ITERATIONS) {
                return;
            }

            if (Block.blocksList[blockId] instanceof BlockLog) {
                if ((world.getBlockMetadata(x, y, z) & 0x3) > 2) {
                    return; // Not a natural log
                }
            }


            ArrayList<Integer> coords = new ArrayList<>();
            coords.add(x);
            coords.add(y);
            coords.add(z);
            if (areCoordinatesDuplicate(allCoords, coords)) {
                return;
            }

            allCoords.add(coords);
            totalIterations++;

            // Check left, right, below, above, front, and back
            // I LOVE RECURSION!!!!!!!!!!!!!!!!!
            checkForBlock(allCoords, world, targetBlockId,x - 1, y, z, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId,x + 1, y, z, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId, x,y - 1, z, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId, x,y + 1, z, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId, x, y, z - 1, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId, x, y, z + 1, startX, startY, startZ, totalIterations);
        }
        else if (Block.blocksList[blockId] instanceof BlockLeavesBase) {

            // So we don't veinmine a whole damn forest.
            if (getMagnitudeBetween(startX, startZ, x, z) >= 4) {
                return;
            }

            ArrayList<Integer> coords = new ArrayList<>();
            coords.add(x);
            coords.add(y);
            coords.add(z);
            if (areCoordinatesDuplicate(allCoords, coords)) {
                return;
            }

            allCoords.add(coords);

            // This makes leaves act like a sort of conductor for veinmining logs without actually breaking the leaves.
            checkForBlock(allCoords, world, targetBlockId,x - 1, y, z, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId,x + 1, y, z, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId, x,y - 1, z, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId, x,y + 1, z, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId, x, y, z - 1, startX, startY, startZ, totalIterations);
            checkForBlock(allCoords, world, targetBlockId, x, y, z + 1, startX, startY, startZ, totalIterations);
        }
    }

    static ArrayList<ArrayList<Integer>> getAdjacentBlockCoords(World world, int targetBlockId, int startX, int startY, int startZ) {
        ArrayList<ArrayList<Integer>> allCoords = new ArrayList<>();
        checkForBlock(allCoords, world, targetBlockId, startX, startY, startZ, startX, startY, startZ, 0);
        return allCoords;
    }

    static boolean isItemTool(Item tool) {
        return (tool instanceof ItemToolPickaxe) || (tool instanceof ItemToolSpade) || (tool instanceof ItemToolAxe);
    }

    static boolean isBlockVeinmineable(Block block) {
        // I wish I could write a neat little loop for this instead of a chunky
        // block of or's
        return block instanceof BlockOre ||
                block instanceof BlockOreCoal ||
                block instanceof BlockOreMetal ||
                block instanceof BlockGravel ||
                block instanceof BlockLog;
    }


    // Main methods
    public void veinmine(EntityPlayerSP plr, int x, int y, int z) {
        World world = plr.worldObj;
        if (!plr.isHoldingTool()) {
            // without this check we get a NullPointerException if the player breaks a block with their fist
            return;
        }

        ItemStack stack = plr.getCurrentEquippedItem();
        Item tool = stack.getItem();

        if (!plr.isSneaking() || !isItemTool(tool)) {
            return;
        }

        int blockId = world.getBlockId(x, y, z);
        Block block = Block.blocksList[blockId];
        if (!isBlockVeinmineable(block) || !tool.canHarvestBlock(block)) {
            return;
        }

        if (block instanceof BlockLog) {
            int meta = world.getBlockMetadata(x, y, z);
            // no idea what (meta 0x3) means, it's just how my decompiler displays the code that
            // checks for a naturally spawned log.
            if ((meta & 0x3) > 2) {
                // In any case, if it's less than 2, that means it's a naturally spawned log
                // Otherwise, it's a player placed log, and we don't wanna veinmine a whole ass house.

                return;
            }
        }

        world.removeBlockTileEntity(x, y, z);
        ArrayList<ArrayList<Integer>> coordsToVeinmine = getAdjacentBlockCoords(world, blockId, x, y, z);
        for (int i = 0; i <= coordsToVeinmine.size() - 1; i++) {
            int currentDamage = stack.getItemDamage();
            if (currentDamage >= stack.getMaxDamage()) {
                break; // Preserve the tool if it's about to break.
            }

            ArrayList<Integer> coords = coordsToVeinmine.get(i);

            int thisX = (coords.get(0));
            int thisY = (coords.get(1));
            int thisZ = (coords.get(2));
            int metadata = world.getBlockMetadata(thisX, thisY, thisZ);
            int thisBlockId = world.getBlockId(thisX, thisY, thisZ);

            if (thisBlockId != blockId) {
                continue; // For leaf edge cases.
            }

            block.harvestBlock(world, plr, thisX, thisY, thisZ, metadata);
            block.onBlockDestroyedByPlayer(world, thisX, thisY, thisZ, metadata);
            stack.onDestroyBlock(blockId, thisX, thisY, thisZ, plr);
            world.playAuxSFX(2001, x, y, z, blockId + metadata * 65536);
            world.setBlockWithNotify(thisX, thisY, thisZ, 0);
        }
    }

    public boolean onPlayerBreakBlock(NetworkPlayer plr, RegisteredItemStack stack, int x, int y, int z, int facing, boolean wasCancelled) {
        boolean isSingleplayer = !MINECRAFT.isMultiplayerWorld();

        if (isSingleplayer) {
            veinmine(MINECRAFT.thePlayer, x, y, z);
        }

        return false;
    }

    public void onInit() {
        System.out.println("Veinminer client initialized.");
    }
}
