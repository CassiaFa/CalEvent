package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme

/**
 * Standard screen layout (edge-to-edge aware): status bar inset, optional header, content, optional bottom bar.
 *
 * - Header shown when [title], [onBack] or [headerActions] is given: back button (if [onBack]), title, actions.
 * - [content] fills the remaining space; make it scroll yourself (`verticalScroll` / `LazyColumn`).
 *   Without [bottomBar] the content also gets the navigation bar inset.
 * - [bottomBar] is normally a [BottomActionBar]; it stays above the keyboard.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CalEventTheme.colors
    Column(modifier = modifier.fillMaxSize().background(c.bg).imePadding()) {
        Column(Modifier.statusBarsPadding())  {
            if (title != null || onBack != null || headerActions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (onBack != null) {
                        IconActionButton(CalEventIcons.Back, stringResource(R.string.action_back), onBack)
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = CalEventTheme.type.section, color = c.ink, maxLines = 1, modifier = Modifier.semantics { heading() })
                        }
                    }
                    headerActions?.invoke(this)
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().then(if (bottomBar == null) Modifier.navigationBarsPadding() else Modifier),
            content = content,
        )
        bottomBar?.invoke()
    }
}
