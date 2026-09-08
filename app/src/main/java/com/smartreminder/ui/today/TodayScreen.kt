package com.smartreminder.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.task.TaskPriority
import com.smartreminder.domain.model.today.AiSuggestion
import com.smartreminder.domain.model.today.DailyProgress
import com.smartreminder.domain.model.today.ScheduleConflict
import com.smartreminder.domain.model.today.TodayTimelineItem
import com.smartreminder.domain.model.today.TodayTimelineItem.ConflictBlock
import com.smartreminder.domain.model.today.TodayTimelineItem.FreeSlotBlock
import com.smartreminder.domain.model.today.TodayTimelineItem.RoutineEvent
import com.smartreminder.domain.model.today.TodayTimelineItem.SuggestedFocusBlock
import com.smartreminder.domain.model.today.TodayTimelineItem.TaskEvent
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueAccentContainer
import com.smartreminder.ui.theme.CueBackground
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueCta
import com.smartreminder.ui.theme.CueOnCta
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurface
import com.smartreminder.ui.theme.CueSurfaceSubtle
import com.smartreminder.ui.theme.CueTextPrimary
import com.smartreminder.ui.theme.CueTextSecondary
import com.smartreminder.ui.theme.SmartReminderTheme
import com.smartreminder.ui.today.components.AddTaskBottomSheet
import com.smartreminder.ui.today.components.AiSuggestionCard
import com.smartreminder.ui.today.components.ConflictSection
import com.smartreminder.ui.today.components.DailyProgressSection
import com.smartreminder.ui.today.components.FreeSlotCard
import com.smartreminder.ui.today.components.NowTimeIndicator
import com.smartreminder.ui.today.components.TimelineEventCard
import com.smartreminder.ui.today.components.TodayTopBar
import com.smartreminder.ui.today.components.TodayViewModeSelector
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onAction: (TodayUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CueBackground,
        topBar = {
            when (uiState) {
                is TodayUiState.Success -> {
                    TodayTopBar(
                        userName = uiState.userName,
                        avatarUrl = uiState.userAvatarUrl,
                        onAvatarClick = { onAction(TodayUiAction.ProfileAvatarClick) },
                        onNotificationClick = { onAction(TodayUiAction.NotificationClick) }
                    )
                }
                else -> {}
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAction(TodayUiAction.AddEventClick()) },
                containerColor = CueCta,
                contentColor = CueOnCta,
                shape = RoundedCornerShape(CueSpacing.Lg),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.today_quick_create)
                )
            }
        }
    ) { innerPadding ->
        when (uiState) {
            TodayUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CueAccent)
                }
            }
            is TodayUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(CueSpacing.Xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.message ?: stringResource(R.string.placeholder_coming_soon),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CueTextSecondary
                    )
                }
            }
            is TodayUiState.Success -> {
                TodayContent(
                    state = uiState,
                    onAction = onAction,
                    modifier = Modifier.padding(innerPadding)
                )

                if (uiState.showAddTaskSheet) {
                    AddTaskBottomSheet(
                        initialTime = uiState.addTaskDefaultTime,
                        onDismiss = { onAction(TodayUiAction.DismissAddTask) },
                        onSave = { title, time, duration, prio ->
                            onAction(TodayUiAction.CreateCustomTask(title, time, duration, prio))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState.Success,
    onAction: (TodayUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 88.dp) // Space for FAB
    ) {
        // View Mode Switcher (Overview, Timeline, Minimal, Conflict)
        TodayViewModeSelector(
            selectedMode = state.selectedViewMode,
            onSelectMode = { onAction(TodayUiAction.ChangeViewMode(it)) }
        )

        Spacer(modifier = Modifier.height(CueSpacing.Md))

        AnimatedContent(
            targetState = state.selectedViewMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "TodayViewModeCrossfade"
        ) { mode ->
            when (mode) {
                TodayViewMode.OVERVIEW -> {
                    TodayOverviewView(state = state, onAction = onAction)
                }
                TodayViewMode.TIMELINE -> {
                    TodayTimelineView(state = state, onAction = onAction)
                }
                TodayViewMode.MINIMAL_STREAM -> {
                    TodayMinimalStreamView(state = state, onAction = onAction)
                }
                TodayViewMode.BUSY_CONFLICT -> {
                    TodayConflictView(state = state, onAction = onAction)
                }
            }
        }
    }
}

// ============================================================================
// 1. MÀN HÌNH OVERVIEW (Tiến độ + AI Suggestion + Up Next)
// ============================================================================
@Composable
private fun TodayOverviewView(
    state: TodayUiState.Success,
    onAction: (TodayUiAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl)
    ) {
        // Greeting Header
        val hour = state.currentTime.hour
        val greeting = when {
            hour < 12 -> stringResource(R.string.today_greeting_morning, state.userName)
            hour < 18 -> stringResource(R.string.today_greeting_afternoon, state.userName)
            else -> stringResource(R.string.today_greeting_evening, state.userName)
        }
        val dateFormatted = state.currentDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = CueTextPrimary
        )
        Text(
            text = dateFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = CueTextSecondary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        // Daily Progress
        DailyProgressSection(progress = state.progress)

        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        // AI Suggestion Banner
        if (state.aiSuggestion != null) {
            AiSuggestionCard(
                suggestion = state.aiSuggestion,
                onActionClick = {
                    if (state.aiSuggestion.suggestedTaskTitle != null && state.aiSuggestion.focusWindowStart != null) {
                        onAction(TodayUiAction.AcceptFocusBlock(state.aiSuggestion.suggestedTaskTitle, state.aiSuggestion.focusWindowStart))
                    }
                }
            )
            Spacer(modifier = Modifier.height(CueSpacing.Xl))
        }

        // Up Next Section
        Text(
            text = stringResource(R.string.today_section_up_next),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = CueTextPrimary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Md))

        val upcomingEvents = state.timelineItems.filter { item ->
            when (item) {
                is RoutineEvent -> !item.isPast
                is TaskEvent -> !item.isPast && !item.isCompleted
                else -> false
            }
        }

        if (upcomingEvents.isEmpty()) {
            EmptyTodayView()
        } else {
            upcomingEvents.forEach { item ->
                when (item) {
                    is RoutineEvent -> {
                        TimelineEventCard(
                            startTime = item.startTime,
                            title = item.title,
                            categoryTag = item.categoryTag,
                            isPast = item.isPast
                        )
                    }
                    is TaskEvent -> {
                        TimelineEventCard(
                            startTime = item.startTime,
                            title = item.title,
                            categoryTag = item.category,
                            isPast = item.isPast,
                            isCompleted = item.isCompleted,
                            priority = item.priority,
                            isVirtual = item.isVirtual,
                            attendees = item.attendeeCount,
                            onToggleCompletion = { onAction(TodayUiAction.ToggleTaskCompletion(item.id.removePrefix("task_"), it)) }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

// ============================================================================
// 2. MÀN HÌNH TIMELINE (Chi tiết thời gian + Now Line + Free Slots)
// ============================================================================
@Composable
private fun TodayTimelineView(
    state: TodayUiState.Success,
    onAction: (TodayUiAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl)
    ) {
        // Top Adaptive NOW Module
        val nextFreeSlot = state.timelineItems.filterIsInstance<FreeSlotBlock>().firstOrNull { !it.startTime.isBefore(state.currentTime) }
        if (nextFreeSlot != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CueAccentContainer, RoundedCornerShape(CueSpacing.Lg)),
                shape = RoundedCornerShape(CueSpacing.Lg),
                color = CueAccentContainer
            ) {
                Column(modifier = Modifier.padding(CueSpacing.Lg)) {
                    Text(
                        text = stringResource(R.string.today_free_until, nextFreeSlot.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CueTextPrimary
                    )

                    Spacer(modifier = Modifier.height(CueSpacing.Sm))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CueSpacing.Md),
                        color = CueSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CueSpacing.Md, vertical = CueSpacing.Sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Android assignment · fits ${nextFreeSlot.durationMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CueTextPrimary
                            )

                            Button(
                                onClick = { onAction(TodayUiAction.AcceptFocusBlock("Android assignment", nextFreeSlot.startTime)) },
                                shape = RoundedCornerShape(CueSpacing.Xl),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CueCta,
                                    contentColor = CueOnCta
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.today_free_slot_action_add_to_plan),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Lg))
        }

        // Chronological Items
        var nowIndicatorDrawn = false

        state.timelineItems.forEach { item ->
            // Insert "Now" line at the right chronological position
            if (!nowIndicatorDrawn && item.startTime.isAfter(state.currentTime)) {
                NowTimeIndicator(currentTime = state.currentTime)
                nowIndicatorDrawn = true
            }

            when (item) {
                is RoutineEvent -> {
                    TimelineEventCard(
                        startTime = item.startTime,
                        title = item.title,
                        categoryTag = item.categoryTag,
                        isPast = item.isPast
                    )
                }
                is TaskEvent -> {
                    TimelineEventCard(
                        startTime = item.startTime,
                        title = item.title,
                        categoryTag = item.category,
                        isPast = item.isPast,
                        isCompleted = item.isCompleted,
                        priority = item.priority,
                        isVirtual = item.isVirtual,
                        attendees = item.attendeeCount,
                        onToggleCompletion = { onAction(TodayUiAction.ToggleTaskCompletion(item.id.removePrefix("task_"), it)) }
                    )
                }
                is FreeSlotBlock -> {
                    FreeSlotCard(
                        startTime = item.startTime,
                        durationMinutes = item.durationMinutes,
                        onAddClick = { onAction(TodayUiAction.AddEventClick(item.startTime)) }
                    )
                }
                is SuggestedFocusBlock -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CueSpacing.Xs)
                            .border(1.dp, CueAccent, RoundedCornerShape(CueSpacing.Lg)),
                        shape = RoundedCornerShape(CueSpacing.Lg),
                        color = CueAccentContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(CueSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.labelSmall,
                                color = CueAccent,
                                modifier = Modifier.width(48.dp)
                            )
                            Spacer(modifier = Modifier.width(CueSpacing.Sm))
                            Text(
                                text = stringResource(R.string.today_suggested_focus, item.taskTitle),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = CueAccent,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${item.durationMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = CueAccent
                            )
                        }
                    }
                }
                is ConflictBlock -> {
                    ConflictSection(
                        conflict = ScheduleConflict(
                            conflictTime = item.startTime,
                            primaryTitle = item.primaryTitle,
                            conflictingTitle = item.conflictingTitle,
                            locationOrDetails = item.locationOrDetails,
                            suggestedMoveTime = item.suggestedMoveTime,
                            suggestedItemTitle = item.suggestedItemTitle
                        ),
                        onReviewClick = {}
                    )
                }
            }
        }

        if (!nowIndicatorDrawn) {
            NowTimeIndicator(currentTime = state.currentTime)
        }
    }
}

// ============================================================================
// 3. MÀN HÌNH MINIMAL STREAM (Dòng chảy tối giản & Tinh gọn)
// ============================================================================
@Composable
private fun TodayMinimalStreamView(
    state: TodayUiState.Success,
    onAction: (TodayUiAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl)
    ) {
        // Minimal Top Header
        Text(
            text = stringResource(R.string.today_greeting_morning, state.userName),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = CueTextPrimary
        )
        Text(
            text = state.currentDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
            style = MaterialTheme.typography.bodyMedium,
            color = CueTextSecondary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Lg))

        // Inline Minimal NOW Section
        Text(
            text = stringResource(R.string.today_section_now).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = CueTextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.today_free_until, "10:30"),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CueTextPrimary
        )
        Spacer(modifier = Modifier.height(CueSpacing.Xs))
        val suggestedTitle = state.aiSuggestion?.suggestedTaskTitle ?: "Study session"
        val suggestedStart = state.aiSuggestion?.focusWindowStart ?: LocalTime.of(10, 30)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.today_suggested_focus_fits, suggestedTitle, "90 min"),
                style = MaterialTheme.typography.bodyMedium,
                color = CueAccent,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onAction(TodayUiAction.AcceptFocusBlock(suggestedTitle, suggestedStart)) },
                shape = RoundedCornerShape(CueSpacing.Xl),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CueAccent,
                    contentColor = CueSurface
                ),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = stringResource(R.string.today_free_slot_action_add),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        // Stream of events
        state.timelineItems.forEach { item ->
            when (item) {
                is RoutineEvent -> {
                    TimelineEventCard(
                        startTime = item.startTime,
                        title = item.title,
                        categoryTag = item.categoryTag,
                        isPast = item.isPast
                    )
                }
                is TaskEvent -> {
                    TimelineEventCard(
                        startTime = item.startTime,
                        title = item.title,
                        categoryTag = item.category,
                        isPast = item.isPast,
                        isCompleted = item.isCompleted,
                        priority = item.priority,
                        isVirtual = item.isVirtual,
                        attendees = item.attendeeCount,
                        onToggleCompletion = { onAction(TodayUiAction.ToggleTaskCompletion(item.id.removePrefix("task_"), it)) }
                    )
                }
                is FreeSlotBlock -> {
                    FreeSlotCard(
                        startTime = item.startTime,
                        durationMinutes = item.durationMinutes,
                        onAddClick = { onAction(TodayUiAction.AddEventClick(item.startTime)) }
                    )
                }
                else -> {}
            }
        }
    }
}

// ============================================================================
// 4. MÀN HÌNH BUSY DAY & CONFLICT (Phát hiện & Xử lý xung đột)
// ============================================================================
@Composable
private fun TodayConflictView(
    state: TodayUiState.Success,
    onAction: (TodayUiAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl)
    ) {
        // Intelligence Overview Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CueBorder, RoundedCornerShape(CueSpacing.Lg)),
            shape = RoundedCornerShape(CueSpacing.Lg),
            color = CueSurface
        ) {
            Row(
                modifier = Modifier.padding(CueSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CueSurfaceSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = CueTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(CueSpacing.Md))

                Column {
                    Text(
                        text = stringResource(R.string.today_busy_afternoon),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CueTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.today_conflict_busy_summary, 3),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CueTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CueSpacing.Lg))

        // Display Active Conflict if present or fallback sample conflict
        val activeOrSampleConflict = state.activeConflict ?: ScheduleConflict(
            conflictTime = LocalTime.of(14, 30),
            primaryTitle = "Project Meeting",
            conflictingTitle = "Study Session",
            locationOrDetails = "Conference Room A",
            suggestedMoveTime = LocalTime.of(17, 0),
            suggestedItemTitle = "Study Session",
            conflictingEventId = "sample_study_session"
        )
        ConflictSection(
            conflict = activeOrSampleConflict,
            onReviewClick = { onAction(TodayUiAction.ResolveConflict(activeOrSampleConflict)) }
        )

        Spacer(modifier = Modifier.height(CueSpacing.Md))

        // Normal timeline below
        state.timelineItems.filter { it is RoutineEvent || it is TaskEvent }.forEach { item ->
            when (item) {
                is RoutineEvent -> {
                    TimelineEventCard(
                        startTime = item.startTime,
                        title = item.title,
                        categoryTag = item.categoryTag,
                        isPast = item.isPast
                    )
                }
                is TaskEvent -> {
                    TimelineEventCard(
                        startTime = item.startTime,
                        title = item.title,
                        categoryTag = item.category,
                        isPast = item.isPast,
                        isCompleted = item.isCompleted,
                        priority = item.priority
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun EmptyTodayView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CueSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.today_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = CueTextPrimary
        )
        Spacer(modifier = Modifier.height(CueSpacing.Sm))
        Text(
            text = stringResource(R.string.today_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = CueTextSecondary
        )
    }
}

@Preview(name = "1. Overview View", showBackground = true)
@Composable
private fun TodayOverviewPreview() {
    SmartReminderTheme {
        TodayScreen(
            uiState = TodayUiState.Success(
                userName = "Alex",
                userAvatarUrl = null,
                currentDate = LocalDate.now(),
                currentTime = LocalTime.of(10, 12),
                progress = DailyProgress(4, 7),
                aiSuggestion = AiSuggestion(
                    id = "1",
                    message = "Your afternoon looks busy. I found a 90-minute focus window between 10:30 and 12:00.",
                    suggestedTaskTitle = "Android assignment",
                    focusWindowStart = LocalTime.of(10, 30)
                ),
                timelineItems = listOf(
                    TaskEvent(
                        id = "1",
                        title = "Submit Android assignment",
                        startTime = LocalTime.of(9, 0),
                        category = "University",
                        priority = TaskPriority.HIGH,
                        isPast = false
                    ),
                    TaskEvent(
                        id = "2",
                        title = "Android Project Meeting",
                        startTime = LocalTime.of(14, 0),
                        isVirtual = true,
                        attendeeCount = 4,
                        isPast = false
                    )
                ),
                activeConflict = null,
                selectedViewMode = TodayViewMode.OVERVIEW
            ),
            onAction = {}
        )
    }
}

@Preview(name = "2. Timeline View", showBackground = true)
@Composable
private fun TodayTimelinePreview() {
    SmartReminderTheme {
        TodayScreen(
            uiState = TodayUiState.Success(
                userName = "Alex",
                userAvatarUrl = null,
                currentDate = LocalDate.now(),
                currentTime = LocalTime.of(10, 12),
                progress = DailyProgress(4, 7),
                aiSuggestion = null,
                timelineItems = listOf(
                    TaskEvent(
                        id = "1",
                        title = "Class",
                        startTime = LocalTime.of(9, 0),
                        category = "University event",
                        isPast = true
                    ),
                    FreeSlotBlock(
                        id = "2",
                        startTime = LocalTime.of(10, 0),
                        endTime = LocalTime.of(11, 30),
                        durationMinutes = 90
                    ),
                    RoutineEvent(
                        id = "3",
                        routineId = RoutineId("r1"),
                        routineItemId = RoutineItemId("ri1"),
                        title = "Study Kotlin",
                        routineName = "Study routine",
                        startTime = LocalTime.of(11, 30)
                    ),
                    TaskEvent(
                        id = "4",
                        title = "Android Project Meeting",
                        startTime = LocalTime.of(14, 0),
                        category = "Group event",
                        attendeeCount = 4,
                        isPast = false
                    ),
                    SuggestedFocusBlock(
                        id = "5",
                        taskTitle = "Android assignment",
                        startTime = LocalTime.of(15, 0),
                        durationMinutes = 120,
                        reason = "Suggested focus block"
                    ),
                    FreeSlotBlock(
                        id = "6",
                        startTime = LocalTime.of(17, 0),
                        endTime = LocalTime.of(19, 0),
                        durationMinutes = 120
                    ),
                    RoutineEvent(
                        id = "7",
                        routineId = RoutineId("r2"),
                        routineItemId = RoutineItemId("ri2"),
                        title = "Workout",
                        routineName = "Workout routine",
                        startTime = LocalTime.of(19, 0)
                    )
                ),
                activeConflict = null,
                selectedViewMode = TodayViewMode.TIMELINE
            ),
            onAction = {}
        )
    }
}

@Preview(name = "3. Minimal Stream View", showBackground = true)
@Composable
private fun TodayMinimalStreamPreview() {
    SmartReminderTheme {
        TodayScreen(
            uiState = TodayUiState.Success(
                userName = "Alex",
                userAvatarUrl = null,
                currentDate = LocalDate.now(),
                currentTime = LocalTime.of(10, 12),
                progress = DailyProgress(4, 7),
                aiSuggestion = null,
                timelineItems = listOf(
                    TaskEvent(
                        id = "1",
                        title = "Class · University",
                        startTime = LocalTime.of(9, 0),
                        isPast = true
                    ),
                    FreeSlotBlock(
                        id = "2",
                        startTime = LocalTime.of(10, 0),
                        endTime = LocalTime.of(11, 30),
                        durationMinutes = 90
                    ),
                    RoutineEvent(
                        id = "3",
                        routineId = RoutineId("r1"),
                        routineItemId = RoutineItemId("ri1"),
                        title = "Study Kotlin · Study routine",
                        routineName = "Study routine",
                        startTime = LocalTime.of(11, 30)
                    ),
                    TaskEvent(
                        id = "4",
                        title = "Android Project Meeting · Group",
                        startTime = LocalTime.of(14, 0),
                        attendeeCount = 4,
                        isPast = false
                    )
                ),
                activeConflict = null,
                selectedViewMode = TodayViewMode.MINIMAL_STREAM
            ),
            onAction = {}
        )
    }
}

@Preview(name = "4. Conflict View", showBackground = true)
@Composable
private fun TodayConflictPreview() {
    SmartReminderTheme {
        TodayScreen(
            uiState = TodayUiState.Success(
                userName = "Alex",
                userAvatarUrl = null,
                currentDate = LocalDate.now(),
                currentTime = LocalTime.of(10, 12),
                progress = DailyProgress(2, 5),
                aiSuggestion = null,
                timelineItems = listOf(
                    TaskEvent(
                        id = "1",
                        title = "Lecture",
                        startTime = LocalTime.of(13, 0),
                        category = "Room 302",
                        isPast = false
                    ),
                    TaskEvent(
                        id = "2",
                        title = "Project Meeting",
                        startTime = LocalTime.of(14, 30),
                        category = "Conference Room A",
                        isPast = false
                    )
                ),
                activeConflict = ScheduleConflict(
                    conflictTime = LocalTime.of(14, 30),
                    primaryTitle = "Project Meeting",
                    conflictingTitle = "Study Session",
                    locationOrDetails = "Conference Room A",
                    suggestedMoveTime = LocalTime.of(17, 0),
                    suggestedItemTitle = "Study Session"
                ),
                selectedViewMode = TodayViewMode.BUSY_CONFLICT
            ),
            onAction = {}
        )
    }
}
