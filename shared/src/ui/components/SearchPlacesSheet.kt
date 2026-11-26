package com.muhammadridha.fjalar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.service.DistanceCalculator
import com.muhammadridha.fjalar.domain.service.HaversineCalculator
import com.muhammadridha.fjalar.ui.WindowInsetsLayoutHelper
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.fjalarString
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassColors
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import kotlin.math.roundToInt

internal fun filterRecentDestinations(
    recentDestinations: List<RecentDestination>,
    selectedCategory: PlaceCategory?,
): List<RecentDestination> = if (selectedCategory == null) {
    recentDestinations
} else {
    recentDestinations.filter { it.category == selectedCategory }
}

internal fun shouldShowQuickPicks(query: String, recentDestinations: List<RecentDestination>): Boolean =
    query.isBlank() && recentDestinations.isNotEmpty()

internal fun shouldShowEmptyFilteredRecents(
    query: String,
    recentDestinations: List<RecentDestination>,
    visibleRecentDestinations: List<RecentDestination>,
    selectedCategory: PlaceCategory?,
): Boolean =
    query.isBlank() &&
        selectedCategory != null &&
        recentDestinations.isNotEmpty() &&
        visibleRecentDestinations.isEmpty()

internal fun shouldShowEmptySearchResults(
    results: List<SearchResultPlace>,
    query: String,
    isLoading: Boolean,
): Boolean = results.isEmpty() && query.trim().length >= 3 && !isLoading

internal fun quickPickContentDescription(destination: RecentDestination, locale: AppLocale): String =
    FjalarStrings.get(FjalarStringKey.SEARCH_QUICK_PICK_CONTENT_DESC, locale, destination.name)

internal fun categoryEmoji(category: PlaceCategory): String = when (category) {
    PlaceCategory.TRAIN_STATION -> "🚆"
    PlaceCategory.METRO_STATION -> "🚇"
    PlaceCategory.BUS_STOP -> "🚌"
    PlaceCategory.TRANSIT_STOP -> "🚏"
    PlaceCategory.LANDMARK -> "🏛️"
    PlaceCategory.GENERAL -> "📍"
}

@Composable
fun SearchPlacesOverlay(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResultPlace>,
    selectedCategory: PlaceCategory?,
    onCategorySelect: (PlaceCategory?) -> Unit,
    onPlaceSelect: (SearchResultPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    recentDestinations: List<RecentDestination> = emptyList(),
    onRecentSelect: (RecentDestination) -> Unit = {},
    isLoading: Boolean = false,
    userLocation: GeoCoordinates? = null,
    distanceCalculator: DistanceCalculator = HaversineCalculator,
    locale: AppLocale = LiquidGlassTheme.locale,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 6 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 6 }),
        modifier = modifier.fillMaxSize(),
    ) {
        SearchPlacesSheet(
            query = query,
            onQueryChange = onQueryChange,
            results = results,
            selectedCategory = selectedCategory,
            onCategorySelect = onCategorySelect,
            onPlaceSelect = onPlaceSelect,
            onDismiss = onDismiss,
            recentDestinations = recentDestinations,
            onRecentSelect = onRecentSelect,
            isLoading = isLoading,
            userLocation = userLocation,
            distanceCalculator = distanceCalculator,
            locale = locale,
            modifier = Modifier.fillMaxSize(),
        )
    }
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
    recentDestinations: List<RecentDestination> = emptyList(),
    onRecentSelect: (RecentDestination) -> Unit = {},
    isLoading: Boolean = false,
    userLocation: GeoCoordinates? = null,
    distanceCalculator: DistanceCalculator = HaversineCalculator,
    locale: AppLocale = LiquidGlassTheme.locale,
) {
    val colors = LiquidGlassTheme.colors
    val focusRequester = remember { FocusRequester() }
    val backDescription = FjalarStrings.get(FjalarStringKey.NAV_BACK, locale)
    val clearDescription = fjalarString(FjalarStringKey.SEARCH_CLEAR)
    val visibleRecentDestinations = remember(recentDestinations, selectedCategory) {
        filterRecentDestinations(recentDestinations, selectedCategory)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas.copy(alpha = 0.72f))
            .glassSurface(
                level = GlassLevel.MODAL,
                shape = RectangleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        // Top Safe-Area Search Bar: Back Button + Auto-Focused Pill + Fixed-Height Clear Slot
        Row(
            modifier = WindowInsetsLayoutHelper
                .hudTopPaddingModifier(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClick = onDismiss,
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = backDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "←",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .padding(start = 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🔍",
                    fontSize = 15.sp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clearAndSetSemantics {},
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = fjalarString(FjalarStringKey.SEARCH_PLACEHOLDER),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (query.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onQueryChange("") },
                                )
                                .semantics {
                                    role = Role.Button
                                    contentDescription = clearDescription
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colors.textSecondary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "✕",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Category Filter Chips Row
        Row(
            modifier = WindowInsetsLayoutHelper
                .horizontalSafeDrawingPaddingModifier()
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryChip(
                label = fjalarString(FjalarStringKey.SEARCH_CATEGORY_ALL),
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
                colors = colors,
            )

            CategoryChip(
                label = fjalarString(FjalarStringKey.SEARCH_CATEGORY_TRAIN),
                selected = selectedCategory == PlaceCategory.TRAIN_STATION,
                onClick = {
                    onCategorySelect(
                        if (selectedCategory == PlaceCategory.TRAIN_STATION) null else PlaceCategory.TRAIN_STATION
                    )
                },
                colors = colors,
            )

            CategoryChip(
                label = fjalarString(FjalarStringKey.SEARCH_CATEGORY_METRO),
                selected = selectedCategory == PlaceCategory.METRO_STATION,
                onClick = {
                    onCategorySelect(
                        if (selectedCategory == PlaceCategory.METRO_STATION) null else PlaceCategory.METRO_STATION
                    )
                },
                colors = colors,
            )

            CategoryChip(
                label = fjalarString(FjalarStringKey.SEARCH_CATEGORY_BUS),
                selected = selectedCategory == PlaceCategory.BUS_STOP,
                onClick = {
                    onCategorySelect(
                        if (selectedCategory == PlaceCategory.BUS_STOP) null else PlaceCategory.BUS_STOP
                    )
                },
                colors = colors,
            )

            CategoryChip(
                label = fjalarString(FjalarStringKey.SEARCH_CATEGORY_TRANSIT),
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

        // Results & Recent Destinations List
        LazyColumn(
            modifier = WindowInsetsLayoutHelper
                .horizontalSafeDrawingPaddingModifier()
                .fillMaxWidth()
                .weight(1f)
                .then(WindowInsetsLayoutHelper.navigationBottomPaddingModifier())
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (shouldShowQuickPicks(query, visibleRecentDestinations)) {
                item(key = "quick_picks_header") {
                    Text(
                        text = fjalarString(FjalarStringKey.SEARCH_QUICK_PICKS_TITLE),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }

                itemsIndexed(visibleRecentDestinations, key = { _, recent -> "recent_${recent.id}" }) { index, recent ->
                    val contentDesc = quickPickContentDescription(recent, locale)
                    PlaceListRow(
                        name = recent.name,
                        detail = null,
                        category = recent.category,
                        coordinates = recent.coordinates,
                        userLocation = userLocation,
                        distanceCalculator = distanceCalculator,
                        onClick = { onRecentSelect(recent) },
                        colors = colors,
                        locale = locale,
                        rowContentDescription = contentDesc,
                    )

                    if (index < visibleRecentDestinations.lastIndex) {
                        HorizontalDivider(
                            color = colors.textSecondary.copy(alpha = 0.1f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 52.dp, end = 8.dp),
                        )
                    }
                }
            } else if (
                shouldShowEmptyFilteredRecents(
                    query = query,
                    recentDestinations = recentDestinations,
                    visibleRecentDestinations = visibleRecentDestinations,
                    selectedCategory = selectedCategory,
                )
            ) {
                item(key = "quick_picks_empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = categoryEmoji(selectedCategory ?: PlaceCategory.GENERAL),
                            fontSize = 32.sp,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = fjalarString(FjalarStringKey.SEARCH_RECENT_EMPTY_TITLE),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = fjalarString(FjalarStringKey.SEARCH_RECENT_EMPTY_BODY),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (shouldShowEmptySearchResults(results, query, isLoading)) {
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
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = fjalarString(FjalarStringKey.SEARCH_EMPTY_TITLE),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = fjalarString(FjalarStringKey.SEARCH_EMPTY_BODY),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                itemsIndexed(results, key = { _, place -> place.id }) { index, place ->
                    PlaceListRow(
                        name = place.name,
                        detail = place.detail,
                        category = place.category,
                        coordinates = place.coordinates,
                        userLocation = userLocation,
                        distanceCalculator = distanceCalculator,
                        onClick = { onPlaceSelect(place) },
                        colors = colors,
                        locale = locale,
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
            .heightIn(min = 48.dp)
            .then(backgroundModifier)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
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
private fun PlaceListRow(
    name: String,
    detail: String?,
    category: PlaceCategory,
    coordinates: GeoCoordinates,
    userLocation: GeoCoordinates?,
    distanceCalculator: DistanceCalculator,
    onClick: () -> Unit,
    colors: LiquidGlassColors,
    locale: AppLocale,
    rowContentDescription: String? = null,
) {
    val distanceMeters = remember(coordinates, userLocation) {
        userLocation?.let {
            distanceCalculator.calculateDistanceMeters(it, coordinates).roundToInt()
        }
    }

    val semanticsModifier = if (rowContentDescription != null) {
        Modifier.semantics { contentDescription = rowContentDescription }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .then(semanticsModifier)
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
                text = categoryEmoji(category),
                fontSize = 18.sp,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Place Name & Detail
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
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
                text = formatDistance(distanceMeters, locale),
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
