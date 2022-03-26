package per.goweii.wanandroid.module.book.contract

import kotlinx.coroutines.*
import okhttp3.Cookie
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import per.goweii.basic.core.base.BasePresenter
import per.goweii.basic.core.base.BaseView
import per.goweii.wanandroid.module.book.bean.BookIntroBean
import per.goweii.wanandroid.utils.CookieUtils.loadForUrl

interface BookIntroView : BaseView {
    fun getBookIntroSuccess(bean: BookIntroBean)
    fun getBookIntroListFailed()
}

class BookIntroPresenter : BasePresenter<BookIntroView>() {
    private lateinit var mainScope: CoroutineScope

    override fun attach(baseView: BookIntroView) {
        super.attach(baseView)
        mainScope = MainScope()
    }

    override fun detach() {
        super.detach()
        mainScope.cancel()
    }

    fun getIntro(link: String) = mainScope.launch {
        try {
            val bean = getIntroByJSoup(link)
            if (isAttach) {
                baseView.getBookIntroSuccess(bean)
            }
        } catch (e: Throwable) {
            if (isAttach) {
                baseView.getBookIntroListFailed()
            }
        }
    }

    private suspend fun getIntroByJSoup(url: String): BookIntroBean = withContext(Dispatchers.IO) {
        val cookies: List<Cookie> = loadForUrl(url)
        val map: MutableMap<String, String> = HashMap(cookies.size)
        for (cookie in cookies) {
            map[cookie.name()] = cookie.value()
        }
        val document = Jsoup.connect(url).cookies(map).get()
        var img: String?
        var name: String?
        var author: String?
        var desc: String?
        var copyright: BookIntroBean.Copyright?
        val list = arrayListOf<BookIntroBean.BookChapterBean>()
        document
            .apply {
                img = getElementsByClass("book_img").first()
                    .getElementsByTag("img").first()
                    .attributes()
                    .get("src")
            }
            .apply {
                getElementsByClass("book_info").first()
                    .apply {
                        name = getElementsByTag("h2").first().ownText()
                        getElementsByTag("p").apply {
                            author = this[0].ownText()
                            desc = this[1].ownText().removeSuffix("简介：")
                            this[2].getElementsByTag("a").first()
                                .apply {
                                    copyright = BookIntroBean.Copyright(
                                        name = ownText(),
                                        url = attributes().get("href")
                                    )
                                }
                        }
                    }
            }
            .getElementsByClass("main_content_l").first()
            .getElementsByClass("block book_catalog").first()
            .getElementsByClass("catalog").first()
            .getElementsByTag("ul").first()
            .getElementsByTag("li")
            .forEach {
                try {
                    it.getElementsByTag("a").first()
                        .run {
                            BookIntroBean.BookChapterBean(
                                index = getElementsByTag("i").first().ownText().toInt(),
                                name = ownText().trim(),
                                link = attributes().get("href"),
                            )
                        }
                        .apply { list.add(this) }
                } catch (unused: Throwable) {
                }
            }
        return@withContext BookIntroBean(
            img = img!!,
            author = author!!,
            name = name!!,
            desc = desc!!,
            copyright = copyright!!,
            chapters = list
        )
    }

    private fun parseBookChapterBean(element: Element): BookIntroBean.BookChapterBean {
        var index: Int?
        var name: String?
        var link: String?
        element.getElementsByTag("a").first()
            .apply {
                link = attributes().get("href")
                index = getElementsByTag("i").first().ownText().toInt()
                name = ownText().trim()
            }
        return BookIntroBean.BookChapterBean(
            index = index!!,
            name = name!!,
            link = link!!,
        )
    }
}