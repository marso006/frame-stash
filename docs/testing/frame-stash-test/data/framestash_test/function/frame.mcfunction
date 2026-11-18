# Summons an item frame in the block you run this from.
# Usage: execute positioned <x> <y> <z> run function framestash_test:frame {type:"item_frame",item:"stone",facing:3}
# Item frames in 1.21.10 need an absolute block_pos, so the position is read from a marker first.
execute align xyz run summon minecraft:marker ~ ~ ~ {Tags:["fstest_pos"]}
execute store result storage framestash_test:pos x int 1 run data get entity @e[type=minecraft:marker,tag=fstest_pos,limit=1] Pos[0]
execute store result storage framestash_test:pos y int 1 run data get entity @e[type=minecraft:marker,tag=fstest_pos,limit=1] Pos[1]
execute store result storage framestash_test:pos z int 1 run data get entity @e[type=minecraft:marker,tag=fstest_pos,limit=1] Pos[2]
kill @e[type=minecraft:marker,tag=fstest_pos]
$data modify storage framestash_test:pos type set value "$(type)"
$data modify storage framestash_test:pos item set value "$(item)"
$data modify storage framestash_test:pos facing set value $(facing)
function framestash_test:frame_summon with storage framestash_test:pos
