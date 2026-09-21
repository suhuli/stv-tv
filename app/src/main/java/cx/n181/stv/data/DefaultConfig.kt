package cx.n181.stv.data

object DefaultConfig {
    fun build(): AppConfig {
        val sources = listOf(
            source("dyttzy", "电影天堂资源", "https://caiji.dyttzyapi.com/api.php/provide/vod", 1),
            source("ruyi", "如意资源", "https://cj.rycjapi.com/api.php/provide/vod", 2),
            source("bfzy", "暴风资源", "https://bfzyapi.com/api.php/provide/vod", 3),
            source("ffzy", "非凡影视", "https://ffzy5.tv/api.php/provide/vod", 4),
            source("hong", "红牛资源", "https://www.hongniuzy2.com/api.php/provide/vod", 5),
            source("zy360", "360资源", "https://360zy.com/api.php/provide/vod", 6),
            source("iqiyi", "iqiyi资源", "https://www.iqiyizyapi.com/api.php/provide/vod", 7),
            source("jisu", "极速资源", "https://jszyapi.com/api.php/provide/vod", 8),
            source("mdzy", "魔都资源", "https://www.mdzyapi.com/api.php/provide/vod", 9),
            source("zuid", "最大资源", "https://api.zuidapi.com/api.php/provide/vod", 10),
            source("wujin", "无尽资源", "https://api.wujinapi.me/api.php/provide/vod", 11),
            source("ikun", "iKun资源", "https://ikunzyapi.com/api.php/provide/vod", 12),
            source("lzi", "量子资源站", "https://cj.lziapi.com/api.php/provide/vod", 13),
            source("huya", "虎牙资源", "https://www.huyaapi.com/api.php/provide/vod", 14),
            source("subo", "速播资源", "https://subocj.com/api.php/provide/vod", 15),
            source("ukzy", "U酷资源", "https://api.ukuapi88.com/api.php/provide/vod", 16),
            source("jyzy", "金鹰资源", "https://jyzyapi.com/provide/vod", 17),
            source("gszy", "光速资源", "https://api.guangsuapi.com/api.php/provide/vod", 18),
            source("xinlang", "新浪资源", "https://api.xinlangapi.com/xinlangapi.php/provide/vod", 19),
            source("haohua", "豪华资源", "https://hhzyapi.com/api.php/provide/vod", 20),
            source("xigua", "西瓜资源", "https://caiji.xgzyapi.com/api.php/provide/vod", 21),
            source("maoyan", "猫眼资源", "https://api.maoyanapi.top/api.php/provide/vod", 22)
        )

        return AppConfig(sources = sources)
    }

    private fun source(
        key: String,
        name: String,
        api: String,
        order: Int
    ) = SourceConfig(
        key = key,
        name = name,
        api = api,
        enabled = true,
        order = order
    )
}
