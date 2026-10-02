package ir.clubcore.ai;

import java.util.List;
import java.util.Locale;

/** Rule-based Persian content used when Claude is not available. */
final class FallbackTexts {

    private FallbackTexts() {
    }

    static String workoutPlan(String goal, String level, int days) {
        String g = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        boolean loss = g.contains("کاهش") || g.contains("چربی") || g.contains("لاغر");
        boolean mass = g.contains("حجم") || g.contains("عضله") || g.contains("قدرت");
        int d = Math.max(2, Math.min(6, days));
        String sets = "مبتدی".equals(level) ? "۳ ست × ۱۲ تکرار" : "پیشرفته".equals(level) ? "۵ ست × ۶-۸ تکرار" : "۴ ست × ۸-۱۰ تکرار";
        List<String> templates = mass
                ? List.of("سینه و پشت بازو: پرس سینه هالتر، پرس بالاسینه دمبل، فلای، پشت بازو سیم‌کش",
                        "پشت و جلو بازو: بارفیکس یا لت، زیربغل هالتر خم، جلو بازو هالتر، چکشی",
                        "پا: اسکوات، پرس پا، جلو پا ماشین، پشت پا ماشین، ساق ایستاده",
                        "سرشانه و شکم: پرس سرشانه دمبل، نشر جانب، فیس‌پول، کرانچ و پلانک",
                        "تمام‌بدن سنگین: ددلیفت، پرس سینه، اسکوات جلو، بارفیکس",
                        "نقاط ضعف + کاردیو سبک ۲۰ دقیقه")
                : loss
                        ? List.of("سیرکویی تمام‌بدن: اسکوات، شنا، روئینگ دمبل، لانج، پلانک + ۲۰ دقیقه کاردیو",
                                "کاردیو اینتروال (HIIT) ۲۵ دقیقه + تمرینات شکم",
                                "بالاتنه: پرس سینه، لت، پرس سرشانه، جلو و پشت بازو + ۱۵ دقیقه تردمیل شیب‌دار",
                                "پایین‌تنه: اسکوات گابلت، ددلیفت رومانیایی، پرس پا، هیپ‌تراست + ۱۵ دقیقه دوچرخه",
                                "کاردیو یکنواخت ۴۵ دقیقه با شدت متوسط",
                                "سیرکویی سبک + کشش و تحرک‌پذیری")
                        : List.of("تمام‌بدن A: اسکوات، پرس سینه، لت، پلانک",
                                "کاردیو ۳۰ دقیقه + کشش",
                                "تمام‌بدن B: ددلیفت رومانیایی، پرس سرشانه، روئینگ، لانج",
                                "تمرین عملکردی: کتل‌بل، طناب، باکس‌جامپ",
                                "شنا یا دوچرخه ۴۰ دقیقه",
                                "یوگا یا تحرک‌پذیری");
        StringBuilder sb = new StringBuilder();
        sb.append("## برنامه تمرینی پیشنهادی (").append(d).append(" روز در هفته)\n\n");
        sb.append("هدف: ").append(goal == null || goal.isBlank() ? "آمادگی عمومی" : goal).append(" — سطح: ")
                .append(level == null ? "متوسط" : level).append("\n\n");
        for (int i = 0; i < d; i++) {
            sb.append("**روز ").append(i + 1).append(":** ").append(templates.get(i)).append("\n\n");
        }
        sb.append("- حجم پیشنهادی هر حرکت: ").append(sets).append("\n");
        sb.append("- قبل از تمرین ۱۰ دقیقه گرم کردن و بعد از آن ۵ دقیقه سرد کردن فراموش نشود.\n");
        sb.append("- هر ۴ هفته وزنه یا تکرارها را به‌تدریج افزایش دهید.\n\n");
        sb.append("_این برنامه به‌صورت خودکار و بدون هوش مصنوعی تولید شده است؛ پیش از اجرا با مربی هماهنگ کنید._");
        return sb.toString();
    }

    static String retentionSms(String firstName, String gym, boolean expired) {
        return expired
                ? firstName + " عزیز، دلمان برایت تنگ شده! با تمدید اشتراک در " + gym
                        + " این هفته، از تخفیف ویژه بازگشت بهره‌مند شو."
                : firstName + " عزیز، مدتی است در " + gym
                        + " نمی‌بینیمت. برنامه تمرینی‌ات منتظر توست؛ برای مشاوره رایگان با مربی به پذیرش سر بزن.";
    }

    static String memberChat(String message, String name, String stats) {
        String m = message == null ? "" : message;
        String tip;
        if (m.contains("تغذیه") || m.contains("رژیم") || m.contains("غذا") || m.contains("پروتئین")) {
            tip = "برای بیشتر اهداف، مصرف روزانه ۱.۶ تا ۲ گرم پروتئین به ازای هر کیلوگرم وزن بدن، نوشیدن کافی آب و "
                    + "خواب ۷ تا ۹ ساعت توصیه می‌شود. برای رژیم دقیق با متخصص تغذیه مشورت کنید.";
        } else if (m.contains("برنامه") || m.contains("تمرین")) {
            tip = "می‌توانید از بخش «برنامه تمرینی» یک برنامه پیشنهادی بسازید یا از مربی خود درخواست برنامه کنید.";
        } else if (m.contains("درد") || m.contains("آسیب")) {
            tip = "در صورت درد یا آسیب، تمرین آن ناحیه را متوقف کنید و حتماً با پزشک یا فیزیوتراپ مشورت کنید.";
        } else {
            tip = "نظم در تمرین مهم‌ترین عامل پیشرفت است؛ هدف هفته‌ای ۳ تا ۴ جلسه را دنبال کنید.";
        }
        return name + " عزیز، دستیار هوشمند در حال حاضر به سرویس هوش مصنوعی متصل نیست، اما:\n\n" + tip
                + "\n\n" + stats;
    }
}
