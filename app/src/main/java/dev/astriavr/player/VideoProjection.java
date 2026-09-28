package dev.astriavr.player;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.regex.Pattern;

/** Source projection IDs are also persisted in the playlist. Layout: 0 SBS, 1 TB, 2 mono. */
public final class VideoProjection {
    private VideoProjection() {}
    public static final int FISHEYE_180 = 181, STEREO_FISHEYE_180 = 182, CUBEMAP = 361, EAC = 362;
    public static boolean isFisheye(int projection) {
        return projection == FISHEYE_180 || projection == STEREO_FISHEYE_180;
    }
    public static int[] choices(boolean singleLens) {
        return singleLens ? new int[]{0, 180, 360, FISHEYE_180, STEREO_FISHEYE_180, CUBEMAP, EAC}
            : new int[]{0, 180, 360, STEREO_FISHEYE_180, CUBEMAP, EAC};
    }
    /** Upgrade the old manual fisheye option in headset mode without rewriting the phone choice. */
    public static int forViewingMode(int requested, boolean singleLens) {
        return !singleLens && requested == FISHEYE_180 ? STEREO_FISHEYE_180 : requested;
    }
    public static boolean valid(int projection) {
        return projection == 0 || projection == 180 || projection == 360
            || isFisheye(projection) || projection == CUBEMAP || projection == EAC;
    }
    public static int degrees(int projection) {
        return projection == 360 || projection == CUBEMAP || projection == EAC ? 360 : 180;
    }
    public static String label(int projection) {
        switch (projection) {
            case FISHEYE_180: return AppText.MONO_FISHEYE.text();
            case STEREO_FISHEYE_180: return AppText.STEREO_FISHEYE.text();
            case CUBEMAP: return "Cubemap 3×2";
            case EAC: return AppText.EAC_CUBEMAP.text();
            case 360: return AppText.EQUIRECTANGULAR.text();
            case 180: return AppText.EQUIRECTANGULAR_279.text();
            default: return AppText.AUTO_DETECT.text();
        }
    }

    /** DB v2 reserved pair: ordinary video explicitly viewed without VR projection. */
    public static boolean isFlatCover(int layout, int degrees) { return layout == 2 && degrees == 0; }

    /** An unplayed ordinary file must not be cropped using the legacy VR180 fallback. */
    public static boolean isOrdinaryCover(int layout, int projection, String name, int width, int height) {
        if (isFlatCover(layout, projection)) return true;
        if (projection != 0 || layout == 0 || layout == 1) return false;
        return !isVrVideo(name, false, -1, width, height, 1f);
    }

    public static final class Selection {
        public final int degrees, layout, projection;
        public final String reason;
        Selection(int degrees, int layout, String reason) {
            this.projection = degrees; this.degrees = degrees(degrees); this.layout = layout; this.reason = reason;
        }
    }

    private static Pattern token(String token) {
        return Pattern.compile("(?<![a-z0-9])(?:" + token + ")(?![a-z0-9])", Pattern.CASE_INSENSITIVE);
    }
    private static final Pattern HALF = token("(?:vr[ _-]?)?180(?:[ _-]?(?:vr|deg|degrees))?");
    private static final Pattern FULL = token("(?:vr[ _-]?)?360(?:[ _-]?(?:vr|deg|degrees))?");
    private static final Pattern SBS = token("sbs|hsbs|fsbs|lr|side[ _-]?by[ _-]?side");
    private static final Pattern TB = token("tb|ou|over[ _-]?under|top[ _-]?bottom");
    private static final Pattern MONO = token("mono|2d");
    private static final Pattern VR = token("vr|3d|spherical|equirectangular|panorama");
    private static final Pattern FISHEYE = token("(?:single[ _-]?)?fisheye(?:[ _-]?180)?");
    private static final Pattern CUBE = token("cubemap|cube[ _-]?map|c3x2");
    private static final Pattern ANGULAR = token("eac|equi[ _-]?angular");
    private static final Pattern DUAL_FISHEYE = token("dual[ _-]?fisheye(?:[ _-]?(?:180|360))?|dfisheye(?:[ _-]?(?:180|360))?");
    private static final Pattern STEREO_FISHEYE = token("(?:stereo|stereoscopic|3d)[ _-]?fisheye(?:[ _-]?180)?|stereo|stereoscopic|3d");
    private static final Pattern JOINED_STEREO_FISHEYE = token("(?:stereo|stereoscopic|3d)[ _-]?fisheye(?:[ _-]?180)?");
    private static final Pattern FISHEYE_FULL = token("(?:d?fisheye|dualfisheye)[ _-]?360");
    private static final Pattern FISHEYE_HALF = token("(?:d?fisheye|dualfisheye)[ _-]?180");
    public static int namedProjection(String name) {
        if (ANGULAR.matcher(name).find()) return EAC;
        if (CUBE.matcher(name).find()) return CUBEMAP;
        boolean dual = DUAL_FISHEYE.matcher(name).find() || name.contains("双鱼眼") || name.contains("双目鱼眼");
        boolean fisheye = FISHEYE.matcher(name).find() || dual || name.contains("鱼眼")
            || JOINED_STEREO_FISHEYE.matcher(name).find();
        boolean full = FULL.matcher(name).find() || FISHEYE_FULL.matcher(name).find() || name.contains("鱼眼360");
        if (fisheye && !full) {
            int layout = namedLayout(name);
            boolean stereo = layout == 0 || layout == 1 || STEREO_FISHEYE.matcher(name).find() || name.contains("双目");
            if (stereo || (dual && (HALF.matcher(name).find() || FISHEYE_HALF.matcher(name).find() || name.contains("鱼眼180"))))
                return STEREO_FISHEYE_180;
            if (!dual) return FISHEYE_180;
        }
        return namedDegrees(name);
    }

    /** Plain 16:9 and 2:1 videos must not inherit the old default 180-degree VR fallback. */
    public static boolean isVrVideo(String name, boolean projectionMetadata, int stereoLayout,
            int width, int height, float pixelRatio) {
        if (projectionMetadata || stereoLayout == 0 || stereoLayout == 1 || namedProjection(name) > 0) return true;
        if (HALF.matcher(name).find() || FULL.matcher(name).find() || SBS.matcher(name).find()
                || TB.matcher(name).find() || VR.matcher(name).find()
                || name.contains("全景") || name.contains("左右格式") || name.contains("上下格式")) return true;
        // 4:1 is a strong stereo-panorama hint; 2:1 alone is ambiguous and remains ordinary.
        return width > 0 && height > 0 && Float.isFinite(pixelRatio) && pixelRatio > 0
                && near(width * (double) pixelRatio / height, 4);
    }

    public static int namedDegrees(String name) {
        boolean half = HALF.matcher(name).find();
        boolean full = FULL.matcher(name).find();
        return half == full ? 0 : half ? 180 : 360;
    }

    public static int namedLayout(String name) {
        boolean sbs = SBS.matcher(name).find();
        boolean tb = TB.matcher(name).find();
        boolean mono = MONO.matcher(name).find();
        return (sbs ? 1 : 0) + (tb ? 1 : 0) + (mono ? 1 : 0) != 1 ? -1 : sbs ? 0 : tb ? 1 : 2;
    }

    private static boolean near(double ratio, double expected) {
        return Math.abs(ratio / expected - 1) <= .04;
    }

    public static Selection resolve(int requestedDegrees, int requestedLayout, int metadataDegrees,
            int metadataLayout, String name, int width, int height, float pixelRatio) {
        int named = namedProjection(name);
        int layout = requestedLayout >= 0 ? requestedLayout : metadataLayout >= 0 ? metadataLayout : namedLayout(name);
        int degrees = requestedDegrees != 0 ? requestedDegrees : metadataDegrees > 0 ? metadataDegrees : named;
        String reason = requestedDegrees != 0 ? AppText.MANUAL_SELECTION.text() : metadataDegrees > 0 ? AppText.VIDEO_METADATA.text() : named != 0 ? AppText.FILE_NAME.text() : "";
        double ratio = width > 0 && height > 0 && Float.isFinite(pixelRatio) && pixelRatio > 0
            ? width * (double) pixelRatio / height : 0;
        if (degrees == FISHEYE_180) return new Selection(degrees, 2, reason);
        if (degrees == STEREO_FISHEYE_180) {
            // A mono container flag must not collapse two lens images into the same eye.
            // Explicit SBS/TB wins, then stereo metadata, filename, and finally aspect ratio.
            int namedStereo = namedLayout(name);
            layout = requestedLayout == 0 || requestedLayout == 1 ? requestedLayout
                : metadataLayout == 0 || metadataLayout == 1 ? metadataLayout
                : namedStereo == 0 || namedStereo == 1 ? namedStereo : ratio > 0 && ratio < 1 ? 1 : 0;
            return new Selection(degrees, layout, reason);
        }
        if (degrees == CUBEMAP || degrees == EAC) {
            if (layout < 0) {
                double monoRatio = degrees == CUBEMAP ? 1.5 : 16.0 / 9.0;
                layout = near(ratio, monoRatio * 2) ? 0 : near(ratio, monoRatio / 2) ? 1 : 2;
            }
            return new Selection(degrees, layout, reason);
        }
        // A 2:1 frame can be either mono 360 or SBS 180. Never decide from that alone.
        if (degrees == 0 && metadataDegrees >= 0 && ratio > 0) {
            if (layout >= 0) {
                double eyeRatio = ratio * (layout == 0 ? .5 : layout == 1 ? 2 : 1);
                if (near(eyeRatio, 2)) degrees = 360;
                else if (near(eyeRatio, 1)) degrees = 180;
            } else if (near(ratio, 4)) {
                degrees = 360; layout = 0;
            }
            if (degrees != 0) reason = AppText.ESTIMATED_FROM_ASPECT_RATIO.text();
        }
        if (degrees == 0) { degrees = 180; reason = AppText.UNRECOGNIZED_USING.text(); }
        if (layout < 0) {
            if (degrees == 360) layout = near(ratio, 4) ? 0 : near(ratio, 1) ? 1 : 2;
            else layout = near(ratio, 1) ? 2 : near(ratio, .5) ? 1 : 0;
        }
        if (metadataDegrees < 0) reason += AppText.THIS_PROJECTION_TAG_IS_NOT_YET.text();
        return new Selection(degrees, layout, reason);
    }

    /** Media3 Format.projectionData contains the MP4 proj box. Returns a source ID, 0 unknown, -1 unsupported. */
    public static int metadataDegrees(byte[] data) {
        if (data == null || data.length < 8 || data.length > 1024 * 1024) return 0;
        ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        long rootSize = Integer.toUnsignedLong(bytes.getInt());
        if (bytes.getInt() != 0x70726f6a || rootSize != data.length) return 0; // proj
        int result = 0;
        while (bytes.remaining() >= 8) {
            int start = bytes.position();
            long size = Integer.toUnsignedLong(bytes.getInt());
            int type = bytes.getInt();
            if (size < 8 || size > data.length - start) return 0;
            int end = start + (int) size;
            if (type == 0x65717569) { // equi, version/flags plus 0.32 crop fractions
                if (size != 28 || bytes.getInt() != 0) return -1;
                long top = Integer.toUnsignedLong(bytes.getInt()), bottom = Integer.toUnsignedLong(bytes.getInt());
                long left = Integer.toUnsignedLong(bytes.getInt()), right = Integer.toUnsignedLong(bytes.getInt());
                if (top != 0 || bottom != 0) return -1;
                if (left == 0 && right == 0) result = 360;
                else if (Math.abs(left - 0x40000000L) <= 1 && Math.abs(right - 0x40000000L) <= 1) result = 180;
                else return -1;
            } else if (type == 0x70726864) { // prhd: nonzero pose requires additional transforms
                if (size != 24 || bytes.getInt() != 0 || bytes.getInt() != 0
                        || bytes.getInt() != 0 || bytes.getInt() != 0) return -1;
            } else if (type == 0x63626d70) { // cbmp: standard 3x2, unpadded faces
                if (size != 20 || bytes.getInt() != 0 || bytes.getInt() != 0 || bytes.getInt() != 0) return -1;
                result = CUBEMAP;
            } else if (type == 0x6d736870) return -1; // Arbitrary mesh requires its own geometry.
            bytes.position(end);
        }
        return bytes.remaining() == 0 ? result : 0;
    }
}
