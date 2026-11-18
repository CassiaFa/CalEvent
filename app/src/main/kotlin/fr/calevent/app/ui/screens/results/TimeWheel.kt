package fr.calevent.app.ui.screens.results

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.ui.theme.CalEventTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import java.time.LocalTime

private val ROW_HEIGHT = 44.dp

/**
 * One snapping wheel over [values]. [selected] only sets the initial position: [onSelect] fires only
 * after the user scrolled (or used an accessibility action), so an untouched value is never rewritten.
 */
@Composable
private fun Wheel(
    values: List<Int>,
    selected: Int,
    label: String,
    onSelect: (Int) -> Unit,
    slotKey: Any,
    modifier: Modifier = Modifier,
) {
    val c = CalEventTheme.colors
    val initialIndex = remember(slotKey) {
        values.indices.minByOrNull { kotlin.math.abs(values[it] - selected) } ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    val centered by remember {
        derivedStateOf {
            val extra = if (listState.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
            (listState.firstVisibleItemIndex + extra).coerceIn(0, values.lastIndex)
        }
    }
    var pendingScroll by remember(slotKey) { mutableStateOf<Int?>(null) }
    LaunchedEffect(slotKey) {
        listState.scrollToItem(initialIndex)
        snapshotFlow { listState.isScrollInProgress }.drop(1).filter { !it }.collect { onSelect(values[centered]) }
    }
    LaunchedEffect(pendingScroll) {
        pendingScroll?.let { listState.animateScrollToItem(it) }
    }
    fun step(delta: Int): Boolean {
        val target = (centered + delta).coerceIn(0, values.lastIndex)
        if (target == centered) return false
        pendingScroll = target
        return true
    }
    Box(
        modifier
            .height(ROW_HEIGHT * 3)
            .semantics {
                contentDescription = label
                stateDescription = "%02d".format(values[centered])
                customActions = listOf(
                    CustomAccessibilityAction("+") { step(1) },
                    CustomAccessibilityAction("−") { step(-1) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(ROW_HEIGHT).background(c.surface2, RoundedCornerShape(12.dp)))
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(vertical = ROW_HEIGHT),
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        ) {
            itemsIndexed(values) { index, v ->
                Box(Modifier.height(ROW_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val isCentered = index == centered
                    Text(
                        "%02d".format(v),
                        style = (if (isCentered) CalEventTheme.type.section else CalEventTheme.type.body)
                            .copy(fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
                        color = if (isCentered) c.ink else c.ink2,
                    )
                }
            }
        }
    }
}

/**
 * Hour (00-23) and minute (5-min steps) wheels. [onChange] is called after the user changes a wheel.
 * A minute that is not a multiple of 5 stays exact until the user touches the minute wheel.
 */
@Composable
internal fun TimeWheel(
    label: String,
    time: LocalTime,
    onChange: (LocalTime) -> Unit,
    slotKey: Any,
    modifier: Modifier = Modifier,
) {
    val c = CalEventTheme.colors
    val hours = remember { (0..23).toList() }
    val minutes = remember { (0..55 step 5).toList() }
    var current by remember(slotKey) { mutableStateOf(time) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = CalEventTheme.type.label, color = c.ink2)
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wheel(
                hours, time.hour, stringResource(R.string.slot_wheel_hours, label),
                onSelect = { h -> current = current.withHour(h); onChange(current) },
                slotKey = slotKey, modifier = Modifier.width(64.dp),
            )
            Text(":", style = CalEventTheme.type.section, color = c.ink)
            Wheel(
                minutes, time.minute, stringResource(R.string.slot_wheel_minutes, label),
                onSelect = { m -> current = current.withMinute(m); onChange(current) },
                slotKey = slotKey, modifier = Modifier.width(64.dp),
            )
        }
    }
}
