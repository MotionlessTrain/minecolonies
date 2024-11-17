package com.minecolonies.core.generation;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData.Builder;
import com.minecolonies.api.items.component.ModDataComponents;
import com.minecolonies.api.util.CraftingUtils;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagManager;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Automatically calculated vanilla level nbts of items.
 */
public class ItemNbtCalculator implements DataProvider
{
    private final PackOutput                               packOutput;
    private final CompletableFuture<HolderLookup.Provider> lookupProvider;

    public ItemNbtCalculator(@NotNull final PackOutput packOutput, final CompletableFuture<HolderLookup.Provider> lookupProvider)
    {
        this.packOutput = packOutput;
        this.lookupProvider = lookupProvider;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "ItemNBTCalculator";
    }

    @NotNull
    @Override
    public CompletableFuture<?> run(@NotNull final CachedOutput cache)
    {
        final ResourceManager serverResources = loadServerData();

        // Force loading some tags, since the creative tabs don't enumerate properly without them
        return lookupProvider
            // Tag TagKey[minecraft:instrument / minecraft:goat_horns] can't be dereferenced during construction
            .thenApply(p -> loadRegistryTags(p, serverResources, BuiltInRegistries.INSTRUMENT))
            // Missing tag: 'minecraft:enchantable/fishing' in 'minecraft:item'
            .thenApply(p -> loadRegistryTags(p, serverResources, BuiltInRegistries.ITEM))
            // Missing tag: 'minecraft:blocks_wind_charge_explosions' in 'minecraft:block'
            .thenApply(p -> loadRegistryTags(p, serverResources, BuiltInRegistries.BLOCK))
            // and now the actual calculator
            .thenCompose(provider ->
        {
            final List<ItemStack> allStacks;
            final ImmutableList.Builder<ItemStack> listBuilder = new ImmutableList.Builder<>();
            final CreativeModeTab.ItemDisplayParameters tempDisplayParams = new CreativeModeTab.ItemDisplayParameters(FeatureFlags.REGISTRY.allFlags(), false, provider);

            CraftingUtils.forEachCreativeTabItems(tempDisplayParams, (tab, stacks) ->
            {
                for (final ItemStack item : stacks)
                {
                    if (item.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof IMateriallyTexturedBlock texturedBlock)
                    {
                        final Builder builder = MaterialTextureData.builder();
                        for (final IMateriallyTexturedBlockComponent key : texturedBlock.getComponents())
                        {
                            builder.setComponent(key.getId(), key.getDefault());
                        }
                        final ItemStack copy = item.copy();
                        builder.writeToItemStack(copy);
                        listBuilder.add(copy);
                    }
                    else
                    {
                        listBuilder.add(item);
                    }
                }
            });

            listBuilder.add(Items.FILLED_MAP.getDefaultInstance());
            allStacks = listBuilder.build();

            final TreeMap<String, Set<String>> keyMapping = new TreeMap<>();
            final Set<DataComponentType<?>> typesToRemove = new ReferenceArraySet<>();

            // We ignore damage in nbt.
            typesToRemove.add(DataComponents.DAMAGE);

            // The following we don't care about matching.
            typesToRemove.add(DataComponents.LORE);
            typesToRemove.add(DataComponents.MAX_STACK_SIZE);
            typesToRemove.add(DataComponents.RARITY);
            typesToRemove.add(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            ModDataComponents.REGISTRY.getEntries().forEach(t -> typesToRemove.add(t.get()));
            com.ldtteam.structurize.component.ModDataComponents.REGISTRY.getEntries().forEach(t -> typesToRemove.add(t.get()));

            for (final ItemStack stack : allStacks)
            {
                final ResourceLocation resourceLocation = stack.getItemHolder().unwrapKey().get().location();
                final Set<DataComponentType<?>> keys = new ReferenceArraySet<>(stack.getComponents().keySet());

                if (stack.getItem() instanceof ArmorItem)
                {
                    keys.add(DataComponents.DYED_COLOR);
                }
                if (stack.is(Items.FILLED_MAP))
                {
                    keys.add(DataComponents.MAP_ID);
                }
                if (!stack.isEnchantable())
                {
                    keys.remove(DataComponents.ENCHANTMENTS);
                }
                if (!stack.isRepairable())
                {
                    keys.remove(DataComponents.REPAIR_COST);
                }
                if (stack.getAttributeModifiers().modifiers().isEmpty())
                {
                    keys.remove(DataComponents.ATTRIBUTE_MODIFIERS);
                }

                keys.removeAll(typesToRemove);

                keyMapping.compute(resourceLocation.toString(), (k, keysInMap) -> {
                    if (keysInMap == null)
                    {
                        keysInMap = new TreeSet<>();
                    }

                    for (final DataComponentType<?> type : keys)
                    {
                        keysInMap.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).toString());
                    }

                    return keysInMap;
                });
            }

            final Path path = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, "compatibility").file(new ResourceLocation(MOD_ID, "itemnbtmatching"), "json");
            final JsonArray jsonArray = new JsonArray();
            for (final Map.Entry<String, Set<String>> entry : keyMapping.entrySet())
            {
                final JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("item", entry.getKey());

                if (!entry.getValue().isEmpty())
                {
                    final JsonArray subArray = new JsonArray();
                    entry.getValue().forEach(subArray::add);
                    jsonObject.add("checkednbtkeys", subArray);
                }

                jsonArray.add(jsonObject);
            }

            return DataProvider.saveStable(cache, jsonArray, path);
        });
    }

    private static ResourceManager loadServerData()
    {
        // ideally we'd use ExistingFileHelper.getManager but sadly that's private :'(
        final List<PackResources> packs = List.of(
                ServerPacksSource.createVanillaPackSource(),
                ResourcePackLoader.createPackForMod(ModList.get().getModFileById("neoforge")).openPrimary(new PackLocationInfo("mod/neoforge", Component.empty(), PackSource.BUILT_IN, Optional.empty()))
        );
        // we only load tags from vanilla/nf, for simplicity (and because that's all we need for now)

        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    }

    private static <T> HolderLookup.Provider loadRegistryTags(
            @NotNull final HolderLookup.Provider provider,
            @NotNull final ResourceManager resources,
            @NotNull final Registry<T> registry)
    {
        final ResourceKey<? extends Registry<T>> registryId = registry.key();

        // from TagManager.createLoader
        final TagLoader<Holder<T>> tagLoader = new TagLoader<>(registry::getHolder, Registries.tagsDirPath(registryId));
        final TagManager.LoadResult<T> loadResult = new TagManager.LoadResult<>(registryId, tagLoader.loadAndBuild(resources));

        final Map<TagKey<T>, List<Holder<T>>> map = loadResult.tags()
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(entry -> TagKey.create(registryId, entry.getKey()), values -> List.copyOf(values.getValue())));
        registry.bindTags(map);

        return new HolderLookup.Provider()
        {
            @NotNull
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistries()
            {
                return provider.listRegistries();
            }

            @NotNull
            @Override
            public <U> Optional<HolderLookup.RegistryLookup<U>> lookup(@NotNull final ResourceKey<? extends Registry<? extends U>> id)
            {
                if (id.equals(registryId))
                {
                    return Optional.of((HolderLookup.RegistryLookup<U>) registry.asLookup());
                }

                return provider.lookup(id);
            }

            @NotNull
            @Override
            public <V> RegistryOps<V> createSerializationContext(@NotNull final DynamicOps<V> ops)
            {
                return provider.createSerializationContext(ops);
            }
        };
    }
}
