package com.childfilter.app.ui.screens

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.ui.components.FaceComparisonSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

private data class PhotoItem(
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long   // epoch seconds
)

// ---------------------------------------------------------------------------
// Filter / sort enums
// ---------------------------------------------------------------------------

private enum class DateFilter { ALL, TODAY, THIS_WEEK, THIS_MONTH }
private enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST }

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MatchedPhotosScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Raw photo list loaded from MediaStore
    val allPhotos = remember { mutableStateListOf<PhotoItem>() }

    // Selection state
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var selectionMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Search / filter / sort state
    var searchQuery by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(DateFilter.ALL) }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }

    // Face comparison sheet state
    var comparisonPhoto by remember { mutableStateOf<Uri?>(null) }

    // Collect children for reference photo
    val prefs = AppPreferences.getInstance(context)
    val children by prefs.getChildren().collectAsState(initial = emptyList())
    val referenceUri: Uri? = children.firstOrNull()?.photoUri?.let { Uri.parse(it) }

    // Exit selection mode on back press
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedUris.clear()
    }

    // -----------------------------------------------------------------------
    // Load photos from MediaStore (IO thread)
    // -----------------------------------------------------------------------
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("Pictures/My Child Photos%")
            val sortOrderStr = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrderStr
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: ""
                    val date = cursor.getLong(dateColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    withContext(Dispatchers.Main) {
                        allPhotos.add(PhotoItem(uri, name, date))
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Derived: filtered + sorted list
    // -----------------------------------------------------------------------
    val filteredPhotos by remember {
        derivedStateOf {
            val now = LocalDate.now(ZoneId.systemDefault())
            val weekStart = now.minusDays(now.dayOfWeek.value.toLong() - 1)
            val monthStart = now.withDayOfMonth(1)

            val filtered = allPhotos.filter { photo ->
                val matchesSearch = searchQuery.isBlank() ||
                    photo.displayName.contains(searchQuery, ignoreCase = true) ||
                    Instant.ofEpochSecond(photo.dateAdded)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString()
                        .contains(searchQuery)

                val photoDate = Instant.ofEpochSecond(photo.dateAdded)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val matchesDate = when (dateFilter) {
                    DateFilter.ALL -> true
                    DateFilter.TODAY -> photoDate == now
                    DateFilter.THIS_WEEK -> !photoDate.isBefore(weekStart)
                    DateFilter.THIS_MONTH -> !photoDate.isBefore(monthStart)
                }

                matchesSearch && matchesDate
            }

            when (sortOrder) {
                SortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.dateAdded }
                SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.dateAdded }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    fun exitSelectionMode() {
        selectionMode = false
        selectedUris.clear()
    }

    fun deleteSelected() {
        scope.launch(Dispatchers.IO) {
            val toDelete = selectedUris.toList()
            for (uri in toDelete) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                allPhotos.removeAll { it.uri in toDelete.toSet() }
                selectedUris.clear()
                selectionMode = false
            }
        }
    }

    fun shareSelected() {
        val uriList = ArrayList(selectedUris.toList())
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${uriList.size} photo(s)"))
    }

    // -----------------------------------------------------------------------
    // UI
    // -----------------------------------------------------------------------
    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedUris.size} selected",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit selection",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    actions = {
                        // Select All / Deselect All
                        IconButton(onClick = {
                            if (selectedUris.size == allPhotos.size) {
                                selectedUris.clear()
                            } else {
                                selectedUris.clear()
                                selectedUris.addAll(allPhotos.map { it.uri })
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = if (selectedUris.size == allPhotos.size)
                                    "Deselect all"
                                else
                                    "Select all",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        // Share
                        IconButton(
                            onClick = { shareSelected() },
                            enabled = selectedUris.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share selected",
                                tint = if (selectedUris.isNotEmpty())
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                            )
                        }
                        // Delete
                        IconButton(
                            onClick = { if (selectedUris.isNotEmpty()) showDeleteDialog = true },
                            enabled = selectedUris.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete selected",
                                tint = if (selectedUris.isNotEmpty())
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Matched Photos") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            // ----------------------------------------------------------------
            // Search bar
            // ----------------------------------------------------------------
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search photos\u2026") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ----------------------------------------------------------------
            // Filter chips row + sort button
            // ----------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = dateFilter == DateFilter.ALL,
                    onClick = { dateFilter = DateFilter.ALL },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = dateFilter == DateFilter.TODAY,
                    onClick = { dateFilter = DateFilter.TODAY },
                    label = { Text("Today") }
                )
                FilterChip(
                    selected = dateFilter == DateFilter.THIS_WEEK,
                    onClick = { dateFilter = DateFilter.THIS_WEEK },
                    label = { Text("This week") }
                )
                FilterChip(
                    selected = dateFilter == DateFilter.THIS_MONTH,
                    onClick = { dateFilter = DateFilter.THIS_MONTH },
                    label = { Text("This month") }
                )

                Box(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = {
                        sortOrder = if (sortOrder == SortOrder.NEWEST_FIRST)
                            SortOrder.OLDEST_FIRST
                        else
                            SortOrder.NEWEST_FIRST
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = if (sortOrder == SortOrder.NEWEST_FIRST)
                            "Sorted: Newest first \u2014 tap for Oldest first"
                        else
                            "Sorted: Oldest first \u2014 tap for Newest first",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ----------------------------------------------------------------
            // Photo count
            // ----------------------------------------------------------------
            Text(
                text = "${filteredPhotos.size} photo${if (filteredPhotos.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // ----------------------------------------------------------------
            // Grid or empty state
            // ----------------------------------------------------------------
            if (filteredPhotos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || dateFilter != DateFilter.ALL)
                                "No photos match your filters"
                            else
                                "No matched photos yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isEmpty() && dateFilter == DateFilter.ALL) {
                            Text(
                                text = "When the watcher service finds photos\nmatching your child's face, they will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredPhotos, key = { it.uri.toString() }) { photo ->
                        val isSelected = photo.uri in selectedUris

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            if (isSelected) selectedUris.remove(photo.uri)
                                            else selectedUris.add(photo.uri)
                                            if (selectedUris.isEmpty()) selectionMode = false
                                        } else {
                                            val encoded = Uri.encode(photo.uri.toString())
                                            navController.navigate("photo_detail/$encoded")
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            comparisonPhoto = photo.uri
                                        } else {
                                            // In selection mode, long-press selects like a tap
                                            if (isSelected) selectedUris.remove(photo.uri)
                                            else selectedUris.add(photo.uri)
                                        }
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(photo.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Matched photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Selection overlay
                            if (selectionMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isSelected)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else
                                                Color.Transparent
                                        )
                                        .then(
                                            if (isSelected)
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            else Modifier
                                        )
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .background(
                                                color = Color.White,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Face Comparison Bottom Sheet (long-press outside selection mode)
    // -----------------------------------------------------------------------
    comparisonPhoto?.let { uri ->
        FaceComparisonSheet(
            matchedUri = uri,
            referenceUri = referenceUri,
            confidence = 0.87f,
            onDismiss = { comparisonPhoto = null }
        )
    }

    // -----------------------------------------------------------------------
    // Batch delete confirmation dialog
    // -----------------------------------------------------------------------
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${selectedUris.size} photo(s)?") },
            text = { Text("These photos will be permanently removed from storage.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleteSelected()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
