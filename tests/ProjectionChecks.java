import dev.astriavr.player.VideoProjection;
import java.nio.ByteBuffer;

public final class ProjectionChecks {
    private static int checks;
    private static void equal(int actual, int expected, String label) {
        checks++;
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
    private static byte[] box(String type, byte[] payload) {
        return ByteBuffer.allocate(payload.length + 8).putInt(payload.length + 8)
            .put(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).put(payload).array();
    }
    private static byte[] equi(int top, int bottom, int left, int right) {
        return box("proj", box("equi", ByteBuffer.allocate(20).putInt(0).putInt(top)
            .putInt(bottom).putInt(left).putInt(right).array()));
    }
    private static void selection(int p, int l, int mp, int ml, String name, int w, int h,
            int expectedProjection, int expectedLayout, String label) {
        VideoProjection.Selection result = VideoProjection.resolve(p, l, mp, ml, name, w, h, 1);
        equal(result.degrees, expectedProjection, label + " projection");
        equal(result.layout, expectedLayout, label + " layout");
    }
    public static void main(String[] args) {
        equal(VideoProjection.isOrdinaryCover(-1,0,"holiday.mp4",1920,1080) ? 1 : 0,1,"unplayed ordinary cover stays flat");
        equal(VideoProjection.isOrdinaryCover(-1,0,"wide.mp4",3840,1920) ? 1 : 0,0,"untagged 2:1 cover matches VR playback detection");
        equal(VideoProjection.isOrdinaryCover(-1,0,"VR180_SBS.mp4",3840,1920) ? 1 : 0,0,"named VR cover stays projected");
        equal(VideoProjection.isOrdinaryCover(-1,0,"recording.mp4",7680,1920) ? 1 : 0,0,"4:1 stereo panorama remains VR");
        equal(VideoProjection.isOrdinaryCover(0,0,"clip.mp4",1920,1080) ? 1 : 0,0,"manual stereo layout keeps VR cover");
        equal(VideoProjection.isOrdinaryCover(2,180,"clip.mp4",1920,1080) ? 1 : 0,0,"manual projection keeps VR cover");
        equal(VideoProjection.isOrdinaryCover(2,0,"VR360.mp4",3840,1920) ? 1 : 0,1,"explicit flat view overrides filename");
        equal(VideoProjection.metadataDegrees(box("proj",box("cbmp",ByteBuffer.allocate(12).putInt(0).putInt(0).putInt(0).array()))),VideoProjection.CUBEMAP,"valid SV3D cubemap selects its pole convention");
        int fish = VideoProjection.STEREO_FISHEYE_180;
        equal(VideoProjection.valid(fish) ? 1 : 0, 1, "stereo fisheye survives preference validation");
        equal(VideoProjection.forViewingMode(VideoProjection.FISHEYE_180, false), fish, "old manual headset option becomes stereo");
        equal(VideoProjection.forViewingMode(VideoProjection.FISHEYE_180, true), VideoProjection.FISHEYE_180, "phone retains mono fisheye choice");
        equal(VideoProjection.forViewingMode(fish, true), fish, "phone supports stereo source");
        equal(VideoProjection.forViewingMode(0, false), 0, "mode switch leaves automatic detection alone");
        equal(java.util.Arrays.stream(VideoProjection.choices(false)).filter(p -> p == VideoProjection.FISHEYE_180).count() == 0 ? 1 : 0, 1, "headset hides mono option");
        equal(java.util.Arrays.stream(VideoProjection.choices(true)).filter(VideoProjection::isFisheye).count() == 2 ? 1 : 0, 1, "phone offers both fisheye types");
        selection(fish,-1,0,-1,"clip.mp4",4000,2000,180,0,"stereo fisheye defaults to SBS");
        selection(fish,-1,0,-1,"clip.mp4",2000,4000,180,1,"portrait fisheye infers TB");
        selection(fish,1,0,0,"clip_SBS.mp4",4000,2000,180,1,"manual TB overrides metadata and filename");
        selection(fish,0,0,1,"clip_TB.mp4",2000,4000,180,0,"manual SBS overrides metadata and filename");
        selection(fish,-1,0,1,"clip.mp4",4000,2000,180,1,"stereo metadata wins over shape");
        selection(fish,2,0,2,"clip_TB.mp4",2000,2000,180,1,"stale mono choice and metadata cannot collapse stereo");
        selection(VideoProjection.FISHEYE_180,0,0,0,"clip.mp4",2000,2000,180,2,"explicit single fisheye remains mono");
        for (String name : new String[]{"dual_fisheye_180.mp4", "VR180_fisheye_SBS.mp4", "fisheye180_TB.mp4", "stereofisheye.mp4", "双鱼眼180.mp4", "双目鱼眼.mp4"}) {
            equal(VideoProjection.namedProjection(name), fish, "stereo filename " + name);
            equal(VideoProjection.resolve(0,-1,0,-1,name,4000,2000,1).projection, fish, "automatic source ID " + name);
        }
        for (String name : new String[]{"dual_fisheye_360.mp4", "dfisheye360.mp4", "双目鱼眼360.mp4", "dual_fisheye.mp4"})
            equal(VideoProjection.isFisheye(VideoProjection.namedProjection(name)) ? 1 : 0, 0, "360 or ambiguous dual lens is not stereo 180 " + name);
        equal(VideoProjection.namedProjection("single_fisheye_180.mp4"), VideoProjection.FISHEYE_180, "single lens filename remains mono");
        equal(VideoProjection.isFlatCover(2,0) ? 1 : 0,1,"explicit ordinary cover");
        equal(VideoProjection.isFlatCover(-1,0) ? 1 : 0,0,"unplayed auto video retains VR detection");
        equal(VideoProjection.isFlatCover(2,180) ? 1 : 0,0,"mono VR180 is not ordinary video");
        equal(VideoProjection.isFlatCover(2,360) ? 1 : 0,0,"mono VR360 is not ordinary video");
        selection(0,-1,0,-1,"旅行_360.mp4",3840,1920,360,2,"360 mono filename");
        selection(0,-1,0,-1,"VR360_TB.mp4",4096,4096,360,1,"360 TB filename");
        selection(0,-1,0,-1,"test_360_SBS.mp4",7680,1920,360,0,"360 SBS filename");
        selection(0,-1,0,-1,"VR180_SBS.mp4",3840,1920,180,0,"180 SBS filename");
        selection(0,-1,0,-1,"test_180_TB.mp4",1920,3840,180,1,"180 TB filename");
        selection(0,-1,0,-1,"test_180_mono.mp4",1920,1920,180,2,"180 mono filename");
        selection(0,-1,360,1,"wrong_180_SBS.mp4",4096,4096,360,1,"metadata wins");
        selection(180,0,360,1,"test_360_TB.mp4",4096,4096,180,0,"manual wins");
        selection(0,-1,0,-1,"unmarked.mp4",3840,1920,180,0,"ambiguous 2:1 preserves 180 SBS");
        equal(VideoProjection.isVrVideo("recording.mp4",false,-1,3840,1920,1) ? 1 : 0,1,"reported untagged 3840x1920 enters VR");
        equal(VideoProjection.isVrVideo("recording.mp4",false,-1,1920,1920,2) ? 1 : 0,1,"pixel aspect ratio participates in VR detection");
        for (int[] size : new int[][]{{1920,1080},{3840,2160},{2560,1080},{1080,1920},{1920,1920},{0,0},{-1,1920}})
            equal(VideoProjection.isVrVideo("recording.mp4",false,-1,size[0],size[1],1) ? 1 : 0,0,"ordinary or unknown size " + size[0] + "x" + size[1]);
        for (float pixelRatio : new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
            equal(VideoProjection.isVrVideo("recording.mp4",false,-1,3840,1920,pixelRatio) ? 1 : 0,0,"invalid pixel ratio cannot imply VR");
        equal(VideoProjection.isFlatCover(2,0) ? 1 : 0,1,"explicit ordinary cover still wins for a saved choice");
        for (String name : new String[]{"clip_VR180SBS.mp4","clip_1803DSBS.mp4","clip_3D180_LR.mp4"}) {
            equal(VideoProjection.isVrVideo(name,false,-1,1920,1080,1) ? 1 : 0,1,"compact filename enters VR " + name);
            selection(0,-1,0,-1,name,1920,1080,180,0,"compact filename " + name);
        }
        selection(0,-1,0,-1,"clip_VR360TB.mp4",4096,4096,360,1,"compact 360 TB");
        selection(0,-1,0,-1,"风景_180_左右格式.mp4",1920,1080,180,0,"Chinese SBS layout");
        selection(0,-1,0,-1,"风景_360_上下格式.mp4",1920,1080,360,1,"Chinese TB layout");
        equal(VideoProjection.namedLayout("clip_HTB.mp4"),1,"half top-bottom layout");
        for (String name : new String[]{"sample_360p_1180_3600.mp4","driver.mp4","club_tbrown.mp4","idVR180SBSxyz.mp4"})
            equal(VideoProjection.isVrVideo(name,false,-1,1920,1080,1) ? 1 : 0,0,"partial words and serial numbers remain ordinary " + name);
        selection(0,-1,0,-1,"unmarked.mp4",7680,1920,360,0,"4:1 inference");
        selection(0,-1,0,2,"unmarked.mp4",3840,1920,360,2,"known mono plus 2:1");
        selection(0,-1,0,0,"unmarked.mp4",3840,1920,180,0,"known SBS plus 2:1");
        selection(360,-1,0,-1,"unmarked.mp4",4096,4096,360,1,"manual 360 auto TB");
        selection(0,-1,0,-1,"new.mp4",0,0,180,0,"new source clears previous detection");
        selection(0,-1,-1,-1,"mesh.mp4",7680,1920,180,0,"unsupported metadata suppresses ratio detection");
        equal(VideoProjection.namedDegrees("sample_360p_1180_3600.mp4"),0,"resolution and serial numbers ignored");
        equal(VideoProjection.namedDegrees("sample_180_360.mp4"),0,"conflicting names ignored");
        equal(VideoProjection.namedLayout("club_tbrown.mp4"),-1,"partial words ignored");
        equal(VideoProjection.namedLayout("sample_SBS_TB.mp4"),-1,"conflicting layouts ignored");
        equal(VideoProjection.metadataDegrees(equi(0,0,0,0)),360,"MP4 v2 full sphere");
        equal(VideoProjection.metadataDegrees(equi(0,0,0x40000000,0x40000000)),180,"MP4 v2 centered half sphere");
        equal(VideoProjection.metadataDegrees(equi(1,0,0,0)),-1,"vertical crop is unsupported");
        equal(VideoProjection.metadataDegrees(equi(0,0,0x80000000,0)),-1,"off-center crop is unsupported");
        equal(VideoProjection.metadataDegrees(box("proj", box("mshp", new byte[0]))),-1,"mesh is not guessed as 360");
        equal(VideoProjection.metadataDegrees(box("proj", box("cbmp", new byte[0]))),-1,"cubemap is not guessed as 360");
        equal(VideoProjection.metadataDegrees(null),0,"no metadata");
        byte[] valid = equi(0,0,0,0);
        for (int n=0;n<valid.length;n++) equal(VideoProjection.metadataDegrees(java.util.Arrays.copyOf(valid,n)),0,"truncated metadata " + n);
        byte[] bad = valid.clone(); ByteBuffer.wrap(bad).putInt(8,0);
        equal(VideoProjection.metadataDegrees(bad),0,"zero size does not loop");
        ByteBuffer.wrap(bad).putInt(8,-1);
        equal(VideoProjection.metadataDegrees(bad),0,"oversized box does not overflow");
        java.util.Random random = new java.util.Random(360);
        for (int n=0;n<1000;n++) {
            byte[] payload = new byte[random.nextInt(128)]; random.nextBytes(payload);
            VideoProjection.metadataDegrees(box("proj", payload)); checks++;
        }
        System.out.println("PASS: " + checks + " projection detection / metadata checks");
    }
}
