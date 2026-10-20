package com.enotes.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

internal val dashboardActionIds = listOf("new_note", "new_folder", "scan_document", "voice_note", "ai_assistant")
internal val dashboardCategories = listOf("All Notes", "Personal", "Study", "Work", "Others")

internal data class DashboardPreferences(
    val actionOrder: List<String> = dashboardActionIds,
    val hiddenActions: Set<String> = emptySet(),
    val pinnedActions: Set<String> = emptySet(),
    val quickAccessOrder: List<String> = dashboardCategories.map { "category:$it" },
    val hiddenQuickAccess: Set<String> = emptySet(),
    val pinnedQuickAccess: Set<String> = emptySet()
) {
    fun encode(): String = JSONObject().apply {
        put("actionOrder", JSONArray(actionOrder))
        put("hiddenActions", JSONArray(hiddenActions.toList()))
        put("pinnedActions", JSONArray(pinnedActions.toList()))
        put("quickAccessOrder", JSONArray(quickAccessOrder))
        put("hiddenQuickAccess", JSONArray(hiddenQuickAccess.toList()))
        put("pinnedQuickAccess", JSONArray(pinnedQuickAccess.toList()))
    }.toString()

    companion object {
        fun decode(encoded: String?): DashboardPreferences {
            if (encoded.isNullOrBlank()) return DashboardPreferences()
            return runCatching {
                val json = JSONObject(encoded)
                fun strings(key: String, fallback: List<String>): List<String> =
                    json.optJSONArray(key)?.let { array ->
                        buildList { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
                    }?.distinct()?.takeIf { it.isNotEmpty() } ?: fallback
                fun set(key: String): Set<String> = strings(key, emptyList()).toSet()
                val actionOrder = (strings("actionOrder", dashboardActionIds) + dashboardActionIds).distinct()
                val quickAccessOrder = strings("quickAccessOrder", DashboardPreferences().quickAccessOrder)
                DashboardPreferences(
                    actionOrder = actionOrder,
                    hiddenActions = set("hiddenActions"),
                    pinnedActions = set("pinnedActions"),
                    quickAccessOrder = quickAccessOrder,
                    hiddenQuickAccess = set("hiddenQuickAccess"),
                    pinnedQuickAccess = set("pinnedQuickAccess")
                )
            }.getOrElse { DashboardPreferences() }
        }
    }
}
internal fun moveDashboardItem(order: List<String>, id: String, direction: Int): List<String> {
    if (order.isEmpty()) return order
    val from = order.indexOf(id)
    val to = (from + direction).coerceIn(0, order.lastIndex)
    if (from < 0 || from == to) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

internal data class HomeAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

internal data class DashboardOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val selected: Boolean = false
)

@Composable
internal fun QuickActionsSection(
    actions: List<HomeAction>,
    preferences: DashboardPreferences,
    onEdit: () -> Unit
) {
    val ordered = remember(actions, preferences.actionOrder, preferences.hiddenActions, preferences.pinnedActions) {
        val byId = actions.associateBy(HomeAction::id)
        (preferences.actionOrder + actions.map(HomeAction::id)).distinct()
            .filterNot(preferences.hiddenActions::contains)
            .sortedWith(compareByDescending<String> { it in preferences.pinnedActions }.thenBy { preferences.actionOrder.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } })
            .mapNotNull(byId::get)
    }
    val state = rememberLazyListState()
    CarouselHeader(
        title = "Quick Actions", subtitle = "Your favorite tools", icon = Icons.Default.Bolt,
        state = state, onEdit = onEdit,
        onPrevious = { count -> state.animateScrollToItem((state.firstVisibleItemIndex - count).coerceAtLeast(0)) },
        onNext = { count -> state.animateScrollToItem((state.firstVisibleItemIndex + count).coerceAtMost((ordered.size - 1).coerceAtLeast(0))) }
    )
    if (ordered.isEmpty()) {
        EmptyCarouselMessage("No actions shown. Tap Edit to add one.")
    } else {
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ordered, key = { it.id }) { action ->
                Card(
                    Modifier.width(104.dp).heightIn(min = 122.dp).clickable(onClick = action.onClick),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = action.tint.copy(alpha = 0.09f))
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(action.icon, action.title, tint = action.tint, modifier = Modifier.size(27.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(action.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(action.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        CarouselDots(ordered.size, state, 104.dp)
    }
}

@Composable
internal fun QuickAccessSection(
    options: List<DashboardOption>,
    preferences: DashboardPreferences,
    onEdit: () -> Unit,
    onSelect: (String) -> Unit
) {
    val byId = remember(options) { options.associateBy(DashboardOption::id) }
    val ordered = remember(options, preferences.quickAccessOrder, preferences.hiddenQuickAccess, preferences.pinnedQuickAccess) {
        val fullOrder = preferences.quickAccessOrder.distinct()
        fullOrder.filter { it in byId && it !in preferences.hiddenQuickAccess }
            .sortedWith(compareByDescending<String> { it in preferences.pinnedQuickAccess }.thenBy { fullOrder.indexOf(it) })
            .mapNotNull(byId::get)
    }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Card(
        Modifier.fillMaxWidth().clickable { onSelect("category:All Notes") }, shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, Color(0xFFE8E5F4)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Quick Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Jump to your favorite categories", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 3).coerceAtLeast(0)) } }, enabled = state.canScrollBackward, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ChevronLeft, "Previous Quick Access options")
            }
            TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit")
            }
            IconButton(onClick = {
                if (state.canScrollForward) scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex + 3).coerceAtMost((ordered.size - 1).coerceAtLeast(0))) }
                else onSelect("category:All Notes")
            }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ChevronRight, if (state.canScrollForward) "More Quick Access options" else "Show all notes", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (ordered.isEmpty()) {
        EmptyCarouselMessage("Quick Access is empty. Tap Edit to add shortcuts.")
    } else {
        Column(Modifier.fillMaxWidth()) {
            val itemWidth = 104.dp
            LazyRow(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ordered, key = DashboardOption::id) { option ->
                    Card(
                        Modifier.width(itemWidth).heightIn(min = 100.dp).clickable { onSelect(option.id) },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (option.selected) Color(0xFFD7C9FF) else Color(0xFFE9E7F1)),
                        colors = CardDefaults.cardColors(containerColor = if (option.selected) Color(0xFFF0EAFE) else Color.White)
                    ) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(option.icon, null, tint = option.tint, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(option.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(option.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            CarouselDots(ordered.size, state, itemWidth)
        }
    }
}

@Composable
private fun CarouselHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    state: LazyListState,
    onEdit: () -> Unit,
    onPrevious: suspend (Int) -> Unit,
    onNext: suspend (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val visibleCount = 3
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { scope.launch { onPrevious(visibleCount) } }, enabled = state.canScrollBackward, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.ChevronLeft, "Previous options")
        }
        IconButton(onClick = { scope.launch { onNext(visibleCount) } }, enabled = state.canScrollForward, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.ChevronRight, "More options")
        }
        TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 7.dp)) {
            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(3.dp))
            Text("Edit")
        }
    }
}

@Composable
private fun CarouselDots(itemCount: Int, state: LazyListState, itemWidth: Dp) {
    if (itemCount <= 1) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemsPerPage = ((maxWidth + 8.dp) / (itemWidth + 8.dp)).toInt().coerceAtLeast(1)
        val pages = ceil(itemCount / itemsPerPage.toFloat()).toInt().coerceAtLeast(1)
        val currentPage by remember(state, itemsPerPage, pages) {
            derivedStateOf { (state.firstVisibleItemIndex / itemsPerPage).coerceIn(0, pages - 1) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(pages) { page ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (page == currentPage) 7.dp else 6.dp)
                        .background(if (page == currentPage) MaterialTheme.colorScheme.primary else Color(0xFFD6D3E0), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun EmptyCarouselMessage(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = 18.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
internal fun CustomizeDashboardDialog(
    title: String,
    description: String,
    options: List<DashboardOption>,
    order: List<String>,
    hidden: Set<String>,
    pinned: Set<String>,
    quickAccessIds: Set<String>? = null,
    onVisibilityChange: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onPinChange: (String, Boolean) -> Unit,
    onQuickAccessChange: ((String, Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val byId = remember(options) { options.associateBy(DashboardOption::id) }
    val rows = remember(options, order) { (order + options.map(DashboardOption::id)).distinct().mapNotNull(byId::get) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 500.dp)) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows, key = DashboardOption::id) { option ->
                        val isInOrder = option.id in order
                        val isVisible = isInOrder && option.id !in hidden
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFFFBFAFF), RoundedCornerShape(14.dp)).padding(start = 2.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DragHandle, "Hold and drag to reorder ${option.title}",
                                modifier = Modifier.size(38.dp).dashboardDragHandle(option.id, onMove), tint = MaterialTheme.colorScheme.outline
                            )
                            Icon(option.icon, null, tint = option.tint, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(option.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(option.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onPinChange(option.id, option.id !in pinned) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.PushPin, if (option.id in pinned) "Unpin from top" else "Pin to top", tint = if (option.id in pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(19.dp))
                            }
                            if (onQuickAccessChange != null) {
                                val quickAccessId = "action:${option.id}"
                                IconButton(onClick = { onQuickAccessChange(option.id, quickAccessId !in quickAccessIds.orEmpty()) }, modifier = Modifier.size(36.dp)) {
                                    Icon(if (quickAccessId in quickAccessIds.orEmpty()) Icons.Default.Star else Icons.Default.StarBorder, if (quickAccessId in quickAccessIds.orEmpty()) "Remove from Quick Access" else "Add to Quick Access", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                                }
                            }
                            IconButton(onClick = { if (isVisible) onVisibilityChange(option.id, false) }, enabled = isVisible, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.RemoveCircleOutline, "Remove from list", tint = if (isVisible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, modifier = Modifier.size(19.dp))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Checkbox(isVisible, { onVisibilityChange(option.id, it) }, modifier = Modifier.size(42.dp))
                                Text(if (isVisible) "Show" else if (isInOrder) "Hidden" else "Add", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun Modifier.dashboardDragHandle(id: String, onMove: (String, Int) -> Unit): Modifier {
    val latestOnMove by rememberUpdatedState(onMove)
    return pointerInput(id) {
        var accumulated = 0f
        val threshold = 42.dp.toPx()
        detectDragGesturesAfterLongPress(
            onDragStart = { accumulated = 0f },
            onDragEnd = { accumulated = 0f },
            onDragCancel = { accumulated = 0f },
            onDrag = { change, dragAmount ->
                change.consume()
                accumulated += dragAmount.y
                while (accumulated >= threshold) {
                    latestOnMove(id, 1)
                    accumulated -= threshold
                }
                while (accumulated <= -threshold) {
                    latestOnMove(id, -1)
                    accumulated += threshold
                }
            }
        )
    }
}

