package com.haooz.chedule.data.gdust

/** 广科门户/水电的轻量数据模型（androidMain 内部使用，导入时映射到 core 的 Course）。 */

data class GdustCalendar(
    val year: String,
    val semester: String,
    val week: Int,
    val allWeek: Int,
    val startTime: String,   // yyyy-MM-dd（周一）
    val endTime: String,
)

data class GdustLesson(
    val week: Int,
    val dayWeek: Int,        // 1=周一
    val whichSection: Int,   // 1-10
    val classroomName: String?,
    val courseName: String,
    val teacher: String?,
    val courseDate: String,  // yyyy-MM-dd
)

data class GdustAnnouncement(
    val title: String,
    val url: String,
    val announcementType: String,
    val issuer: String?,
    val gmtCreate: Long,
)

data class GdustUserInfo(
    val deptName: String?,
    val jobNum: String,
    val realName: String,
)

/** 水电房间（SWAEServlet getbindroom 返回 roomlist 条目）。 */
data class GdustPowerRoom(
    val roomfullname: String?,
    val roomverify: String?,
)

/** 水电查询结果（modlist.0.odd 提取）。 */
data class GdustPowerReading(
    val odd: Double?,
    val raw: String,
)

/** 门户登录态（TOKEN + 学号），EncryptedSharedPreferences 持久化。 */
data class GdustSession(
    val portalToken: String,
    val jobNumber: String,
    val realName: String,
)
