import dev.astriavr.player.Optics;
import dev.astriavr.player.PoseMath;

/** Small executable checks of production math. No Android device or testing framework required. */
public class GeometryChecks {
    private static int checks;
    private static void close(double actual, double expected, double tolerance, String name) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance)
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }
    private static void yes(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    private static void vector(float[] matrix, float x, float y, float z, float ex, float ey, float ez, String name) {
        close(matrix[0]*x + matrix[3]*y + matrix[6]*z, ex, 1e-5, name + " x");
        close(matrix[1]*x + matrix[4]*y + matrix[7]*z, ey, 1e-5, name + " y");
        close(matrix[2]*x + matrix[5]*y + matrix[8]*z, ez, 1e-5, name + " z");
    }
    public static void main(String[] args) {
        // Screen viewing must preserve shape and never inherit headset circles/IPD.
        Optics.PhoneViewport ultrawide = new Optics.PhoneViewport(2800,1260,false);
        close(ultrawide.width,2240,0,"16:9 on 20:9 width");
        close(ultrawide.height,1260,0,"16:9 on 20:9 height");
        close(ultrawide.x,280,0,"16:9 equal side bars");
        for (int[] screen : new int[][]{{2800,1260},{2400,1080},{1920,1080},{2560,1440},{2048,1536},{1260,2800},{2799,1259}}) {
            for (boolean fill : new boolean[]{false,true}) {
                Optics.PhoneViewport view = new Optics.PhoneViewport(screen[0],screen[1],fill);
                yes(view.width > 0 && view.height > 0,"positive phone viewport");
                close(view.x * 2 + view.width,screen[0],1,"phone horizontal center");
                close(view.y * 2 + view.height,screen[1],1,"phone vertical center");
                yes(view.x >= 0 && view.y >= 0 && view.x + view.width <= screen[0]
                    && view.y + view.height <= screen[1],"phone viewport fits window");
                if (fill) {
                    close(view.width,screen[0],0,"full screen uses all width");
                    close(view.height,screen[1],0,"full screen uses all height");
                } else close(view.width / (double)view.height,16.0/9,0.003,"phone default aspect");
                for (int fov : new int[]{60,70,80,90,100,110,120,130}) {
                    float[] plane = Optics.phonePlane(view.width,view.height,fov);
                    close(Math.toDegrees(Math.atan(plane[0])) * 2,fov,1e-4,"phone horizontal FOV");
                    close(plane[0] / view.width,plane[1] / view.height,1e-8,"equal pixel scale avoids stretching");
                    close(Optics.phoneDragAngle(view.width,0,view.width,plane[0]),fov,1e-4,"drag across image spans FOV");
                    close(Optics.phoneDragAngle(view.width/2f+10,view.width/2f,view.width,plane[0]),
                        Optics.phoneDragAngle(view.height/2f+10,view.height/2f,view.height,plane[1]),1e-4,"equal x/y drag sensitivity");
                }
            }
        }
        close(Optics.clampPhoneFov(0),60,0,"phone minimum FOV");
        close(Optics.clampPhoneFov(180),130,0,"phone maximum FOV");
        close(Optics.clampPhoneFov(90),90,0,"phone default FOV");
        close(Optics.clampPhoneFov(65),70,0,"old phone FOV snaps to new step");
        for (int angle=60;angle<=130;angle+=10) {
            close(Optics.stepPhoneFov(angle,1),Math.min(angle+10,130),0,"phone FOV increment");
            close(Optics.stepPhoneFov(angle,-1),Math.max(angle-10,60),0,"phone FOV decrement");
        }
        close(Optics.phoneDragAngle(0,10,0,1),0,0,"unmeasured view drag is safe");
        close(Math.hypot(Optics.WIDTH_CM, Optics.HEIGHT_CM), 17.272, 1e-9, "6.8 inch diagonal");
        close(Optics.WIDTH_CM / Optics.HEIGHT_CM, 20.0/9.0, 1e-9, "20:9");
        for (int width : new int[]{2800, 2400}) {
            int height = width * 9 / 20;
            for (int step = 0; step <= 30; step++) {
                float cm = 5f + step / 10f;
                Optics.Layout layout = new Optics.Layout(width, height, cm);
                close(layout.eyeWidth * Optics.WIDTH_CM / width, 5, .004, "physical circle width");
                close(layout.eyeHeight * Optics.HEIGHT_CM / height, 5, .004, "physical circle height");
                close((layout.rightX - layout.leftX) * Optics.WIDTH_CM / width, cm, .008, "center distance");
                close(layout.leftX + layout.rightX + layout.eyeWidth, width, 1, "symmetric eyes");
                yes(layout.leftX >= 0 && layout.rightX + layout.eyeWidth <= width, "eyes fit screen");
                yes(layout.leftX + layout.eyeWidth <= layout.rightX, "no overlap at 5cm");
                yes(layout.y >= 0 && layout.y + layout.eyeHeight <= height, "vertical fit");
            }
        }
        close(new Optics.Layout(2800,1260,6.5f).eyeWidth, 889, 0, "target circle diameter px");
        close(Optics.clampIpd(4), 5, 0, "lower limit");
        close(Optics.clampIpd(9), 8, 0, "upper limit");
        close(Optics.clampIpd(Float.NaN), 6.5, 0, "bad saved preference");
        close(Math.toDegrees(Math.atan(Optics.TAN_HALF_FOV)), 44, 1e-5, "default circle edge is 44 degrees from center");

        close(Optics.diameter(Float.NaN),5,0,"invalid saved diameter defaults");
        close(Optics.diameter(Float.POSITIVE_INFINITY),5,0,"infinite diameter defaults");
        close(Optics.diameter(-1),4,0,"diameter lower limit");
        close(Optics.diameter(20),7,0,"diameter upper limit");
        close(Optics.diameter(4.24f),4.2,1e-6,"diameter rounds down");
        close(Optics.diameter(4.3f),4.4,1e-6,"diameter rounds to 0.2 centimeter");
        close(Optics.maxDiameter(9,4),4,0,"minimum screen supports 4cm");
        close(Optics.maxDiameter(8.9,4),0,0,"minimum IPD constrains narrow screen");
        close(Optics.maxDiameter(14,7),7,0,"7cm exact physical fit");
        close(Optics.maxDiameter(13.9,7),6.8,1e-6,"width limits diameter");
        close(Optics.maxDiameter(15,6.7),6.6,1e-6,"height limits diameter");
        close(Optics.maxDiameter(Double.NaN,7),0,0,"invalid width cannot fit");
        close(Optics.maxDiameter(15,Double.POSITIVE_INFINITY),0,0,"invalid height cannot fit");
        close(Optics.fitDiameter(7,15,6.7),6.6,1e-6,"resize adapts oversized circle");
        close(Optics.fitDiameter(4.5f,9,4),4,0,"default adapts to smallest supported screen");
        for (int step=20; step<=35; step++) { float d=step/5f;
            close(Optics.diameter(d),d,0,"all sixteen steps persist exactly");
            close(Optics.fitDiameter(d,16,8),d,0,"larger screen preserves selected size");
        }
        for (double[] screen : new double[][]{{Optics.WIDTH_CM, Optics.HEIGHT_CM}, {15, 7.5}, {14.5, 6.5}, {9,4}, {14,7}, {13.9,7}, {24,15}}) {
            for (int width : new int[]{2800, 2799, 1400}) {
                int height = (int)Math.round(width * screen[1] / screen[0]);
                for (int step=20; step<=35; step++) { float requested=step/5f;
                    float diameter = Optics.fitDiameter(requested, screen[0], screen[1]);
                    yes(Optics.fits(diameter, screen[0], screen[1]), "selected eye circles fit");
                    float lower = Optics.minIpd(diameter), upper = Optics.maxIpd(diameter, screen[0]);
                    close(Optics.stepIpd(5, -1, diameter, screen[0]), lower, 0, "diameter-linked lower IPD");
                    close(Optics.stepIpd(8, 1, diameter, screen[0]), upper, 0, "screen-linked upper IPD");
                    for (int tenth = Math.round(lower*10); tenth <= Math.round(upper*10); tenth++) {
                        Optics.Layout area = new Optics.Layout(width, height, tenth/10f, diameter, screen[0], screen[1]);
                        close(area.eyeWidth * screen[0] / width, diameter, .012, "physical circle width for all sizes");
                        close(area.eyeHeight * screen[1] / height, diameter, .012, "physical circle height for all sizes");
                        close(area.leftX + area.rightX + area.eyeWidth, width, 1, "whole pair remains centered");
                        close(area.y * 2 + area.eyeHeight, height, 1, "vertical center remains centered");
                        yes(area.leftX >= 0 && area.rightX + area.eyeWidth <= width, "no horizontal crop");
                        yes(area.y >= 0 && area.y + area.eyeHeight <= height, "no vertical crop");
                        yes(area.leftX + area.eyeWidth <= area.rightX, "no eye overlap at minimum IPD");
                        close((area.rightX-area.leftX)*screen[0]/width, tenth/10f, .012, "actual IPD matches requested distance");
                    }
                }
            }
        }
        yes(!Optics.fits(6.5f, 13, 6), "short screen rejects oversized circles");
        yes(!Optics.fits(6.5f, 12, 7), "narrow screen rejects overlapping circles");
        yes(Optics.detectedScreen(2800,1260,0,400) == null, "invalid DPI is not applied");
        yes(Optics.detectedScreen(2800,1260,Float.NaN,400) == null, "NaN DPI rejected");
        double[] landscape = Optics.detectedScreen(2800,1260,450,460);
        double[] portrait = Optics.detectedScreen(1260,2800,460,450);
        close(landscape[0], portrait[0], 1e-8, "detection orientation-independent width");
        close(landscape[1], portrait[1], 1e-8, "detection orientation-independent height");
        close(landscape[0], 2800.0/450*2.54, 1e-8, "physical DPI not logical DPI");

        close(Optics.parseScreenCm("15.75"), 15.75, 0, "custom hundredth cm accepted");
        close(Optics.parseScreenCm("7.1"), 7.1, 0, "custom one decimal accepted");
        for (String invalid : new String[]{"15.751", "0", "-7.1", "NaN", "1e1", "", ".5", "51.00"}) {
            yes(Double.isNaN(Optics.parseScreenCm(invalid)), "custom invalid precision or value rejected");
        }

        float[] left = Optics.eyeRect(0,0,false), right = Optics.eyeRect(0,1,false);
        close(left[0]+.5*left[2], .25, 0, "SBS left eye center");
        close(right[0]+.5*right[2], .75, 0, "SBS right eye center");
        close(Optics.eyeRect(0,0,true)[0], .5, 0, "swap SBS");
        float[] top = Optics.eyeRect(1,0,false), bottom = Optics.eyeRect(1,1,false);
        close(top[1]+.5*top[3], .75, 0, "TB left eye is top");
        close(bottom[1]+.5*bottom[3], .25, 0, "TB right eye is bottom");
        close(Optics.eyeRect(2,1,true)[2], 1, 0, "mono keeps full width");

        float[] identity = new float[9]; PoseMath.identity(identity);
        float[] result = new float[9];
        float[] yaw90 = {0,0,1, 0,1,0, -1,0,0};
        PoseMath.relative(identity, yaw90, result);
        vector(result, 0,0,-1, -1,0,0, "head yaw turns viewing ray");
        PoseMath.relative(yaw90, yaw90, result);
        vector(result, 0,0,-1, 0,0,-1, "recenter makes current direction front");
        vector(result, 0,1,0, 0,1,0, "recenter resets roll");
        float[] pitch90 = {1,0,0, 0,0,-1, 0,1,0};
        PoseMath.relative(identity, pitch90, result);
        vector(result, 0,0,-1, 0,1,0, "pitch points up");
        // A non-identity reference catches multiplication order and transpose mistakes.
        PoseMath.relative(yaw90, identity, result);
        vector(result, 0,0,-1, 1,0,0, "inverse reference");
        for (int angle = -180; angle <= 180; angle += 5) {
            float c = (float)Math.cos(Math.toRadians(angle)), s = (float)Math.sin(Math.toRadians(angle));
            float[] rotation = {c,0,s, 0,1,0, -s,0,c};
            PoseMath.relative(rotation, rotation, result);
            for (int index = 0; index < 9; index++) close(result[index], identity[index], 1e-5, "recenter throughout full turn");
        }
        System.out.println("PASS: " + checks + " geometry / eye mapping / pose checks");
        System.out.printf(java.util.Locale.ROOT, "2800x1260: eye diameter 889px; 5-8cm centers %.2f-%.2fpx%n",
            new Optics.Layout(2800,1260,5).centerDistance, new Optics.Layout(2800,1260,8).centerDistance);
    }
}
