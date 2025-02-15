package com.enderio.machines.common.blockentity.task;

import com.enderio.core.common.recipes.OutputStack;
import com.enderio.machines.common.io.item.MachineInventory;
import com.enderio.machines.common.io.item.MultiSlotAccess;
import com.enderio.machines.common.io.item.SingleSlotAccess;
import com.enderio.machines.common.recipe.MachineRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO: A recipe interface that doesn't require power :)
public abstract class CraftingMachineTask<R extends MachineRecipe<C>, C extends Container>
    implements IMachineTask {

    protected final Level level;
    protected final MachineInventory inventory;
    protected final C container;
    protected final MultiSlotAccess outputSlots;

    @Nullable
    private R recipe;

    private int progressMade;
    private int progressRequired;

    private boolean hasConsumedInputs;
    private boolean hasDeterminedOutputs;

    private List<OutputStack> outputs = List.of();

    private boolean isComplete;

    public CraftingMachineTask(@NotNull Level level, MachineInventory inventory, C container, MultiSlotAccess outputSlots, @Nullable R recipe) {
        this.level = level;
        this.inventory = inventory;
        this.container = container;
        this.outputSlots = outputSlots;
        this.recipe = recipe;
    }

    public MachineInventory getInventory() {
        return inventory;
    }

    @Nullable
    public R getRecipe() {
        return recipe;
    }

    // region Abstract Implementation

    protected abstract void consumeInputs(R recipe);

    protected abstract int makeProgress(int remainingProgress);

    protected abstract int getProgressRequired(R recipe);

    // endregion

    // region Task Implementation

    @Override
    public void tick() {
        // If the recipe is done, don't let it tick.
        if (isComplete)
            return;

        // If the recipe failed to load somehow, cancel
        if (recipe == null) {
            isComplete = true;
            return;
        }

        // Get the outputs list.
        if (!hasDeterminedOutputs) {
            hasDeterminedOutputs = true;
            outputs = recipe.craft(container, level.registryAccess());

            // TODO: Compact any items that are the same into singular stacks?
        }

        // If we can't input or output, cancel the task. However, if for some reason we can't output after the inputs are collected, don't.
        if (!hasConsumedInputs && (!placeOutputs(outputs, true) || !recipe.matches(container, level))) {
            isComplete = true;
            // This means if a sag mill recipe outputs 2 it cancels the recipe, and the determined outputs are cleared. It's a weird behaviour but not necessarily a bug.
            // We might want to review how this works in the future, as right now we wait for an inventory change rather than the machine tick repeatedly.
            return;
        }

        // If we haven't done so already, consume inputs for the recipe.
        if (!hasConsumedInputs) {
            // Consume inputs for the recipe.
            consumeInputs(recipe);
            hasConsumedInputs = true;

            // Store the recipe energy cost.
            // This is run afterward as it allows container context changes after takeInputs()
            progressRequired = getProgressRequired(recipe);
        }

        // Try to consume as much energy as possible to finish the craft.
        if (progressMade < progressRequired) {
            progressMade += makeProgress(progressRequired - progressMade);
        }

        // If the recipe has been crafted, attempt to put it into storage
        if (progressMade >= progressRequired) {
            // Attempt to complete the craft
            if (placeOutputs(outputs, false)) {
                // The receiver was able to take the outputs, task complete.
                isComplete = true;
            }
        }
    }

    @Override
    public float getProgress() {
        if (recipe == null)
            return 0.0f;
        return progressMade / (float) progressRequired;
    }

    @Override
    public boolean isCompleted() {
        return isComplete;
    }

    // endregion

    // region Resource Depletion

    protected boolean placeOutputs(List<OutputStack> outputs, boolean simulate) {
        // TODO: Handle fluids too.

        // See that we can add all the outputs
        for (OutputStack output : outputs) {
            ItemStack item = output.getItem();

            for (SingleSlotAccess outputAccess: outputSlots.getAccesses()) {
                item = outputAccess.insertItem(inventory, item, true);
            }

            // If we fail, say we can't accept these outputs
            if (!item.isEmpty())
                return false;
        }

        // If we're not simulating, go for it
        if (!simulate) {
            for (OutputStack output : outputs) {
                ItemStack item = output.getItem();

                for (SingleSlotAccess outputAccess: outputSlots.getAccesses()) {
                    item = outputAccess.insertItem(inventory, item, false);
                }
            }
        }

        return true;
    }

    // endregion

    // region Serialization

    private static final String KEY_RECIPE_ID = "RecipeId";
    private static final String KEY_PROGRESS_MADE = "ProgressMade";
    private static final String KEY_PROGRESS_REQUIRED = "ProgressRequired";
    private static final String KEY_HAS_COLLECTED_INPUTS = "HasCollectedInputs";
    private static final String KEY_IS_COMPLETE = "IsComplete";
    private static final String KEY_HAS_DETERMINED_OUTPUTS = "HasDeterminedOutputs";
    private static final String KEY_OUTPUTS = "Outputs";

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        // If the recipe is null, we aren't going to keep the task
        if (recipe == null) {
            return tag;
        }

        tag.putString(KEY_RECIPE_ID, recipe.getId().toString());
        tag.putInt(KEY_PROGRESS_MADE, progressMade);
        tag.putInt(KEY_PROGRESS_REQUIRED, progressRequired);
        tag.putBoolean(KEY_HAS_COLLECTED_INPUTS, hasConsumedInputs);
        tag.putBoolean(KEY_IS_COMPLETE, isComplete);

        tag.putBoolean(KEY_HAS_DETERMINED_OUTPUTS, hasDeterminedOutputs);
        if (hasDeterminedOutputs) {
            ListTag outputsNbt = new ListTag();
            for (OutputStack stack : outputs) {
                outputsNbt.add(stack.serializeNBT());
            }
            tag.put(KEY_OUTPUTS, outputsNbt);
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        recipe = loadRecipe(new ResourceLocation(nbt.getString(KEY_RECIPE_ID)));
        progressMade = nbt.getInt(KEY_PROGRESS_MADE);
        progressRequired = nbt.getInt(KEY_PROGRESS_REQUIRED);
        hasConsumedInputs = nbt.getBoolean(KEY_HAS_COLLECTED_INPUTS);
        isComplete = nbt.getBoolean(KEY_IS_COMPLETE);

        hasDeterminedOutputs = nbt.getBoolean(KEY_HAS_DETERMINED_OUTPUTS);
        if (hasDeterminedOutputs) {
            ListTag outputsNbt = nbt.getList(KEY_OUTPUTS, Tag.TAG_COMPOUND);
            outputs = new ArrayList<>();
            for (Tag tag : outputsNbt) {
                outputs.add(OutputStack.fromNBT((CompoundTag) tag));
            }
        }
    }

    @Nullable
    protected R loadRecipe(ResourceLocation id) {
        //noinspection unchecked
        return (R) level.getRecipeManager().byKey(id).orElse(null);
    }

    // endregion
}
