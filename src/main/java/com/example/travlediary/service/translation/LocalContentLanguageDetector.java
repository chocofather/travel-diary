package com.example.travlediary.service.translation;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.springframework.stereotype.Component;

import java.util.SortedMap;

@Component
public class LocalContentLanguageDetector {
    private static final int MIN_LETTERS = 4;
    private static final double MIN_CONFIDENCE = 0.75d;
    private static final double MIN_DISTANCE = 0.15d;

    private static final String SIMPLIFIED_ONLY =
            "这们个来时国会为发说见长门东车书云风马鸟鱼龙广汉语体学万与业乐乡亲产众优传价儿党兰关兴养兽内写军农冲决况冻净凤击划刘则办务动医华协单卖卫却厂历县叶号后吓听启员园围图圆圣场坏块坚坛坝壮声处备复头夹夺奋奖妇妈孙宁宝实审宪宫宽宾对寻导寿将尔尘层岁岂岗岛岭币帅师帐帘干并庆庄床库应庙废开异弃张弥弯弹强归当录彻忆怀态总恋恒恳恶恼悬惊惧惨惩懒戏户拥择担拟拢拣拥据损摆摇摄敌数断无旧昼显晋晓术机杀杂权条极构枪柜标树样桥梦检楼欢欧步残殴毕气汇汉汤沟没泽洁浅济浓浑测湾湿灭灯灵灾灿炉点炼热爱爷牵犹状独狭狮环现电画畅疗监盖盘着矿码砖礼祷离种积称稳窝竞笔笼简粮纠红纤约级纪纯纲纳纵纷纸纹纽线练组细织终绍经结给络绝统继绩绪续绳维绵综绿缆缘缚缩网罗职联聪肃肠肤肾胆胜胶脏脑脚脱脸腊舰艺节范茧荐荡荣药莲获萝营萧萨蓝虚虫虽虾蚀蚁蚂蚕蛮补装见观规视览觉触誉计订认讨让议讯记讲许论设访证评识诈诉诊词译试诗诚话询该详语误说请诸读课谁调谈谋谢谨谱贝负贡财责贤败货质贩贪贫购贯贴贵贷费贺贼资赋赌赏赔赖赚赛赞赠赵赶趋跃践踊踪躯车轨转轮软轰轻载较辅辆辈辉辐辑输辖辙辽达迁过迈运还进远违连迟适选递逻遗邮邻郑释里鉴针钉钓钙钟钢钥钱钻铁铃铜铝铭银铺链销锁锅锻错锡锦键锯镶长闷闪闭问闯闲间闻阁阅队阳阴阵阶际陆陈险随隐难雾静顶项顺须顾顿颁领颂预频颗题额颜风飞饥饭饮饰饱馆驱验惊骑骗骚鱼鲁鲜鸟鸡鸣鸭鸦麦黄齐齿龄龟";
    private static final String TRADITIONAL_ONLY =
            "這們個來時國會為發說見長門東車書雲風馬鳥魚龍廣漢語體學萬與業樂鄉親產眾優傳價兒黨蘭關興養獸內寫軍農衝決況凍淨鳳擊劃劉則辦務動醫華協單賣衛卻廠歷縣葉號後嚇聽啟員園圍圖圓聖場壞塊堅壇壩壯聲處備復頭夾奪奮獎婦媽孫寧寶實審憲宮寬賓對尋導壽將爾塵層歲豈崗島嶺幣帥師帳簾幹並慶莊床庫應廟廢開異棄張彌彎彈強歸當錄徹憶懷態總戀恆懇惡惱懸驚懼慘懲懶戲戶擁擇擔擬攏揀據損擺搖攝敵數斷無舊晝顯晉曉術機殺雜權條極構槍櫃標樹樣橋夢檢樓歡歐步殘毆畢氣匯漢湯溝沒澤潔淺濟濃渾測灣濕滅燈靈災燦爐點煉熱愛爺牽猶狀獨狹獅環現電畫暢療監蓋盤著礦碼磚禮禱離種積稱穩窩競筆籠簡糧糾紅纖約級紀純綱納縱紛紙紋紐線練組細織終紹經結給絡絕統繼績緒續繩維綿綜綠纜緣縛縮網羅職聯聰肅腸膚腎膽勝膠臟腦腳脫臉臘艦藝節範繭薦蕩榮藥蓮獲蘿營蕭薩藍虛蟲雖蝦蝕蟻螞蠶蠻補裝見觀規視覽覺觸譽計訂認討讓議訊記講許論設訪證評識詐訴診詞譯試詩誠話詢該詳語誤說請諸讀課誰調談謀謝謹譜貝負貢財責賢敗貨質販貪貧購貫貼貴貸費賀賊資賦賭賞賠賴賺賽贊贈趙趕趨躍踐踴蹤軀車軌轉輪軟轟輕載較輔輛輩輝輻輯輸轄轍遼達遷過邁運還進遠違連遲適選遞邏遺郵鄰鄭釋裡鑒針釘釣鈣鐘鋼鑰錢鑽鐵鈴銅鋁銘銀鋪鏈銷鎖鍋鍛錯錫錦鍵鋸鑲長悶閃閉問闖閒間聞閣閱隊陽陰陣階際陸陳險隨隱難霧靜頂項順須顧頓頒領頌預頻顆題額顏風飛飢飯飲飾飽館驅驗驚騎騙騷魚魯鮮鳥雞鳴鴨鴉麥黃齊齒齡龜";

    private final LanguageDetector detector = LanguageDetectorBuilder.fromLanguages(
                    Language.KOREAN, Language.ENGLISH, Language.JAPANESE, Language.CHINESE)
            .withMinimumRelativeDistance(MIN_DISTANCE)
            .build();

    public DetectedLanguage detect(String text) {
        if (text == null || text.isBlank()) {
            return DetectedLanguage.undetermined();
        }

        long letters = text.codePoints().filter(Character::isLetter).count();
        long hangul = text.codePoints().filter(LocalContentLanguageDetector::isHangul).count();
        long kana = text.codePoints().filter(LocalContentLanguageDetector::isKana).count();
        long han = text.codePoints().filter(LocalContentLanguageDetector::isHan).count();
        boolean hasNonHangulLetter = text.codePoints()
                .anyMatch(codePoint -> Character.isLetter(codePoint) && !isHangul(codePoint));
        if (hangul > 0 && !hasNonHangulLetter) {
            return new DetectedLanguage("ko", 1.0d);
        }
        if (letters < MIN_LETTERS) {
            return DetectedLanguage.undetermined();
        }
        if (kana >= MIN_LETTERS) {
            return new DetectedLanguage("ja", 1.0d);
        }
        if (hangul >= MIN_LETTERS && hangul >= han) {
            return new DetectedLanguage("ko", 1.0d);
        }
        if (han >= MIN_LETTERS) {
            String variant = chineseVariant(text);
            if (variant != null) {
                return new DetectedLanguage(variant, 0.99d);
            }
        }

        SortedMap<Language, Double> confidences = detector.computeLanguageConfidenceValues(text);
        Language detected = detector.detectLanguageOf(text);
        double confidence = confidences.getOrDefault(detected, 0.0d);
        if (detected == Language.ENGLISH && confidence >= MIN_CONFIDENCE) {
            return new DetectedLanguage("en", confidence);
        }
        if (detected == Language.KOREAN && confidence >= MIN_CONFIDENCE) {
            return new DetectedLanguage("ko", confidence);
        }
        if (detected == Language.JAPANESE && confidence >= MIN_CONFIDENCE && kana > 0) {
            return new DetectedLanguage("ja", confidence);
        }
        return DetectedLanguage.undetermined();
    }

    private static String chineseVariant(String text) {
        long simplified = text.codePoints().filter(codePoint -> SIMPLIFIED_ONLY.indexOf(codePoint) >= 0).count();
        long traditional = text.codePoints().filter(codePoint -> TRADITIONAL_ONLY.indexOf(codePoint) >= 0).count();
        if (simplified > traditional) return "zh-CN";
        if (traditional > simplified) return "zh-TW";
        return null;
    }

    private static boolean isHangul(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HANGUL;
    }

    private static boolean isKana(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
