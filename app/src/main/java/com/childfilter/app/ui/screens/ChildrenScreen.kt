package com.childfilter.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.data.ChildProfile
import com.childfilter.app.ml.FaceDetector
import com.childfilter.app.ml.FaceNetHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copies a content URI image to app-internal storage so the path never expires. */
private suspend fun persistPhoto(context: android.content.Context, uri: Uri, id: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "children").also { it.mkdirs() }
            val dest = File(dir, "$id.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (_: Exception) { null }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildrenScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = AppPreferences.getInstance(context)
    val coroutineScope = rememberCoroutineScope()

    val children by prefs.getChildren().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var childName by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPhotoCount by remember { mutableStateOf(0) }
    var croppedFaceBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var computedEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var deleteTarget by remember { mutableStateOf<ChildProfile?>(null) }

    // Bottom sheet state
    var bottomSheetChild by remember { mutableStateOf<ChildProfile?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val galleryLauncher = rememberLauncherForActivityResult(
        // GetMultipleContents uses ACTION_GET_CONTENT with EXTRA_ALLOW_MULTIPLE=true,
        // which works on all Android versions (unlike PickMultipleVisualMedia which
        // falls back to single-select on many Samsung/OEM devices below API 33).
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedPhotoUri = uris.first()
            selectedPhotoCount = uris.size
            croppedFaceBitmap = null
            computedEmbedding = null
            errorMessage = null
            isProcessing = true
            processingProgress = "Processing 0/${uris.size} photos..."
            coroutineScope.launch {
                try {
                    val faceNet = FaceNetHelper(context)
                    val embeddings = mutableListOf<FloatArray>()
                    var lastFace: android.graphics.Bitmap? = null

                    for ((index, uri) in uris.withIndex()) {
                        processingProgress = "Processing ${index + 1}/${uris.size} photos..."
                        val bitmap = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }
                        if (bitmap == null) continue
                        val faceDetector = FaceDetector()
                        val faces = faceDetector.detectAndCrop(bitmap)
                        faceDetector.close()
                        if (faces.isNotEmpty()) {
                            val face = faces.first()
                            embeddings.add(faceNet.getEmbedding(face))
                            if (lastFace == null) lastFace = face
                        }
                    }
                    faceNet.close()

                    if (embeddings.isEmpty()) {
                        errorMessage = "No face detected in any of the selected photos"
                        isProcessing = false
                        return@launch
                    }

                    croppedFaceBitmap = lastFace
                    // Average embeddings across all photos for a more robust reference
                    val size = embeddings[0].size
                    val averaged = FloatArray(size) { i -> embeddings.sumOf { it[i].toDouble() }.toFloat() / embeddings.size }
                    // L2-normalize the averaged embedding
                    val norm = kotlin.math.sqrt(averaged.sumOf { (it * it).toDouble() }.toFloat())
                    computedEmbedding = if (norm == 0f) averaged else FloatArray(size) { averaged[it] / norm }

                    processingProgress = "Detected faces in ${embeddings.size}/${uris.size} photos"
                    isProcessing = false
                } catch (e: Exception) {
                    croppedFaceBitmap = null
                    computedEmbedding = null
                    selectedPhotoUri = null
                    errorMessage = when {
                        e.message?.contains("model not loaded", ignoreCase = true) == true ->
                            "Face recognition is not available. Please reinstall the app."
                        else -> "Failed to process photos: ${e.message}"
                    }
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Children", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showAddDialog = true
                    childName = ""
                    selectedPhotoUri = null
                    croppedFaceBitmap = null
                    computedEmbedding = null
                    errorMessage = null
                    isProcessing = false
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add child")
            }
        }
    ) { paddingValues ->
        if (children.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No children added yet.\nTap + to add your child's face.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(children) { child ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { bottomSheetChild = child },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (child.photoUri != null) {
                                AsyncImage(
                                    model = child.photoUri,
                                    contentDescription = child.name,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = child.name.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = child.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { deleteTarget = child }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet: reference face detail
    if (bottomSheetChild != null) {
        ModalBottomSheet(
            onDismissRequest = { bottomSheetChild = null },
            sheetState = bottomSheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            val child = bottomSheetChild!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { bottomSheetChild = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (child.photoUri != null) {
                    AsyncImage(
                        model = child.photoUri,
                        contentDescription = child.name,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = child.name.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = child.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Reference face for face recognition",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { bottomSheetChild = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }

    // Add child dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Child") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = childName,
                        onValueChange = { childName = it },
                        label = { Text("Child's name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                selectedPhotoUri == null -> "Select Photos from Gallery (up to 5)"
                                selectedPhotoCount > 1 -> "Change Photos ($selectedPhotoCount selected)"
                                else -> "Change Photo"
                            }
                        )
                    }
                    if (isProcessing) {
                        Text(
                            text = processingProgress.ifEmpty { "Detecting face..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!isProcessing && processingProgress.isNotEmpty() && computedEmbedding != null) {
                        Text(
                            text = processingProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (croppedFaceBitmap != null) {
                        Image(
                            bitmap = croppedFaceBitmap!!.asImageBitmap(),
                            contentDescription = "Detected face",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .align(Alignment.CenterHorizontally),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val emb = computedEmbedding
                        if (emb == null) {
                            errorMessage = "Please select a photo with a visible face before saving."
                            return@TextButton
                        }
                        val childId = UUID.randomUUID().toString()
                        coroutineScope.launch {
                            // Copy photo to internal storage so the URI never expires
                            val internalPath = selectedPhotoUri?.let {
                                persistPhoto(context, it, childId)
                            }
                            val profile = ChildProfile(
                                id = childId,
                                name = childName.trim(),
                                embedding = emb,
                                photoUri = internalPath ?: selectedPhotoUri?.toString()
                            )
                            prefs.saveChildren(children + profile)
                        }
                        showAddDialog = false
                    },
                    enabled = childName.isNotBlank() && !isProcessing
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove Child") },
            text = {
                Text("Are you sure you want to remove ${deleteTarget!!.name}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTarget!!
                        coroutineScope.launch {
                            prefs.saveChildren(children.filter { it.id != target.id })
                        }
                        deleteTarget = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
