package dev.protomanly.pmweather.compat.sodium.interfaces;

public interface ChunkVertexExtension {
   void pmwsetData(int var1, int var2, int var3, int var4);

   int getID();

   int getX();

   int getY();

   int getZ();

   void copyData(ChunkVertexExtension var1);
}
