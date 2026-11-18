# Builds a row of labelled containers 4 blocks north of you. Run while standing on flat ground.
function framestash_test:reset
fill ~-7 ~-1 ~-6 ~12 ~-1 ~-2 minecraft:smooth_stone
fill ~-7 ~ ~-6 ~12 ~3 ~-3 minecraft:air

# Test 1: three chests, one item each
setblock ~-6 ~ ~-4 minecraft:chest[facing=south]
setblock ~-5 ~ ~-4 minecraft:chest[facing=south]
setblock ~-4 ~ ~-4 minecraft:chest[facing=south]
execute positioned ~-6 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"stone",facing:3}
execute positioned ~-5 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"iron_ingot",facing:3}
execute positioned ~-4 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"oak_log",facing:3}
summon minecraft:text_display ~-5 ~1.6 ~-4 {Tags:["fstest"],billboard:"center",text:{"text":"Test 1: stone / iron / logs each go to their own chest"}}

# Test 2: double chest, frame on the right half only
setblock ~-2 ~ ~-4 minecraft:chest[facing=south,type=right]
setblock ~-1 ~ ~-4 minecraft:chest[facing=south,type=left]
execute positioned ~-2 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"cobblestone",facing:3}
summon minecraft:text_display ~-1.5 ~1.6 ~-4 {Tags:["fstest"],billboard:"center",text:{"text":"Test 2: the double chest takes cobblestone"}}

# Test 3: barrel and shulker box (the shulker box must refuse the shulker box item)
setblock ~1 ~ ~-4 minecraft:barrel[facing=south]
setblock ~2 ~ ~-4 minecraft:shulker_box
execute positioned ~1 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"redstone",facing:3}
execute positioned ~2 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"glowstone_dust",facing:3}
execute positioned ~3 ~ ~-4 run function framestash_test:frame {type:"item_frame",item:"shulker_box",facing:5}
summon minecraft:text_display ~1.5 ~1.6 ~-4 {Tags:["fstest"],billboard:"center",text:{"text":"Test 3: barrel takes redstone, shulker takes glowstone but NOT the shulker box"}}

# Test 4: overflow. The stone chest from test 1 has one free slot; the glow-frame chest takes the rest
fill ~-6 ~ ~-4 ~-6 ~ ~-4 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:dirt",count:64},{Slot:1b,id:"minecraft:dirt",count:64},{Slot:2b,id:"minecraft:dirt",count:64},{Slot:3b,id:"minecraft:dirt",count:64},{Slot:4b,id:"minecraft:dirt",count:64},{Slot:5b,id:"minecraft:dirt",count:64},{Slot:6b,id:"minecraft:dirt",count:64},{Slot:7b,id:"minecraft:dirt",count:64},{Slot:8b,id:"minecraft:dirt",count:64},{Slot:9b,id:"minecraft:dirt",count:64},{Slot:10b,id:"minecraft:dirt",count:64},{Slot:11b,id:"minecraft:dirt",count:64},{Slot:12b,id:"minecraft:dirt",count:64},{Slot:13b,id:"minecraft:dirt",count:64},{Slot:14b,id:"minecraft:dirt",count:64},{Slot:15b,id:"minecraft:dirt",count:64},{Slot:16b,id:"minecraft:dirt",count:64},{Slot:17b,id:"minecraft:dirt",count:64},{Slot:18b,id:"minecraft:dirt",count:64},{Slot:19b,id:"minecraft:dirt",count:64},{Slot:20b,id:"minecraft:dirt",count:64},{Slot:21b,id:"minecraft:dirt",count:64},{Slot:22b,id:"minecraft:dirt",count:64},{Slot:23b,id:"minecraft:dirt",count:64},{Slot:24b,id:"minecraft:dirt",count:64},{Slot:25b,id:"minecraft:dirt",count:64}]} replace
setblock ~5 ~ ~-4 minecraft:chest[facing=south]
execute positioned ~5 ~ ~-3 run function framestash_test:frame {type:"glow_item_frame",item:"stone",facing:3}
summon minecraft:text_display ~5 ~1.6 ~-4 {Tags:["fstest"],billboard:"center",text:{"text":"Test 4: glow frame = overflow. 64 stone go to test 1, 64 come here"}}

# Test 5: catch-all (hopper in the frame)
setblock ~7 ~ ~-4 minecraft:chest[facing=south]
execute positioned ~7 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"hopper",facing:3}
summon minecraft:text_display ~7 ~1.6 ~-4 {Tags:["fstest"],billboard:"center",text:{"text":"Test 5: catch-all takes rotten flesh and string"}}

# Safety: locked chest and blocked chest are skipped, apples go to the open chest
setblock ~9 ~ ~-4 minecraft:chest[facing=south]{lock:{items:"minecraft:diamond"}}
setblock ~10 ~ ~-4 minecraft:chest[facing=south]
setblock ~10 ~1 ~-4 minecraft:stone
setblock ~11 ~ ~-4 minecraft:chest[facing=south]
execute positioned ~9 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"apple",facing:3}
execute positioned ~10 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"apple",facing:3}
execute positioned ~11 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"apple",facing:3}
summon minecraft:text_display ~10 ~2.4 ~-4 {Tags:["fstest"],billboard:"center",text:{"text":"Safety: locked (left) and blocked (middle) stay empty; apples go right"}}

# Restock: torches, labelled, with stock inside
setblock ~12 ~ ~-4 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:torch",count:64}]}
execute positioned ~12 ~ ~-3 run function framestash_test:frame {type:"item_frame",item:"torch",facing:3}
summon minecraft:text_display ~12 ~1.6 ~-4 {Tags:["fstest"],billboard:"center",text:{"text":"Restock: /restock fills your 4 torches to 64"}}

tellraw @s {"text":"Frame Stash test room built. Next: /function framestash_test:items","color":"green"}
