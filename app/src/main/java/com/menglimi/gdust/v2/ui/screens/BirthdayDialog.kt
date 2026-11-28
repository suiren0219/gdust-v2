package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.text.NexioText as Text
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import com.menglimi.gdust.v2.R
import com.menglimi.gdust.v2.ui.LocalDs

/** 贺图不含固定姓名或年龄；只在已取得生日的当天打开。 */
@Composable
internal fun BirthdayDialog(onDismiss: () -> Unit) {
    val ds = LocalDs.current
    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("今天，给你留一份祝福", fontSize = 13.sp, color = ds.sub)
            Text("生日快乐", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = ds.text)
            Text(
                "愿新的一岁，有热爱的事，也有好好休息的时间。",
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = ds.sub,
            )
            Image(
                painter = painterResource(R.drawable.birthday_gift),
                contentDescription = "两位角色为你送上生日祝福",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1080f / 714f)
                    .clip(ContinuousRoundedRectangle(18.dp)),
            )
            Text(
                "今天的课程可以照常安排，快乐也记得留给自己。",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = ds.sub,
            )
            NexioActionButton(
                text = "收下祝福",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                onClick = onDismiss,
            )
        }
    }
}
