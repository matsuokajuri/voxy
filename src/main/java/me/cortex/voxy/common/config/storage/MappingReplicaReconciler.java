package me.cortex.voxy.common.config.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.world.other.MappingStorageMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reconciles mapping replicas without deleting a value that may be the only valid copy. */
public final class MappingReplicaReconciler {
    private MappingReplicaReconciler() {
    }

    public static Int2ObjectOpenHashMap<byte[]> reconcile(StorageBackend[] backends) {
        if (backends.length == 0) {
            throw new IllegalArgumentException("At least one fragmented backend is required");
        }

        List<Replica> replicas = new ArrayList<>(backends.length);
        for (int index = 0; index < backends.length; index++) {
            Int2ObjectOpenHashMap<byte[]> mappings = backends[index].getIdMappingsData();
            boolean valid = true;
            try {
                validateSnapshot(mappings);
            } catch (RuntimeException exception) {
                valid = false;
            }
            replicas.add(new Replica(index, mappings, valid));
        }

        if (replicas.stream().allMatch(replica -> replica.mappings().isEmpty())) {
            return new Int2ObjectOpenHashMap<>();
        }

        Replica candidate = selectCandidate(replicas);
        if (candidate == null) {
            throw new IllegalStateException("No verified mapping replica has a safe majority or superset");
        }

        for (Replica replica : replicas) {
            if (snapshotsEqual(candidate.mappings(), replica.mappings())) {
                continue;
            }
            if (!hasNoExtraKeys(replica.mappings(), candidate.mappings())) {
                throw new IllegalStateException(
                        "Mapping replica " + replica.index() + " contains unverified extra keys; refusing repair");
            }
        }

        for (Replica replica : replicas) {
            if (snapshotsEqual(candidate.mappings(), replica.mappings())) {
                continue;
            }
            for (var entry : candidate.mappings().int2ObjectEntrySet()) {
                MappingStorageMetadata.putBytes(
                        backends[replica.index()],
                        entry.getIntKey(),
                        entry.getValue());
            }
            backends[replica.index()].flush();
        }
        return MappingStorageMetadata.deepCopy(candidate.mappings());
    }

    private static Replica selectCandidate(List<Replica> replicas) {
        Replica onlyValid = null;
        int validCount = 0;
        for (Replica replica : replicas) {
            if (replica.valid() && !replica.mappings().isEmpty()) {
                onlyValid = replica;
                validCount++;
            }
        }
        if (validCount == 1) {
            return onlyValid;
        }

        Replica majority = null;
        int majorityCount = 0;
        for (Replica candidate : replicas) {
            if (!candidate.valid() || candidate.mappings().isEmpty()) {
                continue;
            }
            int count = 0;
            for (Replica replica : replicas) {
                if (snapshotsEqual(candidate.mappings(), replica.mappings())) {
                    count++;
                }
            }
            if (count > majorityCount) {
                majority = candidate;
                majorityCount = count;
            }
        }
        if (majority != null && majorityCount > backendsWithData(replicas) / 2) {
            return majority;
        }

        Replica superset = null;
        for (Replica candidate : replicas) {
            if (!candidate.valid() || candidate.mappings().isEmpty()) {
                continue;
            }
            boolean containsEveryReplica = true;
            for (Replica replica : replicas) {
                if (!isCompatibleSubset(replica.mappings(), candidate.mappings())) {
                    containsEveryReplica = false;
                    break;
                }
            }
            if (containsEveryReplica) {
                if (superset != null && !snapshotsEqual(superset.mappings(), candidate.mappings())) {
                    return null;
                }
                superset = candidate;
            }
        }
        return superset;
    }

    private static int backendsWithData(List<Replica> replicas) {
        int count = 0;
        for (Replica replica : replicas) {
            if (!replica.mappings().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void validateSnapshot(Int2ObjectOpenHashMap<byte[]> mappings) {
        MappingStorageMetadata.validateDataMappings(MappingStorageMetadata.dataMappings(mappings));
        byte[] manifest = mappings.get(MappingStorageMetadata.MANIFEST_KEY);
        if (manifest != null) {
            MappingStorageMetadata.decodeManifest(manifest);
        }
        byte[] backup = mappings.get(MappingStorageMetadata.BACKUP_KEY);
        if (backup != null) {
            MappingStorageMetadata.decodeBackup(backup);
        }
    }

    private static boolean hasNoExtraKeys(
            Int2ObjectOpenHashMap<byte[]> possibleSubset,
            Int2ObjectOpenHashMap<byte[]> expectedSuperset) {
        for (int key : possibleSubset.keySet()) {
            if (!expectedSuperset.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompatibleSubset(
            Int2ObjectOpenHashMap<byte[]> possibleSubset,
            Int2ObjectOpenHashMap<byte[]> expectedSuperset) {
        for (var entry : possibleSubset.int2ObjectEntrySet()) {
            byte[] expected = expectedSuperset.get(entry.getIntKey());
            if (expected == null || !Arrays.equals(entry.getValue(), expected)) {
                return false;
            }
        }
        return true;
    }

    private static boolean snapshotsEqual(
            Int2ObjectOpenHashMap<byte[]> first,
            Int2ObjectOpenHashMap<byte[]> second) {
        return first.size() == second.size() && isCompatibleSubset(first, second);
    }

    private record Replica(int index, Int2ObjectOpenHashMap<byte[]> mappings, boolean valid) {
    }
}
