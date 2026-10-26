package com.muhammadridha.fjalar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.service.DistanceCalculator
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import com.muhammadridha.fjalar.resources.Res
import com.muhammadridha.fjalar.resources.editor_cancel
import com.muhammadridha.fjalar.resources.search_category_all
import com.muhammadridha.fjalar.resources.search_category_metro
import com.muhammadridha.fjalar.resources.search_category_train
import com.muhammadridha.fjalar.resources.search_category_transit
import com.muhammadridha.fjalar.resources.search_clear
import com.muhammadridha.fjalar.resources.search_empty_body
import com.muhammadridha.fjalar.resources.search_empty_title
import com.muhammadridha.fjalar.resources.search_placeholder
import com.muhammadridha.fjalar.resources.search_recent_title
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

internal fun categoryEmoji(category: PlaceCategory): String = when (category) {
    PlaceCategory.TRAIN_STATION -> "🚆"
    PlaceCategory.METRO_STATION -> "🚇"
    PlaceCategory.TRANSIT_STOP -> "🚏"
    PlaceCategory.LANDMARK -> "🏛️"
    PlaceCategory.GENERAL -> "📍"
}

@Composable
fun SearchPlacesSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResultPlace>,
    selectedCategory: PlaceCategory?,
    onCategorySelect: (PlaceCategory?) -> Unit,
    onPlaceSelect: (SearchResultPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    userLocation: GeoCoordinates? = null,
    distanceCalculator: DistanceCalculator = HaversineCalculator,
    locale: AppLocale = LiquidGlassTheme.locale,
) {
    val colors = LiquidGlassTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                level = GlassLevel.MODAL,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            )
            .padding(top = 10.dp, bottom = 16.dp),
    ) {
        // Top Grab Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .background(
                    color = colors.textSecondary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(2.dp),
                ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Header Row: Title & Cancel button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.search_recent_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )

            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.editor_cancel),
                    color = colors.primary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Frosted Search Input Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🔍",
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 8.dp),
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (query.isNotBlank()) {
                val clearDescription = stringResource(Res.string.search_clear)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = { onQueryChange("") },
                        )
                        .semantics { contentDescription = clearDescription }
                        .background(colors.textSecondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✕",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryChip(
                label = stringResource(Res.string.search_category_all),
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
                colors = colors,
            )

            CategoryChip(
                label = stringResource(Res.string.search_category_train),
                selected = selectedCategory == PlaceCategory.TRAIN_STATION,
                onClick = {
                    onCategorySelect(
                        if (selectedCategory == PlaceCategory.TRAIN_STATION) null else PlaceCategory.TRAIN_STATION
                    )
                },
                colors = colors,
            )

            CategoryChip(
                label = stringResource(Res.string.search_category_metro),
                selected = selectedCategory == PlaceCategory.METRO_STATION,
                onClick = {
                    onCategorySelect(
                        if (selectedCategory == PlaceCategory.METRO_STATION) null else PlaceCategory.METRO_STATION
                    )
                },
                colors = colors,
            )

            CategoryChip(
                label = stringResource(Res.string.search_category_transit),
                selected = selectedCategory == PlaceCategory.TRANSIT_STOP,
                onClick = {
                    onCategorySelect(
                        if (selectedCategory == PlaceCategory.TRANSIT_STOP) null else PlaceCategory.TRANSIT_STOP
                    )
                },
                colors = colors,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Loading Indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp)
                    .height(3.dp),
                color = colors.primary,
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(modifier = Modifier.height(7.dp))
        }

        // Results List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (results.isEmpty() && query.isNotBlank() && !isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "🔍",
                            fontSize = 32.sp,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(Res.string.search_empty_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.search_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                itemsIndexed(results, key = { _, place -> place.id }) { index, place ->
                    SearchResultRow(
                        place = place,
                        userLocation = userLocation,
                        distanceCalculator = distanceCalculator,
                        onClick = { onPlaceSelect(place) },
                        colors = colors,
                    )

                    if (index < results.lastIndex) {
                        HorizontalDivider(
                            color = colors.textSecondary.copy(alpha = 0.1f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 52.dp, end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    colors: LiquidGlassColors,
) {
    val backgroundModifier = if (selected) {
        Modifier.background(color = colors.primary, shape = CircleShape)
    } else {
        Modifier.glassSurface(level = GlassLevel.PILL, shape = CircleShape)
    }

    Box(
        modifier = Modifier
            .then(backgroundModifier)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.onPrimary else colors.textSecondary,
            ),
        )
    }
}

@Composable
private fun SearchResultRow(
    place: SearchResultPlace,
    userLocation: GeoCoordinates?,
    distanceCalculator: DistanceCalculator,
    onClick: () -> Unit,
    colors: LiquidGlassColors,
) {
    val distanceMeters = remember(place.coordinates, userLocation) {
        userLocation?.let {
            distanceCalculator.calculateDistanceMeters(it, place.coordinates).roundToInt()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading Category Emoji Container
        Box(
            modifier = Modifier
                .size(38.dp)
                .glassSurface(level = GlassLevel.PILL, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = categoryEmoji(place.category),
                fontSize = 18.sp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Place Name & Detail
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!place.detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = place.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Trailing Distance Readout
        if (distanceMeters != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatDistance(distanceMeters),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textSecondary,
                modifier = Modifier
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
