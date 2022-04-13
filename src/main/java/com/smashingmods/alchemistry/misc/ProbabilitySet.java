package com.smashingmods.alchemistry.misc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ProbabilitySet {

    private List<ProbabilityGroup> set;
    public final boolean relativeProbability;
    public final int rolls;

    public ProbabilitySet(List<ProbabilityGroup> set) {
        this(set, true, 1);
    }

    public ProbabilitySet(List<ProbabilityGroup> set, boolean relativeProbability, int rolls) {
        this.set = set;
        this.relativeProbability = relativeProbability;
        this.rolls = rolls;
    }

    public JsonElement serialize() {
        JsonObject temp = new JsonObject();
        temp.add("rolls", new JsonPrimitive(rolls));
        temp.add("relativeProbability", new JsonPrimitive(relativeProbability));
        JsonArray setGroups = new JsonArray();
        for (ProbabilityGroup group : set) {
            setGroups.add(group.serialize());
        }
        temp.add("groups", setGroups);
        return temp;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(set.size());
        for (ProbabilityGroup group : set) {
            group.write(buf);
        }
        buf.writeBoolean(relativeProbability);
        buf.writeInt(rolls);
    }

    public static ProbabilitySet read(FriendlyByteBuf buf) {
        List<ProbabilityGroup> set = Lists.newArrayList();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            set.add(ProbabilityGroup.read(buf));
        }
        boolean relativeProbability = buf.readBoolean();
        int rolls = buf.readInt();
        return new ProbabilitySet(set, relativeProbability, rolls);
    }

    public List<ProbabilityGroup> getSet() {
        return set;
    }

    public List<ItemStack> toStackList() {
        ImmutableList.Builder<List<ItemStack>> builder = ImmutableList.builder();
        set.forEach(it -> builder.add(ImmutableList.copyOf(it.getOutputs())));//.stream().map(x->x.orElse(null)).collect(Collectors.toList()))));
        return builder.build().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public List<ItemStack> filterNonEmpty() {
        return toStackList().stream().filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    public double probabilityAtIndex(int index) {
        double sum = getTotalProbability();
        if (relativeProbability) return set.get(index).getProbability() / sum;
        else return set.get(index).getProbability();
    }

    private double getTotalProbability() {
        return set.stream().mapToDouble(ProbabilityGroup::getProbability).sum();
    }

    public NonNullList<ItemStack> calculateOutput() {
        NonNullList<ItemStack> temp = NonNullList.create();
        Random rando = new Random();
        for (int i = 1; i <= rolls; i++) {
            if (relativeProbability) {
                double totalProbability = getTotalProbability();
                double targetProbability = rando.nextDouble();
                double trackingProbability = 0.0;

                for (ProbabilityGroup component : set) {
                    trackingProbability += (component.getProbability() / totalProbability);
                    if (trackingProbability >= targetProbability) {
                        component.getOutputs().stream().filter(x -> !x.isEmpty()).forEach(x -> {
                            ItemStack stack = x.copy();

                            int index = IntStream.range(0, temp.size())
                                    .filter(it -> ItemStack.isSameItemSameTags(stack, temp.get(it)))
                                    .findFirst().orElse(-1);

                            if (index != -1) temp.get(index).grow(stack.getCount());
                            else temp.add(stack);
                        });
                        break;
                    }
                }
            } else { //absolute probability
                for (ProbabilityGroup component : set) {
                    if (component.getProbability() >= rando.nextInt(101)) {
                        component.getOutputs().stream().filter(x -> !x.isEmpty()).forEach(x -> {
                            ItemStack stack = x.copy();

                            int index = IntStream.range(0, temp.size())
                                    .filter(it -> ItemStack.isSameItemSameTags(stack, temp.get(it)))
                                    .findFirst().orElse(-1);

                            if (index != -1) temp.get(index).grow(stack.getCount());
                            else temp.add(stack);
                        });
                    }
                }
            }
        }
        return temp;
    }

    public static class Builder {
        private List<ProbabilityGroup> groups = new ArrayList<>();
        private boolean relativeProbability = true;
        private int rolls = 1;

        public Builder() {
        }


        public Builder addGroup(ProbabilityGroup group) {
            groups.add(group);
            return this;
        }

        public Builder rolls(int rolls) {
            this.rolls = rolls;
            return this;
        }

        public Builder relative(boolean relativeProbability) {
            this.relativeProbability = relativeProbability;
            return this;
        }

        public Builder addGroup(double probability, ItemStack... stacks) {
            groups.add(new ProbabilityGroup(Lists.newArrayList(stacks), probability));
            return this;
        }

        /*
        public Builder addGroup(double probability, LazyOptional<Item>... items) {
            return addGroup(probability, Arrays.stream(items).map(x->x.orElse(null)).map(ItemStack::new).toArray(ItemStack[]::new));
        }
*/
        public ProbabilitySet build() {
            return new ProbabilitySet(this.groups, relativeProbability, rolls);
        }
    }
}
