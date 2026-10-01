import dev.astriavr.player.CoverProjection;
import dev.astriavr.player.PlaylistNavigation;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import javax.imageio.ImageIO;
import java.io.File;

public final class PlaylistChecks {
    private static int checks;
    private static void check(boolean good, String message) {
        checks++;
        if (!good) throw new AssertionError(message);
    }
    private static void near(double a, double b, String message) { check(Math.abs(a-b) < .000001, message); }
    public static void main(String[] args) throws Exception {
        check(CoverProjection.sampleTimeUs(120000) == 24000000, "two-minute clip samples 20 percent");
        check(CoverProjection.sampleTimeUs(60000) == 12000000, "one-minute clip samples 12 seconds");
        check(CoverProjection.sampleTimeUs(599999) == 119999800, "just below ten minutes samples 20 percent");
        check(CoverProjection.sampleTimeUs(600000) == 120000000, "exact ten-minute duration samples 2:00");
        check(CoverProjection.sampleTimeUs(Long.MAX_VALUE) == 120000000, "long durations cannot overflow");
        check(CoverProjection.sampleTimeUs(0) == 120000000, "unknown duration samples 2:00");
        check(CoverProjection.sampleTimeUs(-1) == 120000000, "unavailable duration samples 2:00");
        for (int duration = 1; duration < 600000; duration += 137) {
            long sample = CoverProjection.sampleTimeUs(duration);
            check(sample > 0 && sample < duration * 1000L, "short clip has a nonzero in-range timestamp");
        }
        for (int layout = 0; layout < 3; layout++) for (int degrees : new int[]{180, 360}) {
            double[] center = CoverProjection.uv(160, 90, 320, 180, layout, degrees);
            near(center[0], layout == 0 ? .75 : .5, "right-eye horizontal center");
            near(center[1], layout == 1 ? .75 : .5, "right-eye vertical center");
            for (int y = 0; y <= 180; y += 15) for (int x = 0; x <= 320; x += 16) {
                double[] uv = CoverProjection.uv(x, y, 320, 180, layout, degrees);
                check(uv[0] >= (layout == 0 ? .5 : 0) && uv[0] <= 1, "never samples left SBS eye");
                check(uv[1] >= (layout == 1 ? .5 : 0) && uv[1] <= 1, "never samples top TB eye");
                double[] mirrored = CoverProjection.uv(320-x, 180-y, 320, 180, layout, degrees);
                near(uv[0]+mirrored[0], center[0]*2, "front view horizontally symmetric");
                near(uv[1]+mirrored[1], center[1]*2, "front view vertically symmetric");
            }
        }
        int[] source = new int[400 * 200];
        for (int layout = 0; layout <= 1; layout++) {
            for (int y = 0; y < 200; y++) for (int x = 0; x < 400; x++)
                source[y*400+x] = (layout == 0 ? x >= 200 : y >= 100) ? 0xff146cb1 : 0xffe24235;
            int[] cover = CoverProjection.render(source, 400, 200, layout, 180, 80, 45);
            for (int pixel : cover) check(pixel == 0xff146cb1, "actual resampler contains right eye only");
        }
        PlaylistNavigation nav = new PlaylistNavigation();
        check(nav.isNeutral(), "fresh input is neutral");
        check(nav.key(1, true, false, 0) == 1, "left press");
        check(nav.key(1, true, true, 20) == 0, "OS repeats do not duplicate");
        check(nav.hat(-1, 0, 30) == 0, "HAT duplicates same left press");
        check(nav.repeat(349) == 0 && nav.repeat(350) == 1, "delayed navigation repeat");
        check(nav.repeat(529) == 0 && nav.repeat(530) == 1, "180ms repeat");
        nav.key(1, false, false, 550);
        check(!nav.isNeutral(), "HAT still held after key release");
        nav.hat(0, 0, 560);
        check(nav.isNeutral() && nav.repeat(900) == 0, "final release stops repeat");
        check(nav.key(4, true, false, 1000) == 4, "up confirms");
        check(nav.hat(0, -1, 1001) == 0, "up key/HAT confirm only once");
        check(nav.repeat(5000) == 0, "confirm never repeats");
        check(!nav.isNeutral(), "held confirm is retained across mode change");
        nav.key(4, false, false, 5001); nav.hat(0, 0, 5002);
        check(nav.isNeutral(), "release allows ordinary controls again");
        nav.key(1, true, false, 6000);
        check(nav.key(2, true, false, 6001) == 0 && nav.repeat(7000) == 0, "opposite directions cancel repeats");
        nav.key(2, false, false, 7001);
        check(nav.repeat(7351) == 1, "remaining direction restarts deliberate repeat");
        if (args.length > 0) preview(args[0]);
        System.out.println("PASS: " + checks + " right-eye cover / sample time / playlist navigation checks");
    }
    private static void preview(String output) throws Exception {
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        var g = source.createGraphics();
        g.setColor(new Color(150,35,35)); g.fillRect(0,0,400,400);
        g.setColor(new Color(12,55,82)); g.fillRect(400,0,400,400);
        g.setColor(new Color(40,120,150));
        for (int x=400; x<800; x+=20) g.drawLine(x,0,x,400);
        for (int y=0; y<400; y+=20) g.drawLine(400,y,800,y);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif",Font.BOLD,22));
        g.drawString("LEFT EYE",135,190); g.drawString("RIGHT EYE",535,170);
        g.setColor(new Color(100,255,160)); g.fillRect(596,185,8,30); g.fillRect(585,196,30,8);
        g.dispose();
        int[] pixels = source.getRGB(0,0,800,400,null,0,800);
        int[] view = CoverProjection.render(pixels,800,400,0,180,320,180);
        BufferedImage result = new BufferedImage(840,660,BufferedImage.TYPE_INT_RGB);
        g=result.createGraphics(); g.setColor(new Color(8,15,24)); g.fillRect(0,0,840,660);
        g.drawImage(source,20,20,null);
        BufferedImage thumb = new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB);
        thumb.setRGB(0,0,320,180,view,0,320); g.drawImage(thumb,260,455,null);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif",Font.PLAIN,16));
        g.drawString("Synthetic SBS source -> fixed 90-degree right-eye front cover",160,444); g.dispose();
        ImageIO.write(result,"png",new File(output));
    }
}
