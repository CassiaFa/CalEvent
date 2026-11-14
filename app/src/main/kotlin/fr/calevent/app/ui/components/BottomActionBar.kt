package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import fr.calevent.app.ui.theme.CalEventTheme

/**
 * Action area pinned at the bottom: bg background, 1 dp line on top, respects the navigation bar inset.
 * Put the primary button first (fillMaxWidth), then the secondary one.
 */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = CalEventTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.bg)
            .drawBehind { drawRect(c.line, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
