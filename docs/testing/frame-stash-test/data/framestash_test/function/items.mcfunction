# Clears your inventory and fills the main inventory (not the hotbar) with test items.
clear @s
item replace entity @s inventory.0 with minecraft:stone 64
item replace entity @s inventory.1 with minecraft:stone 64
item replace entity @s inventory.2 with minecraft:iron_ingot 20
item replace entity @s inventory.3 with minecraft:oak_log 5
item replace entity @s inventory.4 with minecraft:cobblestone 40
item replace entity @s inventory.5 with minecraft:redstone 30
item replace entity @s inventory.6 with minecraft:glowstone_dust 7
item replace entity @s inventory.7 with minecraft:shulker_box
item replace entity @s inventory.8 with minecraft:rotten_flesh 12
item replace entity @s inventory.9 with minecraft:string 8
item replace entity @s inventory.10 with minecraft:apple 10
item replace entity @s hotbar.0 with minecraft:torch 4
item replace entity @s hotbar.1 with minecraft:stone 16
tellraw @s {"text":"Items ready. Run /stash: the hotbar (torches, 16 stone) should stay. Then /restock for the torches.","color":"green"}
