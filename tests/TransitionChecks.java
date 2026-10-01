import dev.astriavr.player.FovTransition;
import dev.astriavr.player.GamepadState;
import dev.astriavr.player.Optics;
import static dev.astriavr.player.GamepadState.Action;

public class TransitionChecks {
    private static int checks;
    private static void near(double a, double b, double epsilon, String why) {
        checks++;
        if (!Double.isFinite(a) || Math.abs(a-b)>epsilon) throw new AssertionError(why+": "+a+" != "+b);
    }
    private static void yes(boolean value, String why) { near(value?1:0,1,0,why); }
    private static int bit(Action a) { return 1<<a.ordinal(); }
    public static void main(String[] args) {
        for (int fps : new int[]{30,60,90,120,144}) {
            for (int from : new int[]{40,88,120}) for (int to : new int[]{40,48,88,112,120}) {
                FovTransition motion = new FovTransition();
                near(motion.update(from,0,false),from,0,"initial state snaps");
                near(motion.update(to,0,false),from,0,"target does not jump rendered view");
                float previous=from;
                for (int frame=1;frame<=fps;frame++) {
                    long time=frame*1_000_000_000L/fps;
                    float value=motion.update(to,time,false);
                    yes(value>=Math.min(from,to)-.0001 && value<=Math.max(from,to)+.0001,"no overshoot");
                    yes(to>=from ? value>=previous-.0001 : value<=previous+.0001,"monotonic transition");
                    if(time>=FovTransition.DURATION_NS) near(value,to,0,"exact target at all refresh rates");
                    previous=value;
                }
            }
        }
        FovTransition motion=new FovTransition();
        motion.update(88,0,false); motion.update(96,0,false);
        float middle=motion.update(96,100_000_000,false);
        yes(middle>88 && middle<96,"intermediate angles are not quantized");
        near(motion.update(104,100_000_000,false),middle,0,"rapid forward retarget stays continuous");
        float later=motion.update(104,140_000_000,false);
        near(motion.update(80,140_000_000,false),later,0,"reversal stays continuous");
        near(motion.update(80,360_000_000,false),80,0,"reversal lands exactly");
        near(motion.update(90,370_000_000,true),90,0,"mode switch snaps to own target");
        for(float angle=40;angle<=120;angle+=.25f) {
            float[] p=Optics.projection(angle,50);
            near(Math.toDegrees(p[1])*2,angle,.00002,"continuous headset projection uses rendered angle");
        }
        for(float angle=60;angle<=130;angle+=.25f) for(boolean ellipse:new boolean[]{false,true}) {
            float[] edge=Optics.phoneRayAngles(1920,540,1920,1080,angle,60,ellipse);
            near(edge[0]*2,angle,.00005,"phone drag and camera preserve fractional edge FOV");
        }
        for(int mode=0;mode<3;mode++) {
            GamepadState pad=new GamepadState();
            int count=0;
            for(int press=0;press<5;press++) {
                if(mode!=1) count+=Integer.bitCount(pad.leftTriggerKey(true,false));
                if(mode!=0) count+=Integer.bitCount(pad.leftTriggerAxis(1));
                for(int i=0;i<100;i++) {
                    if(mode!=1) near(pad.leftTriggerKey(true,true),0,0,"LT key hold no repeat");
                    if(mode!=0) near(pad.leftTriggerAxis(.7f),0,0,"LT analog hold no repeat");
                }
                if(mode!=1) near(pad.leftTriggerKey(false,false),0,0,"LT release no action");
                if(mode!=0) near(pad.leftTriggerAxis(0),0,0,"LT analog release no action");
            }
            near(count,5,0,"LT once per press including duplicate reports");
        }
        GamepadState pad=new GamepadState();
        near(pad.leftTriggerAxis(1),bit(Action.PLAY_PAUSE),0,"analog first press");
        near(pad.leftTriggerKey(true,false),0,0,"digital after analog deduplicated");
        near(pad.leftTriggerAxis(.3f),0,0,"hysteresis resists trigger jitter");
        pad.clear();
        near(pad.hat(0,-1),bit(Action.FOV_DOWN),0,"D-pad up decreases FOV");
        near(pad.key(Action.FOV_DOWN,true,false),0,0,"FOV key and hat deduplicated");
        for(int i=0;i<5;i++) {
            int output=pad.repeatFov(.05f);
            near(output,i==4?bit(Action.FOV_DOWN):0,0,"hat FOV repeats every 250ms");
        }
        pad.key(Action.FOV_DOWN,false,false);
        yes(pad.hasFovRepeat(),"hat holds after key release");
        pad.hat(0,0); yes(!pad.hasFovRepeat(),"final release stops FOV");
        for(Action action:new Action[]{Action.SPEED_DOWN,Action.SPEED_UP,Action.IPD_DOWN,Action.IPD_UP}) {
            pad.clear(); near(pad.key(action,true,false),bit(action),0,"tuning first press");
            near(pad.key(action,true,true),0,0,"OS repeats suppressed");
            for(int i=0;i<8;i++) near(pad.repeatTuning(.05f),i==7?bit(action):0,0,"tuning deliberate repeat");
            pad.key(action,false,false); near(pad.repeatTuning(.05f),0,0,"tuning release stops");
        }
        for (Action action : new Action[]{Action.SPEED_DOWN, Action.SPEED_UP}) {
            int limit = action == Action.SPEED_DOWN ? 0 : 6;
            int adjacent = action == Action.SPEED_DOWN ? 1 : 5;
            pad.clear();
            near(pad.speedKey(action,true,false,adjacent,0),bit(action),0,"press reaching limit does not arm reset");
            pad.speedKey(action,false,false,limit,40);
            near(pad.speedKey(action,true,false,limit,100),bit(action),0,"first extra tap at limit");
            near(pad.speedKey(action,true,false,limit,110),0,0,"duplicate key down cannot reset");
            pad.speedKey(action,false,false,limit,140);
            near(pad.speedKey(action,true,false,limit,300),bit(Action.SPEED_RESET),0,"second extra tap resets speed");
            for(int i=0;i<30;i++) near(pad.repeatTuning(.05f),0,0,"holding reset tap cannot change normal speed");
            pad.clear();
            pad.speedKey(action,true,false,limit,1000); pad.speedKey(action,false,false,limit,1050);
            near(pad.speedKey(action,true,false,limit,1400),bit(action),0,"slow taps do not reset");
            near(pad.speedKey(action,true,true,limit,1450),0,0,"OS repeat cannot reset");
            pad.speedKey(action,false,false,limit,1460);
            near(pad.speedKey(action,true,false,limit,1500),bit(action),0,"long press clears pending double tap");
            for(int i=0;i<8;i++) pad.repeatTuning(.05f);
            pad.speedKey(action,false,false,limit,1510);
            near(pad.speedKey(action,true,false,limit,1520),bit(action),0,"scheduled hold repeat clears pending tap");
            pad.clear();
            near(pad.speedKey(action,true,false,limit,1600),bit(action),0,"focus loss clears pending tap");
            pad.speedKey(action,false,false,limit,1610);
            near(pad.speedKey(action,true,false,adjacent,1700),bit(action),0,"touch speed change invalidates boundary tap");
        }
        pad.clear();
        pad.speedKey(Action.SPEED_UP,true,false,6,0); pad.speedKey(Action.SPEED_UP,false,false,6,20);
        near(pad.speedKey(Action.SPEED_DOWN,true,false,6,100),bit(Action.SPEED_DOWN),0,"opposite key never resets");
        System.out.println("PASS: "+checks+" FOV transition / continuous projection / controller regression checks");
    }
}
