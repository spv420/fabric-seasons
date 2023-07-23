package io.github.lucaargolo.seasons.mixin;

import io.github.lucaargolo.seasons.FabricSeasons;
import io.github.lucaargolo.seasons.utils.Meltable;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SnowBlock.class)
public abstract class SnowBlockMixin extends Block implements Meltable {

    public SnowBlockMixin(Settings settings) {
        super(settings);
    }


    @Inject(at = @At("HEAD"), method = "randomTick")
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (world.getLightLevel(LightType.SKY, pos) > 0 && world.getBiome(pos).value().getTemperature(pos) >= 0.15F) {
            Block.dropStacks(state, world, pos);
            BlockState replacedState = FabricSeasons.getReplacedMeltablesState(world).getReplaced(pos);
            if(replacedState != null) {
                if(replacedState.getProperties().contains(TallPlantBlock.HALF) && replacedState.get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER) {
                    BlockState replacedUpperState = replacedState.with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER);
                    if(world.canPlace(replacedState, pos, ShapeContext.absent()) && world.canPlace(replacedUpperState, pos.up(), ShapeContext.absent())) {
                        world.setBlockState(pos, replacedState);
                        world.setBlockState(pos.up(), replacedUpperState);
                    }else{
                        world.removeBlock(pos, false);
                    }
                }else if(world.canPlace(replacedState, pos, ShapeContext.absent())) {
                    world.setBlockState(pos, replacedState);
                }else{
                    world.removeBlock(pos, false);
                }
            }else{
                world.removeBlock(pos, false);
            }
        }
    }

}
