package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.menglimi.gdust.v2.R
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.LocalDs

/** 贺图不含固定姓名或年龄；只在已取得生日的当天打开。 */
@Composable
internal fun BirthdayDialog(onDismiss: () -> Unit) {
    val ds = LocalDs.current
    Dialog(onDismissRequest = onDismiss) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("今天，给你留一份祝福", fontSize = 13.sp, color = ds.sub)
                    Text("生日快乐", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ds.text)
                    Text("愿新的一岁，有热爱的事，也有好好休息的时间。", fontSize = 15.sp,
                        lineHeight = 23.sp, color = ds.sub)
                }
                Image(painterResource(R.drawable.birthday_gift), "两位角色为你送上生日祝福",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .aspectRatio(1080f / 714f).clip(RoundedCornerShape(18.dp)))
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("今天的课程可以照常安排，快乐也记得留给自己。", fontSize = 14.sp,
                        lineHeight = 22.sp, color = ds.sub)
                    NexioActionButton("收下祝福", onClick = onDismiss)
                }
            }
        }
    }
}
