package app.fen

import android.app.Application
import app.fen.autobook.capture.ImageImportController
import app.fen.autobook.classify.AutoMapStore
import app.fen.autobook.classify.Classifier
import app.fen.autobook.classify.RuleClassifier
import app.fen.autobook.classify.SqliteAutoMapStore
import app.fen.autobook.ocr.MlKitOcrEngine
import app.fen.autobook.parse.GenericBillParser
import app.fen.autobook.pipeline.RecognitionEngine
import app.fen.autobook.source.SourceDetection
import app.fen.autobook.source.recognizers.AlipayRecognizer
import app.fen.autobook.source.recognizers.DouyinRecognizer
import app.fen.autobook.source.recognizers.GenericRecognizer
import app.fen.autobook.source.recognizers.JdFinanceRecognizer
import app.fen.autobook.source.recognizers.JdRecognizer
import app.fen.autobook.source.recognizers.MeituanRecognizer
import app.fen.autobook.source.recognizers.MeituanTakeoutRecognizer
import app.fen.autobook.source.recognizers.PddRecognizer
import app.fen.autobook.source.recognizers.TaobaoRecognizer
import app.fen.autobook.source.recognizers.UnionpayRecognizer
import app.fen.autobook.source.recognizers.WechatRecognizer
import app.fen.data.AppPrefs
import app.fen.data.FenDb
import app.fen.data.FenRepository

class FenApp : Application() {

    lateinit var repository: FenRepository
        private set

    lateinit var prefs: AppPrefs
        private set

    lateinit var autoMap: AutoMapStore
        private set

    lateinit var imageImport: ImageImportController
        private set

    override fun onCreate() {
        super.onCreate()
        val db = FenDb(this)
        repository = FenRepository(db)
        prefs = AppPrefs(this)
        autoMap = SqliteAutoMapStore(db)
        imageImport = ImageImportController(
            context = this,
            ocr = MlKitOcrEngine(),
            engine = RecognitionEngine(
                SourceDetection(
                    listOf(
                        WechatRecognizer,
                        AlipayRecognizer,
                        UnionpayRecognizer,
                        JdRecognizer,
                        JdFinanceRecognizer,
                        MeituanRecognizer,
                        MeituanTakeoutRecognizer,
                        PddRecognizer,
                        TaobaoRecognizer,
                        DouyinRecognizer,
                        GenericRecognizer,
                    )
                ),
                GenericBillParser(),
            ),
            classifier = Classifier(
                autoMap,
                RuleClassifier(
                    mapOf(
                        "餐厅" to "餐饮",
                        "外卖" to "餐饮",
                        "咖啡" to "餐饮",
                        "美食" to "餐饮",
                        "地铁" to "交通",
                        "打车" to "交通",
                        "加油" to "交通",
                        "公交" to "交通",
                        "电影" to "娱乐",
                        "游戏" to "娱乐",
                        "药" to "医疗",
                        "门诊" to "医疗",
                        "书" to "学习",
                        "课程" to "学习",
                    )
                ),
            ),
            categories = { repository.snapshot.value.categories },
        )
    }
}
