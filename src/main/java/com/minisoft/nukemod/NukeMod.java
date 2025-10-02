package com.minisoft.nukemod;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(NukeMod.MODID)
public class NukeMod {
    public static final String MODID = "nukemod";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static final String exampleBlock = "nuke_block";

    // 简化的方块注册
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock(exampleBlock,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE));

    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("nuke_block", EXAMPLE_BLOCK);
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("nuke_item",
            new Item.Properties().food(new FoodProperties.Builder()
                    .alwaysEdible()
                    .nutrition(1)
                    .saturationModifier(2f)
                    .build()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("nuketnt_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.nukemod.nuketnt_tab"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_BLOCK_ITEM.get());
                output.accept(EXAMPLE_ITEM.get());
            }).build());

    public NukeMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // 注册事件监听器
        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }
        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // 添加到红石方块标签页
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
        // 添加到功能方块标签页
        else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    @SubscribeEvent
    public void onItemUseFinish(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (event.getItem().getItem() == EXAMPLE_ITEM.get()) {
                // 给予玩家中毒效果
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 1200, 1));
            }
        }
    }

    // 核弹事件处理器
    @SubscribeEvent
    public void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
        // 获取方块状态和位置
        BlockState state = event.getLevel().getBlockState(event.getPos());
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        Level level = event.getLevel();

        // 检查是否是我们的方块且玩家按下了Shift
        if (state.is(EXAMPLE_BLOCK.get()) && player.isShiftKeyDown()) {
            // 取消事件（防止其他操作）
            event.setCanceled(true);

            // 只在服务端执行
            if (!level.isClientSide()) {
                // 1. 核爆炸效果
                level.explode(null, pos.getX(), pos.getY(), pos.getZ(),
                        350.0f, Level.ExplosionInteraction.TNT);

                // 2. 音效：使用.value()获取SoundEvent
                SoundEvent explosionSound = SoundEvents.GENERIC_EXPLODE.value();
                level.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                        explosionSound, SoundSource.BLOCKS, 10.0f, 0.1f);

                // 3. 辐射效果（范围700格）
                AABB radiationZone = new AABB(pos).inflate(700);
                level.getEntitiesOfClass(LivingEntity.class, radiationZone).forEach(entity -> {
                    entity.addEffect(new MobEffectInstance(MobEffects.POISON, 1200, 1)); // 60秒中毒II
                });

                // 4. 移除方块
                level.destroyBlock(pos, false);

                // 返回成功结果
                event.setCancellationResult(InteractionResult.SUCCESS);
            } else {
                // 客户端只返回成功，不执行实际逻辑
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}