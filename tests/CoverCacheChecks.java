import dev.astriavr.player.CoverCacheFiles;
import java.io.File;
import java.nio.file.Files;
import java.util.Set;

public final class CoverCacheChecks {
    private static void expect(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    private static File create(File root, String name, int size, long modified) throws Exception {
        File file = new File(root, name);
        Files.write(file.toPath(), new byte[size]);
        expect(file.setLastModified(modified), "timestamp setup");
        return file;
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("astria-cover-check-").toFile();
        try {
            long now = System.currentTimeMillis();
            String a = "a".repeat(64), b = "b".repeat(64), c = "c".repeat(64);
            File old = create(root, a + ".webp", 8, now - 3000);
            File recent = create(root, b + ".webp", 8, now - 1000);
            File orphan = create(root, c + ".jpg", 8, now - 2000);
            File staleTemp = create(root, a + "-123.tmp", 8, now - 3_600_001);
            File activeTemp = create(root, b + "-456.tmp", 8, now);
            File unrelated = create(root, "unrelated.txt", 8, now);
            CoverCacheFiles.trim(root, Set.of(a, b), 16, 2, now);
            expect(old.exists() && recent.exists(), "current covers survive");
            expect(!orphan.exists(), "obsolete revision/removed video's cover removed");
            expect(!staleTemp.exists() && activeTemp.exists(), "stale temp removed, active encode preserved");
            expect(unrelated.exists(), "unrelated file untouched");
            CoverCacheFiles.trim(root, null, 8, 2, now);
            expect(!old.exists() && recent.exists(), "size bound evicts least-recently used first");
            old = create(root, a + ".webp", 8, now - 3000);
            CoverCacheFiles.trim(root, null, 1000, 1, now);
            expect(!old.exists() && recent.exists(), "file count bound");
            File empty = create(root, c + ".webp", 0, now);
            CoverCacheFiles.trim(root, null, 1000, 10, now);
            expect(!empty.exists(), "empty/corrupt write removed");
            CoverCacheFiles.trim(root, Set.of(), 0, 0, now + 3_600_001);
            expect(!recent.exists() && !activeTemp.exists(), "retired cache is purged");
            File retiredImage = create(root, a + ".jpg", 8, now);
            File retiredTemp = create(root, b + "-789.tmp", 8, now);
            CoverCacheFiles.clearRetired(root);
            expect(!retiredImage.exists() && !retiredTemp.exists() && unrelated.exists(), "retired format cleanup preserves unrelated files");
            System.out.println("Cover cache housekeeping checks passed.");
        } finally {
            File[] owned = root.listFiles();
            if (owned != null) for (File file : owned) file.delete();
            root.delete();
        }
    }
}
