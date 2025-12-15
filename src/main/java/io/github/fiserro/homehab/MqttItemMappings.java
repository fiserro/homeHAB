package io.github.fiserro.homehab;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.function.BiConsumer;
import lombok.Builder;
import org.openhab.core.items.GenericItem;

@Builder(builderClassName = "MqttItemMappingsBuilder")
public record MqttItemMappings(Multimap<HabState.Fields, GenericItem> mappings) {
  public static class MqttItemMappingsBuilder {

    public MqttItemMappingsBuilder() {
      mappings = HashMultimap.create();
    }

    public MqttItemMappingsBuilder of(HabState.Fields field, GenericItem item) {
      mappings.put(field, item);
      return this;
    }
  }

  public Collection<GenericItem> get(HabState.Fields field) {
    return mappings.get(field);
  }

  public void forEach(BiConsumer<HabState.Fields, Collection<GenericItem>> consumer) {
    mappings.asMap().forEach(consumer);
  }
}
