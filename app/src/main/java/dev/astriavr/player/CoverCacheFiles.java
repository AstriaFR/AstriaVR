package dev.astriavr.player;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.regex.Pattern;

/** Disk-only housekeeping, called by the low-priority cover worker. */
public final class CoverCacheFiles {
    public static final long MAX_BYTES = 32L * 1024 * 1024;
    public static final int MAX_FILES = 2048;
    private static final Pattern IMAGE = Pattern.compile("[0-9a-f]{64}\\.(jpg|webp)");
    private static final Pattern TEMP = Pattern.compile("[0-9a-f]{64}(-[0-9]+)?\\.tmp");
    private CoverCacheFiles() {}

    /** Retired format only: no current worker writes into this directory. */
    public static void clearRetired(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && (IMAGE.matcher(file.getName()).matches() || TEMP.matcher(file.getName()).matches()))
                file.delete();
        }
        directory.delete(); // Succeeds only when empty.
    }

    public static void trim(File directory, Set<String> currentKeys, long maxBytes, int maxFiles, long now) {
        File[] files = directory.listFiles();
        if (files == null) return;
        ArrayList<File> retained = new ArrayList<>();
        long bytes = 0;
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            // A different Activity's native decode may still be finishing; only discard old temp files.
            if (TEMP.matcher(name).matches()) {
                if (now - file.lastModified() > 3_600_000) file.delete();
                continue;
            }
            if (!IMAGE.matcher(name).matches()) continue;
            String key = name.substring(0, 64);
            if ((file.length() == 0 || currentKeys != null && !currentKeys.contains(key)) && file.delete()) continue;
            retained.add(file);
            bytes += file.length();
        }
        retained.sort(Comparator.comparingLong(File::lastModified));
        int count = retained.size();
        for (File file : retained) {
            if (bytes <= maxBytes && count <= maxFiles) break;
            long size = file.length();
            if (file.delete()) { bytes -= size; count--; }
        }
    }
}
