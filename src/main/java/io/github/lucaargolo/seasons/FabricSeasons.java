package io.github.lucaargolo.seasons;

import io.github.lucaargolo.seasons.block.SeasonDetectorBlock;
import io.github.lucaargolo.seasons.commands.SeasonCommand;
import io.github.lucaargolo.seasons.item.SeasonCalendarItem;
import io.github.lucaargolo.seasons.mixin.WeatherAccessor;
import io.github.lucaargolo.seasons.utils.ModConfig;
import io.github.lucaargolo.seasons.utils.ModIdentifier;
import io.github.lucaargolo.seasons.utils.Season;
import io.github.lucaargolo.seasons.utils.WeatherCache;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class FabricSeasons implements ModInitializer {

    public static final String MOD_ID = "seasons";
    public static ModConfig MOD_CONFIG = new ModConfig();
    public static HashMap<Item, Block> SEEDS_MAP = new HashMap<>();

    private static BlockEntityType<BlockEntity> seasonDetectorType = null;

    @Override
    public void onInitialize() {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        MOD_CONFIG = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> SeasonCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SEEDS_MAP.clear();
            Registry.ITEM.forEach(item -> {
                if(item instanceof BlockItem) {
                    Block block = ((BlockItem) item).getBlock();
                    if(block instanceof CropBlock || block instanceof StemBlock || block instanceof CocoaBlock) {
                        FabricSeasons.SEEDS_MAP.put(item, ((BlockItem) item).getBlock());
                    }
                }
            });
        });

        if(MOD_CONFIG.isSeasonDetectorEnabled()) {
            SeasonDetectorBlock seasonDetector = Registry.register(Registry.BLOCK, new ModIdentifier("season_detector"), new SeasonDetectorBlock(FabricBlockSettings.copyOf(Blocks.DAYLIGHT_DETECTOR)));
            seasonDetectorType = Registry.register(Registry.BLOCK_ENTITY_TYPE, new ModIdentifier("season_detector"), BlockEntityType.Builder.create(() -> seasonDetector.createBlockEntity(null), seasonDetector).build(null));
            Registry.register(Registry.ITEM, new ModIdentifier("season_detector"), new BlockItem(seasonDetector, new Item.Settings().group(ItemGroup.REDSTONE)));
        }

        if(MOD_CONFIG.isSeasonCalendarEnabled()) {
            Registry.register(Registry.ITEM, new ModIdentifier("season_calendar"), new SeasonCalendarItem((new Item.Settings()).group(ItemGroup.TOOLS)));
        }
    }

    public static BlockEntityType<BlockEntity> getSeasonDetectorType() {
        return seasonDetectorType;
    }

    public static Season getCurrentSeason(World world) {
        if(MOD_CONFIG.isSeasonLocked()) {
            return MOD_CONFIG.getLockedSeason();
        }
        int seasonTime = Math.toIntExact(world.getTimeOfDay()) / MOD_CONFIG.getSeasonLength();
        return Season.values()[seasonTime % 4];
    }

    @Environment(EnvType.CLIENT)
    public static Season getCurrentSeason() {
        if(MOD_CONFIG.isSeasonLocked()) {
            return MOD_CONFIG.getLockedSeason();
        }
        World world = MinecraftClient.getInstance().world;
        int worldTime = (world != null) ? Math.toIntExact(world.getTimeOfDay()) : 0;
        int seasonTime = (worldTime / MOD_CONFIG.getSeasonLength());
        return Season.values()[seasonTime % 4];
    }

    public static void injectBiomeTemperature(Biome biome, World world) {
        if(!MOD_CONFIG.doTemperatureChanges()) return;

        List<Biome.Category> ignoredCategories = Arrays.asList(Biome.Category.NONE, Biome.Category.NETHER, Biome.Category.THEEND, Biome.Category.OCEAN);
        if(ignoredCategories.contains(biome.getCategory())) return;

        Season season = FabricSeasons.getCurrentSeason(world);

        Identifier biomeIdentifier = world.getRegistryManager().get(Registry.BIOME_KEY).getId(biome);
        Biome.Weather currentWeather = biome.weather;

        Biome.Weather originalWeather;
        if (!WeatherCache.hasCache(biomeIdentifier)) {
            originalWeather = new Biome.Weather(currentWeather.precipitation, currentWeather.temperature, currentWeather.temperatureModifier, currentWeather.downfall);
            WeatherCache.setCache(biomeIdentifier, originalWeather);
        } else {
            originalWeather = WeatherCache.getCache(biomeIdentifier);
        }

        float temp = originalWeather.temperature;
        if(biome.getCategory() == Biome.Category.JUNGLE || biome.getCategory() == Biome.Category.SWAMP) {
            //Jungle Biomes
            if (season == Season.WINTER) {
                ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                ((WeatherAccessor) currentWeather).setTemperature(temp-0.1f);
            } else {
                ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                ((WeatherAccessor) currentWeather).setTemperature(temp);
            }
        }else if(temp <= 0.1) {
            //Frozen Biomes
            switch (season) {
                case SUMMER:
                    ((WeatherAccessor) currentWeather).setPrecipitation(Biome.Precipitation.RAIN);
                    ((WeatherAccessor) currentWeather).setTemperature(temp+0.3f);
                    break;
                case WINTER:
                    ((WeatherAccessor) currentWeather).setPrecipitation(Biome.Precipitation.SNOW);
                    ((WeatherAccessor) currentWeather).setTemperature(temp-0.2f);
                    break;
                default:
                    ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                    ((WeatherAccessor) currentWeather).setTemperature(temp);
            }
        }else if(temp <= 0.3) {
            //Cold Biomes
            switch (season) {
                case SPRING:
                    ((WeatherAccessor) currentWeather).setPrecipitation(Biome.Precipitation.RAIN);
                    ((WeatherAccessor) currentWeather).setTemperature(temp);
                    break;
                case SUMMER:
                    ((WeatherAccessor) currentWeather).setPrecipitation(Biome.Precipitation.RAIN);
                    ((WeatherAccessor) currentWeather).setTemperature(temp+0.2f);
                    break;
                case WINTER:
                    ((WeatherAccessor) currentWeather).setPrecipitation(Biome.Precipitation.SNOW);
                    ((WeatherAccessor) currentWeather).setTemperature(temp-0.2f);
                    break;
                default:
                    ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                    ((WeatherAccessor) currentWeather).setTemperature(temp);
            }
        }else if(temp <= 0.95) {
            //Temperate Biomes
            switch (season) {
                case SUMMER:
                    ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                    ((WeatherAccessor) currentWeather).setTemperature(temp+0.2f);
                    break;
                case FALL:
                    ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                    ((WeatherAccessor) currentWeather).setTemperature(temp-0.1f);
                    break;
                case WINTER:
                    ((WeatherAccessor) currentWeather).setPrecipitation(Biome.Precipitation.SNOW);
                    ((WeatherAccessor) currentWeather).setTemperature(temp-0.7f);
                    break;
                default:
                    ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                    ((WeatherAccessor) currentWeather).setTemperature(temp);
            }
        }else{
            //Hot biomes
            switch (season) {
                case SUMMER:
                    ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                    ((WeatherAccessor) currentWeather).setTemperature(temp+0.2f);
                    break;
                case WINTER:
                    ((WeatherAccessor) currentWeather).setPrecipitation(Biome.Precipitation.RAIN);
                    ((WeatherAccessor) currentWeather).setTemperature(temp-0.2f);
                    break;
                default:
                    ((WeatherAccessor) currentWeather).setPrecipitation(originalWeather.precipitation);
                    ((WeatherAccessor) currentWeather).setTemperature(temp);
            }
        }
    }

}
