import dev.astriavr.player.AppText;
import dev.astriavr.player.VideoProjection;
import java.util.*;
import java.util.regex.*;

public class LanguageChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static List<String> tokens(String text, String regex) {
        List<String> result = new ArrayList<>();
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) result.add(matcher.group());
        Collections.sort(result);
        return result;
    }
    public static void main(String[] args) {
        check(!AppText.isEnglish(), "Initial catalog language is Chinese");
        Object[] values = {"片名 $1 \\ {1}.mp4", "720", "30", "4", "5", "6", "7", "8", "9"};
        for (AppText key : AppText.values()) {
            AppText.setEnglish(false); String zh = key.text();
            AppText.setEnglish(true); String en = key.text();
            check(!zh.isBlank() && !en.isBlank(), key + " must have both translations");
            check(!Pattern.compile("[\\p{IsHan}]").matcher(en).find(), key + " contains untranslated Chinese");
            check(tokens(zh, "\\{\\d+\\}").equals(tokens(en, "\\{\\d+\\}")), key + " argument mismatch");
            check(tokens(zh, "%[.0-9]*[dfs]").equals(tokens(en, "%[.0-9]*[dfs]")), key + " format mismatch");
            for (boolean english : new boolean[]{false, true}) {
                AppText.setEnglish(english);
                String result = key.text(values);
                if (key.text().contains("{0}"))
                    check(result.contains(values[0].toString()), key + " must not re-parse or translate inserted filenames");
            }
        }
        for (boolean english : new boolean[]{false, true}) {
            AppText.setEnglish(english);
            check(VideoProjection.namedProjection("双鱼眼180.mp4") == VideoProjection.STEREO_FISHEYE_180,
                "Chinese filename detection must be independent of UI language");
            check(VideoProjection.namedProjection("VR360_TB.mp4") == 360, "English filename detection");
            check(VideoProjection.isVrVideo("recording.mp4", false, -1, 3840, 1920, 1), "Untagged 2:1 VR detection is language independent");
            check(VideoProjection.namedProjection("clip_VR180SBS.mp4") == 180, "Compact filename detection is language independent");
            check(VideoProjection.label(180).equals(english ? "EQ 180°" : "等距柱状 180°"), "Projection labels");
        }
        AppText.setEnglish(false);
        check(AppText.SETTINGS.text().equals("设置"), "Switch back to Chinese");
        System.out.println("PASS: " + checks + " bilingual catalog / placeholder / filename checks; " + AppText.values().length + " entries");
    }
}
