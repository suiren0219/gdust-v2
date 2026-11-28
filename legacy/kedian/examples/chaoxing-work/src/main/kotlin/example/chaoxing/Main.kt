package example.chaoxing

import com.menglimi.chaoxing.ChxApi
import com.menglimi.chaoxing.ChxCourseWorkResult
import com.menglimi.chaoxing.ChxOkHttpTransport
import com.menglimi.chaoxing.ChxResponse
import com.menglimi.chaoxing.ChxWorkReader
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.Request
import java.time.Instant

/** 新 App 登录完成后，把其自己的 CookieJar 传进来；无需学校门户或原 App 仓储。 */
fun homeworkReader(cookieJar: CookieJar, checkSession: () -> Unit = {}): ChxWorkReader {
    val transport = ChxOkHttpTransport(cookieJar, checkSession = checkSession)
    return ChxWorkReader(ChxApi(execute = transport::execute), checkSession = checkSession)
}

/** 默认只跑合成响应，展示课程发现 → 作业列表 → 截止时间的完整公开 API。 */
fun main() = runBlocking {
    val api = ChxApi(execute = ::syntheticResponse)
    val now = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
    val results = ChxWorkReader(api, nowMillis = { now }).read()
    check(results.size == 1)
    val course = results.single() as ChxCourseWorkResult.Success
    val work = course.items.single()
    check(work.item.title == "合成示例作业" && work.dueAt == now + 3_600_000L)
    println("离线合成演示：${course.course.name}，${work.item.title}，${work.item.status}")
    println("截止时间：${Instant.ofEpochMilli(work.dueAt)}；只依赖 chaoxing-core。")
}

private fun syntheticResponse(request: Request): ChxResponse = when (request.url.encodedPath) {
    "/mooc2-ans/visit/interaction" -> ChxResponse(200, """
        <input type='hidden' id='stuPageHeader' value='1'>
        <input type='hidden' id='loadStuJobInfo' value='1'>
        <input type='hidden' id='checkUserToken' value='0123456789abcdef0123456789abcdef'>
    """.trimIndent())
    "/mooc2-ans/visit/courselistdata" -> ChxResponse(200, """
        <div id='courseList'><li class='course learnCourse' info='101_301'>
        <input type='hidden' class='clazzId' value='101'><input type='hidden' class='courseId' value='201'>
        <input type='hidden' class='curPersonId' value='301'><input type='hidden' class='role' value='0'>
        <a href='https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&amp;clazzid=101&amp;cpi=301&amp;ismooc2=1&amp;v=2'>
        <div class='course-info'><span class='course-name' title='合成示例课程'>合成示例课程</span></div></a>
        </li></div>
    """.trimIndent())
    "/visit/stucoursemiddle" -> ChxResponse(200, """
        <input type='hidden' id='courseId' value='201'><input type='hidden' id='classId' value='101'>
        <input type='hidden' id='cpi' value='301'><input type='hidden' id='enc' value='synthetic-stu'>
        <input type='hidden' id='workEnc' value='synthetic-work'><input type='hidden' id='t' value='1700000000000'>
        <input type='hidden' id='moocDomainName' value='https://mooc1.chaoxing.com'>
    """.trimIndent())
    "/mooc2/work/list" -> ChxResponse(200, """
        <div class='bottomList'><ul><li data='/mooc2/work/task?courseId=201&amp;classId=101&amp;cpi=301&amp;workId=401&amp;answerId=501&amp;enc=synthetic'>
        <p class='overHidden2'>合成示例作业</p><p class='status'>未交</p><div class='time'>剩余1小时</div>
        </li></ul></div>
    """.trimIndent())
    "/mooc2/work/task" -> ChxResponse(200, "<p>无绝对截止时间，演示回退列表中的剩余时间。</p>")
    else -> error("合成示例未定义此请求，不会发送网络请求")
}
