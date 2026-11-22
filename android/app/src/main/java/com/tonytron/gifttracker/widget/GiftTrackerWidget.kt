package com.tonytron.gifttracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tonytron.gifttracker.MainActivity
import com.tonytron.gifttracker.data.local.LocalGiftStorage
import com.tonytron.gifttracker.data.model.GiftDataset

class GiftTrackerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SMALL_BOX,
            WIDE_COMPACT,
            EXPANDED_LIST
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dataset = LocalGiftStorage.readDatasetBlocking(context)
        provideContent {
            GlanceTheme {
                GiftWidgetContent(context = context, dataset = dataset)
            }
        }
    }

    @Composable
    private fun GiftWidgetContent(context: Context, dataset: GiftDataset) {
        val size = LocalSize.current
        val activeEvent = dataset.events.firstOrNull { !it.isArchived && it.isActive }
            ?: dataset.events.firstOrNull()
        val eventName = activeEvent?.let { "${it.emoji} ${it.name}" } ?: "🎄 Gift Tracker"

        val eventGifts = if (activeEvent != null) {
            dataset.gifts.filter { it.eventId == activeEvent.id }
        } else {
            dataset.gifts.filter { it.eventId != null }
        }

        val personMap = dataset.people.associateBy { it.id }
        val totalBudget = dataset.people.sumOf { it.budget }
        val spent = eventGifts.sumOf { it.cost }
        val progress = if (totalBudget > 0.0) {
            (spent / totalBudget).toFloat().coerceIn(0f, 1f)
        } else {
            val totalCount = eventGifts.size
            if (totalCount > 0) eventGifts.count { it.bought }.toFloat() / totalCount else 0f
        }

        val toBuyList = eventGifts.filter { !it.bought }
        val toWrapCount = eventGifts.count { it.bought && !it.wrapped }
        val ideasCount = dataset.gifts.count { it.eventId == null }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val quickAddIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_NEW_GIFT
            putExtra(EXTRA_OPEN_COMPOSER, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(openAppIntent))
        ) {
            // Header Row: Active Occasion + "+ Gift" Pill Button
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = eventName,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    val subtitle = if (totalBudget > 0) {
                        "$${spent.toInt()} / $${totalBudget.toInt()} budget"
                    } else {
                        "${eventGifts.size} planned • $ideasCount ideas"
                    }
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }

                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(16.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(actionStartActivity(quickAddIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Gift",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Material 3 Progress Bar
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .cornerRadius(4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Status summary pills
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetStatBadge(label = "${toBuyList.size} to buy", emphasized = toBuyList.isNotEmpty())
                Spacer(modifier = GlanceModifier.width(6.dp))
                WidgetStatBadge(label = "$toWrapCount to wrap", emphasized = false)
                if (size.width >= 240.dp) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    WidgetStatBadge(label = "$ideasCount ideas", emphasized = false)
                }
            }

            // Expanded list of next unbought / unwrapped items when widget is tall enough
            if (size.height >= 165.dp) {
                Spacer(modifier = GlanceModifier.height(10.dp))
                val upcoming = (toBuyList + eventGifts.filter { it.bought && !it.wrapped })
                    .distinctBy { it.id }
                    .take(3)

                if (upcoming.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(GlanceTheme.colors.secondaryContainer)
                            .cornerRadius(14.dp)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (eventGifts.isEmpty()) {
                                "Tap + Gift to add your first gift or idea!"
                            } else {
                                "🎉 All ${eventGifts.size} gifts bought & wrapped!"
                            },
                            style = TextStyle(
                                color = GlanceTheme.colors.onSecondaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                } else {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        upcoming.forEachIndexed { idx, item ->
                            if (idx > 0) Spacer(modifier = GlanceModifier.height(5.dp))
                            val pName = item.personId?.let { personMap[it]?.name } ?: "Idea"
                            val statusTag = if (!item.bought) "Buy" else "Wrap"
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .background(GlanceTheme.colors.secondaryContainer)
                                    .cornerRadius(12.dp)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$pName: ${item.title}",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSecondaryContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Spacer(modifier = GlanceModifier.width(6.dp))
                                Text(
                                    text = item.displayPrice().ifBlank { statusTag },
                                    style = TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetStatBadge(label: String, emphasized: Boolean) {
        Box(
            modifier = GlanceModifier
                .background(
                    if (emphasized) GlanceTheme.colors.primaryContainer
                    else GlanceTheme.colors.surfaceVariant
                )
                .cornerRadius(10.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = if (emphasized) GlanceTheme.colors.onPrimaryContainer
                    else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }

    companion object {
        const val ACTION_WIDGET_NEW_GIFT = "com.tonytron.gifttracker.ACTION_WIDGET_NEW_GIFT"
        const val EXTRA_OPEN_COMPOSER = "open_composer"

        private val SMALL_BOX = DpSize(160.dp, 110.dp)
        private val WIDE_COMPACT = DpSize(260.dp, 120.dp)
        private val EXPANDED_LIST = DpSize(260.dp, 190.dp)

        suspend fun refreshAll(context: Context) {
            runCatching {
                GiftTrackerWidget().updateAll(context)
            }
        }
    }
}
