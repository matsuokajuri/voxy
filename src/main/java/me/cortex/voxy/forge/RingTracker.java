package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

final class RingTracker {
    private final Long2ByteOpenHashMap operations = new Long2ByteOpenHashMap(1 << 13);
    private final int[] boundDist;
    private final int radius;
    private int centerX;
    private int centerZ;

    RingTracker(int radius, int centerX, int centerZ, boolean fill) {
        this(null, radius, centerX, centerZ, fill);
    }

    RingTracker(RingTracker stealFrom, int radius, int centerX, int centerZ, boolean fill) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.boundDist = generateBoundingHalfCircleDistance(radius);
        if (stealFrom != null) {
            this.operations.putAll(stealFrom.operations);
            stealFrom.operations.clear();
        }
        if (fill) {
            this.fillRing(true);
        }
    }

    private static long pack(int x, int z) {
        return Integer.toUnsignedLong(x) | (Integer.toUnsignedLong(z) << 32);
    }

    private void fillRing(boolean load) {
        for (int i = 0; i <= this.radius * 2; i++) {
            int x = this.centerX + i - this.radius;
            int d = this.boundDist[i];
            for (int z = this.centerZ - d; z <= this.centerZ + d; z++) {
                int result = this.operations.addTo(pack(x, z), (byte) (load ? 1 : -1));
                if ((load && 0 < result) || (!load && result < 0)) {
                    throw new IllegalStateException();
                }
            }
        }
    }

    void unload() {
        this.fillRing(false);
    }

    void moveCenter(int x, int z) {
        if (this.radius + 1 < Math.abs(x - this.centerX) || this.radius + 1 < Math.abs(z - this.centerZ)) {
            this.fillRing(false);
            this.centerX = x;
            this.centerZ = z;
            this.fillRing(true);
        } else {
            if (x != this.centerX) {
                moveX(x - this.centerX);
            }
            if (z != this.centerZ) {
                moveZ(z - this.centerZ);
            }
        }
    }

    private void moveZ(int delta) {
        if (delta == 0) {
            return;
        }
        if (delta == -1 || delta == 1) {
            for (int i = 0; i <= this.radius * 2; i++) {
                int x = this.centerX + i - this.radius;
                int d = this.boundDist[i] * delta;
                int pz = this.centerZ + d + delta;
                int nz = this.centerZ - d;
                if (0 < this.operations.addTo(pack(x, pz), (byte) 1)) {
                    throw new IllegalStateException("x: " + x + ", z: " + pz + " state: " + this.operations.get(pack(x, pz)));
                }
                if (this.operations.addTo(pack(x, nz), (byte) -1) < 0) {
                    throw new IllegalStateException("x: " + x + ", z: " + nz + " state: " + this.operations.get(pack(x, nz)));
                }
            }
            this.centerZ += delta;
        } else {
            int signDelta = Integer.signum(delta);
            for (int i = 0; i <= this.radius * 2; i++) {
                int x = this.centerX + i - this.radius;
                int d = this.boundDist[i] * signDelta;
                int pz = this.centerZ + d;
                for (int z = pz + (signDelta < 0 ? delta : 1); z <= pz + (signDelta < 0 ? -1 : delta); z++) {
                    if (0 < this.operations.addTo(pack(x, z), (byte) 1)) {
                        throw new IllegalStateException();
                    }
                }
                int nz = this.centerZ - d;
                for (int z = nz + (signDelta < 0 ? (delta + 1) : 0); z < nz + (signDelta < 0 ? 1 : delta); z++) {
                    if (this.operations.addTo(pack(x, z), (byte) -1) < 0) {
                        throw new IllegalStateException();
                    }
                }
            }
            this.centerZ += delta;
        }
    }

    private void moveX(int delta) {
        if (delta == 0) {
            return;
        }
        if (delta == -1 || delta == 1) {
            for (int i = 0; i <= this.radius * 2; i++) {
                int z = this.centerZ + i - this.radius;
                int d = this.boundDist[i] * delta;
                int px = this.centerX + d + delta;
                int nx = this.centerX - d;
                if (0 < this.operations.addTo(pack(px, z), (byte) 1)) {
                    throw new IllegalStateException();
                }
                if (this.operations.addTo(pack(nx, z), (byte) -1) < 0) {
                    throw new IllegalStateException();
                }
            }
            this.centerX += delta;
        } else {
            int signDelta = Integer.signum(delta);
            for (int i = 0; i <= this.radius * 2; i++) {
                int z = this.centerZ + i - this.radius;
                int d = this.boundDist[i] * signDelta;
                int px = this.centerX + d;
                for (int x = px + (signDelta < 0 ? delta : 1); x <= px + (signDelta < 0 ? -1 : delta); x++) {
                    if (0 < this.operations.addTo(pack(x, z), (byte) 1)) {
                        throw new IllegalStateException();
                    }
                }
                int nx = this.centerX - d;
                for (int x = nx + (signDelta < 0 ? (delta + 1) : 0); x < nx + (signDelta < 0 ? 1 : delta); x++) {
                    if (this.operations.addTo(pack(x, z), (byte) -1) < 0) {
                        throw new IllegalStateException();
                    }
                }
            }
            this.centerX += delta;
        }
    }

    interface UpdateConsumer {
        void accept(int x, int z);
    }

    int process(int count, UpdateConsumer onAdd, UpdateConsumer onRemove) {
        if (this.operations.isEmpty()) {
            return 0;
        }
        var iter = this.operations.long2ByteEntrySet().fastIterator();
        int processed = 0;
        while (iter.hasNext() && count-- != 0) {
            var entry = iter.next();
            if (entry.getByteValue() == 0) {
                iter.remove();
                count++;
                continue;
            }
            processed++;
            byte op = entry.getByteValue();
            if (op != 1 && op != -1) {
                throw new IllegalStateException();
            }
            boolean isAdd = op == 1;
            long pos = entry.getLongKey();
            int x = (int) (pos & 0xFFFFFFFFL);
            int z = (int) ((pos >>> 32) & 0xFFFFFFFFL);
            if (isAdd) {
                onAdd.accept(x, z);
            } else {
                onRemove.accept(x, z);
            }
            iter.remove();
        }
        return processed;
    }

    private int[] generateBoundingHalfCircleDistance(int radius) {
        int[] ret = new int[radius * 2 + 1];
        for (int i = -radius; i <= radius; i++) {
            ret[i + radius] = (int) Math.sqrt(radius * radius - i * i);
        }
        return ret;
    }
}
