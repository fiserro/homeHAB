package io.github.fiserro.homehab.module;

public interface HabModules<T extends HabModules<T>> extends CommonModule<T>, HrvModule<T>, FlowerModule<T> {
}
