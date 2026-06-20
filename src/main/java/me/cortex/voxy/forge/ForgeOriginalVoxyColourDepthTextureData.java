package me.cortex.voxy.forge;

import java.util.Arrays;

record ForgeOriginalVoxyColourDepthTextureData(int[] colour, int[] depth, int width, int height, int hash) {
    ForgeOriginalVoxyColourDepthTextureData(int[] colour, int[] depth, int width, int height) {
        this(colour, depth, width, height, width * 312337173 * (Arrays.hashCode(colour) ^ Arrays.hashCode(depth)) ^ height);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        var other = (ForgeOriginalVoxyColourDepthTextureData) obj;
        return this.hash == other.hash && Arrays.equals(other.colour, this.colour) && Arrays.equals(other.depth, this.depth);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public ForgeOriginalVoxyColourDepthTextureData clone() {
        return new ForgeOriginalVoxyColourDepthTextureData(
                Arrays.copyOf(this.colour, this.colour.length),
                Arrays.copyOf(this.depth, this.depth.length),
                this.width,
                this.height,
                this.hash
        );
    }
}
