package dev.protomanly.pmweather.util;

public class SmartDataUpdater<T> {
   private T value;

   public SmartDataUpdater(T initial) {
      this.value = initial;
   }

   public boolean update(T next) {
      boolean isDirty = !this.value.equals(next);
      this.value = next;
      return isDirty;
   }

   public T get() {
      return this.value;
   }
}
