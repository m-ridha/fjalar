package com.muhammadridha.fjalar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.muhammadridha.fjalar.adapters.map.MapUserPosition
import com.muhammadridha.fjalar.ui.components.MapNavigationControls
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.GeoCoordinates
import com.muhammadridha.fjalar.domain.model.PlaceCategory
import com.muhammadridha.fjalar.domain.model.RecentDestination
import com.muhammadridha.fjalar.domain.model.SearchResultPlace
import com.muhammadridha.fjalar.domain.model.ThemeMode
import com.muhammadridha.fjalar.domain.model.TriggerRadius
import com.muhammadridha.fjalar.ui.WindowInsetsLayoutHelper
import com.muhammadridha.fjalar.ui.SearchBackHandler
import com.muhammadridha.fjalar.ui.components.SearchPlacesSheet
import com.muhammadridha.fjalar.ui.components.PlatformMapView
import com.muhammadridha.fjalar.ui.theme.FjalarStringKey
import com.muhammadridha.fjalar.ui.theme.FjalarStrings
import com.muhammadridha.fjalar.ui.theme.GlassLevel
import com.muhammadridha.fjalar.ui.theme.LiquidGlassTheme
import com.muhammadridha.fjalar.ui.theme.glassSurface
import kotlin.math.roundToInt

fun mapPickerConfirmSemanticDescription(
    selectionName: String?,
    coordinates: GeoCoordinates?,
    locale: AppLocale,
): String {
    if (coordinates == null) {
        return FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, locale)
    }
    val targetLabel = selectionName?.takeIf { it.isNotBlank() }
        ?: run {
            val lat = (coordinates.latitude * 10000.0).roundToInt() / 10000.0
            val lon = (coordinates.longitude * 10000.0).roundToInt() / 10000.0
            "$lat, $lon"
        }
    return FjalarStrings.get(FjalarStringKey.ACCESSIBLE_PICKER_CONFIRM, locale, targetLabel)
}

@Composable
fun MapPickerScreen(
    initialCoordinates: GeoCoordinates,
    onConfirmCoordinates: (GeoCoordinates) -> Unit,
    onBack: () -> Unit,
    isSearchVisible: Boolean,
    searchQuery: String,
    searchResults: List<SearchResultPlace>,
    selectedCategory: PlaceCategory?,
    isSearching: Boolean,
    onOpenSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (PlaceCategory?) -> Unit,
    onDismissSearch: () -> Unit,
    modifier: Modifier = Modifier,
    recentDestinations: List<RecentDestination> = emptyList(),
    onSelectRecentDestination: (RecentDestination) -> Unit = {},
    onConfirmDestination: ((GeoCoordinates, String?, PlaceCategory) -> Unit)? = null,
    locale: AppLocale = LiquidGlassTheme.locale,
    initialZoom: Double = 15.0,
    awaitInitialLocation: Boolean = false,
    hasInitialSelection: Boolean = true,
    userPosition: MapUserPosition? = null,
    geofenceRadius: TriggerRadius = TriggerRadius.DEFAULT,
) {
    SearchBackHandler(enabled = isSearchVisible, onBack = onDismissSearch)
    val colors = LiquidGlassTheme.colors
    val isDarkTheme = LiquidGlassTheme.themeMode == ThemeMode.DARK ||
        (LiquidGlassTheme.themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())

    var selection by remember(initialCoordinates, hasInitialSelection) {
        mutableStateOf(MapPickerSelection(initialCoordinates.takeIf { hasInitialSelection }))
    }
    var selectedPlaceName by remember { mutableStateOf<String?>(null) }
    var selectedPlaceCategory by remember { mutableStateOf(PlaceCategory.GENERAL) }
    var viewportRequestId by remember(initialCoordinates, hasInitialSelection) { mutableStateOf(0L) }
    // The next fix should still frame the map: initial framing, or a recenter before any fix.
    var awaitingFirstFix by remember(initialCoordinates, hasInitialSelection) {
        mutableStateOf(awaitInitialLocation)
    }
    var cameraTarget by remember(initialCoordinates) { mutableStateOf(initialCoordinates) }
    var cameraZoom by remember(initialZoom) { mutableStateOf(initialZoom) }
    var preserveZoom by remember { mutableStateOf(true) }
    var mapBearing by remember { mutableStateOf(0.0) }
    var northRequestId by remember { mutableStateOf(0L) }


    LaunchedEffect(userPosition?.coordinates) {
        val coordinates = userPosition?.coordinates ?: return@LaunchedEffect
        if (awaitingFirstFix && selection.coordinates == null) {
            cameraTarget = coordinates
            cameraZoom = 15.0
            preserveZoom = false
            viewportRequestId++
            awaitingFirstFix = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        // Layer 1: 100% Viewport Edge-to-Edge Map
        PlatformMapView(
            center = cameraTarget,
            zoom = cameraZoom,
            pinLocation = selection.coordinates,
            userPosition = userPosition,
            onBearingChanged = { mapBearing = it },
            northRequestId = northRequestId,
            geofenceCenter = selection.coordinates,
            geofenceRadius = geofenceRadius,
            isDarkTheme = isDarkTheme,
            onMapClick = {
                selection = selection.select(it)
                selectedPlaceName = null
                selectedPlaceCategory = PlaceCategory.GENERAL
                cameraTarget = it
                preserveZoom = true
                viewportRequestId++
            },
            onPinDrag = {
                selection = selection.select(it)
                selectedPlaceName = null
                selectedPlaceCategory = PlaceCategory.GENERAL
            },
            destinationEditingEnabled = true,
            onCameraCenterChanged = { center, isUserGesture ->
                selection = selection.onCameraCenterChanged(center, isUserGesture)
                if (isUserGesture) {
                    selectedPlaceName = null
                    selectedPlaceCategory = PlaceCategory.GENERAL
                }
            },
            // Selection follows gestures without issuing a camera reset to initialZoom.
            viewportRequestId = viewportRequestId,
            preserveZoomOnViewportRequest = preserveZoom,
            modifier = Modifier.fillMaxSize(),
        )

        // A neutral crosshair invites deliberate selection; it never claims a user position.
        if (selection.coordinates == null) Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clearAndSetSemantics {
                    contentDescription = FjalarStrings.get(FjalarStringKey.PICKER_HINT, locale)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⌖",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        lineHeight = 28.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Header and map controls share the fold/cutout-safe scroll rectangle.
        Column(
            modifier = WindowInsetsLayoutHelper.safeDrawingPaddingModifier(Modifier)
                .fillMaxWidth()
                .padding(bottom = 100.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = WindowInsetsLayoutHelper
                    .hudTopPaddingModifier(Modifier)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back Button
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                        .clickable(role = Role.Button, onClick = {
                            if (isSearchVisible) onDismissSearch() else onBack()
                        })
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = FjalarStrings.get(FjalarStringKey.NAV_BACK, locale),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        ),
                    )
                }

                // Hint Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = FjalarStrings.get(FjalarStringKey.PICKER_HINT, locale),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .glassSurface(level = GlassLevel.PILL, shape = CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenSearch)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FjalarStrings.get(FjalarStringKey.HUD_SEARCH_PLACEHOLDER, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }

            MapNavigationControls(
                onRecenter = {
                    val fix = userPosition?.coordinates
                    cameraTarget = fix ?: selection.coordinates ?: GeoCoordinates(0.0, 0.0)
                    cameraZoom = if (fix != null || selection.coordinates != null) 15.0 else 2.0
                    preserveZoom = false
                    viewportRequestId++
                    // Tapping recenter before the first fix keeps waiting for it.
                    awaitingFirstFix = fix == null && selection.coordinates == null
                },
                bearing = mapBearing,
                onResetNorth = { northRequestId++ },
                modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // Layer 4: Safe-Area Bottom Floating Action Pill (Confirm)
        Box(
            modifier = WindowInsetsLayoutHelper
                .navigationBottomPaddingModifier(Modifier)
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = {
                    selection.coordinates?.let { coords ->
                        onConfirmCoordinates(coords)
                        onConfirmDestination?.invoke(coords, selectedPlaceName, selectedPlaceCategory)
                    }
                },
                enabled = selection.coordinates != null && !isSearchVisible,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = mapPickerConfirmSemanticDescription(
                            selectedPlaceName,
                            selection.coordinates,
                            locale,
                        )
                    },
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            ) {
                Text(
                    text = "✓ ${FjalarStrings.get(FjalarStringKey.PICKER_CONFIRM, locale)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }

        if (isSearchVisible) {
            SearchPlacesSheet(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                results = searchResults,
                selectedCategory = selectedCategory,
                onCategorySelect = onSelectCategory,
                recentDestinations = recentDestinations,
                onRecentSelect = { destination ->
                    selection = selection.select(destination.coordinates)
                    selectedPlaceName = destination.name
                    selectedPlaceCategory = destination.category
                    cameraTarget = destination.coordinates
                    cameraZoom = 15.0
                    preserveZoom = false
                    viewportRequestId++
                    onSelectRecentDestination(destination)
                    onDismissSearch()
                },
                onPlaceSelect = { place ->
                    selection = selection.select(place)
                    selectedPlaceName = place.name
                    selectedPlaceCategory = place.category
                    cameraTarget = place.coordinates
                    cameraZoom = 15.0
                    preserveZoom = false
                    viewportRequestId++
                    onDismissSearch()
                },
                onDismiss = onDismissSearch,
                isLoading = isSearching,
                userLocation = userPosition?.coordinates,
                locale = locale,
                modifier = WindowInsetsLayoutHelper.safeDrawingPaddingModifier(
                    Modifier.align(Alignment.BottomCenter),
                ).imePadding(),
            )
        }
    }
}
