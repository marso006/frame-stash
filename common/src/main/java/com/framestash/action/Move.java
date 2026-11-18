package com.framestash.action;

/**
 * One planned transfer between a player inventory slot and a container slot.
 *
 * @param playerSlot    slot in the player's inventory (0-8 hotbar, 9-35 main)
 * @param target        index into the target list
 * @param targetSlot    slot in the target container
 * @param count         items to move
 * @param intoContainer true for player to container (stash), false for container to player (restock)
 */
public record Move(int playerSlot, int target, int targetSlot, int count, boolean intoContainer) {}
