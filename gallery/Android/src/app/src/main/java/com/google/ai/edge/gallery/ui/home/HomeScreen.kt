/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.home

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
// import com.google.ai.edge.gallery.ui.preview.PreviewModelManagerViewModel
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.proto.ImportedModel
import com.google.ai.edge.gallery.ui.common.TaskIcon
import com.google.ai.edge.gallery.ui.common.tos.TosDialog
import com.google.ai.edge.gallery.ui.common.tos.TosViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.customColors
import com.google.ai.edge.gallery.ui.theme.homePageTitleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGHomeScreen"
private const val TASK_COUNT_ANIMATION_DURATION = 250
private const val ANIMATION_INIT_DELAY = 100L
private const val TOP_APP_BAR_ANIMATION_DURATION = 600
private const val TITLE_FIRST_LINE_ANIMATION_DURATION = 600
private const val TITLE_SECOND_LINE_ANIMATION_DURATION = 600
private const val TITLE_SECOND_LINE_ANIMATION_DURATION2 = 800
private const val TITLE_SECOND_LINE_ANIMATION_START =
  ANIMATION_INIT_DELAY + (TITLE_FIRST_LINE_ANIMATION_DURATION * 0.5).toInt()
private const val TASK_LIST_ANIMATION_START = TITLE_SECOND_LINE_ANIMATION_START + 110
private const val TASK_CARD_ANIMATION_DELAY_OFFSET = 100
private const val TASK_CARD_ANIMATION_DURATION = 600
private const val CONTENT_COMPOSABLES_ANIMATION_DURATION = 1200
private const val CONTENT_COMPOSABLES_OFFSET_Y = 16

/** Navigation destination data */
object HomeScreenDestination {
  @StringRes val titleRes = R.string.app_name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  tosViewModel: TosViewModel,
  navigateToTaskScreen: (Task) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  val uiState by modelManagerViewModel.uiState.collectAsState()
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showImportModelSheet by remember { mutableStateOf(false) }
  var showUnsupportedFileTypeDialog by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState()
  var showImportDialog by remember { mutableStateOf(false) }
  var showImportingDialog by remember { mutableStateOf(false) }
  var showTosDialog by remember { mutableStateOf(!tosViewModel.getIsTosAccepted()) }
  val selectedLocalModelFileUri = remember { mutableStateOf<Uri?>(null) }
  val selectedImportedModelInfo = remember { mutableStateOf<ImportedModel?>(null) }
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  val filePickerLauncher: ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          val fileName = getFileName(context = context, uri = uri)
          Log.d(TAG, "Selected file: $fileName")
          if (fileName != null && !fileName.endsWith(".task") && !fileName.endsWith(".litertlm")) {
            showUnsupportedFileTypeDialog = true
          } else {
            selectedLocalModelFileUri.value = uri
            showImportDialog = true
          }
        } ?: run { Log.d(TAG, "No file selected or URI is null.") }
      } else {
        Log.d(TAG, "File picking cancelled.")
      }
    }

  // Show home screen content when TOS has been accepted.
  if (!showTosDialog) {
    // The code below manages the display of the model allowlist loading indicator with a debounced
    // delay. It ensures that a progress indicator is only shown if the loading operation
    // (represented by `uiState.loadingModelAllowlist`) takes longer than 200 milliseconds.
    // If the loading completes within 200ms, the indicator is never shown,
    // preventing a "flicker" and improving the perceived responsiveness of the UI.
    // The `loadingModelAllowlistDelayed` state is used to control the actual
    // visibility of the indicator based on this debounced logic.
    var loadingModelAllowlistDelayed by remember { mutableStateOf(false) }
    // This effect runs whenever uiState.loadingModelAllowlist changes
    LaunchedEffect(uiState.loadingModelAllowlist) {
      if (uiState.loadingModelAllowlist) {
        // If loading starts, wait for 200ms
        delay(200)
        // After 200ms, check if loadingModelAllowlist is still true
        if (uiState.loadingModelAllowlist) {
          loadingModelAllowlistDelayed = true
        }
      } else {
        // If loading finishes, immediately hide the indicator
        loadingModelAllowlistDelayed = false
      }
    }

    // Label and spinner to show when in the process of loading model allowlist.
    if (loadingModelAllowlistDelayed) {
      Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator(
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          strokeWidth = 3.dp,
          modifier = Modifier.padding(end = 8.dp).size(20.dp),
        )
        Text(
          stringResource(R.string.loading_model_list),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    // Main UI when allowlist is done loading.
    if (!loadingModelAllowlistDelayed && !uiState.loadingModelAllowlist) {
      Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
          // Top bar animation:
          //
          // Fade in and move down at the same time.
          val progress =
            rememberDelayedAnimationProgress(
              initialDelay = ANIMATION_INIT_DELAY - 50,
              animationDurationMs = TOP_APP_BAR_ANIMATION_DURATION,
              animationLabel = "top bar",
            )
          Box(
            modifier =
              Modifier.graphicsLayer {
                alpha = progress
                translationY = ((-16).dp * (1 - progress)).toPx()
              }
          ) {
            GalleryTopAppBar(
              title = stringResource(HomeScreenDestination.titleRes),
              rightAction =
                AppBarAction(
                  actionType = AppBarActionType.APP_SETTING,
                  actionFn = { showSettingsDialog = true },
                ),
              scrollBehavior = scrollBehavior,
            )
          }
        },
        floatingActionButton = {
          // A floating action button to show "import model" bottom sheet.
          SmallFloatingActionButton(
            onClick = { showImportModelSheet = true },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.secondary,
          ) {
            Icon(Icons.Filled.Add, "")
          }
        },
      ) { innerPadding ->
        // Outer box for coloring the background edge to edge.
        Box(
          contentAlignment = Alignment.TopCenter,
          modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
          // Inner box to hold content.
          Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
          ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
              // App title and intro text.
              Column(
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                AppTitle()
                IntroText()
              }

              TaskList(tasks = uiState.tasks, navigateToTaskScreen = navigateToTaskScreen)
            }

            SnackbarHost(
              hostState = snackbarHostState,
              modifier = Modifier.align(alignment = Alignment.BottomCenter).padding(bottom = 32.dp),
            )
          }
        }
      }
    }
  }

  // Show TOS dialog for users to accept.
  if (showTosDialog) {
    TosDialog(
      onTosAccepted = {
        showTosDialog = false
        tosViewModel.acceptTos()
      }
    )
  }

  // Settings dialog.
  if (showSettingsDialog) {
    SettingsDialog(
      curThemeOverride = modelManagerViewModel.readThemeOverride(),
      modelManagerViewModel = modelManagerViewModel,
      onDismissed = { showSettingsDialog = false },
    )
  }

  // Import model bottom sheet.
  if (showImportModelSheet) {
    ModalBottomSheet(onDismissRequest = { showImportModelSheet = false }, sheetState = sheetState) {
      Text(
        "Import model",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
      )
      Box(
        modifier =
          Modifier.clickable {
            coroutineScope.launch {
              // Give it sometime to show the click effect.
              delay(200)
              showImportModelSheet = false

              // Show file picker.
              val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                  addCategory(Intent.CATEGORY_OPENABLE)
                  type = "*/*"
                  // Single select.
                  putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                }
              filePickerLauncher.launch(intent)
            }
          }
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
          Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = "")
          Text("From local model file")
        }
      }
    }
  }

  // Import dialog
  if (showImportDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      ModelImportDialog(
        uri = uri,
        onDismiss = { showImportDialog = false },
        onDone = { info ->
          selectedImportedModelInfo.value = info
          showImportDialog = false
          showImportingDialog = true
        },
      )
    }
  }

  // Importing in progress dialog.
  if (showImportingDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      selectedImportedModelInfo.value?.let { info ->
        ModelImportingDialog(
          uri = uri,
          info = info,
          onDismiss = { showImportingDialog = false },
          onDone = {
            modelManagerViewModel.addImportedLlmModel(info = it)
            showImportingDialog = false

            // Show a snack bar for successful import.
            scope.launch { snackbarHostState.showSnackbar("Model imported successfully") }
          },
        )
      }
    }
  }

  // Alert dialog for unsupported file type.
  if (showUnsupportedFileTypeDialog) {
    AlertDialog(
      onDismissRequest = { showUnsupportedFileTypeDialog = false },
      title = { Text("Unsupported file type") },
      text = { Text("Only \".task\" or \".litertlm\" file type is supported.") },
      confirmButton = {
        Button(onClick = { showUnsupportedFileTypeDialog = false }) {
          Text(stringResource(R.string.ok))
        }
      },
    )
  }

  if (uiState.loadingModelAllowlistError.isNotEmpty()) {
    AlertDialog(
      icon = {
        Icon(Icons.Rounded.Error, contentDescription = "", tint = MaterialTheme.colorScheme.error)
      },
      title = { Text(uiState.loadingModelAllowlistError) },
      text = { Text("Please check your internet connection and try again later.") },
      onDismissRequest = { modelManagerViewModel.loadModelAllowlist() },
      confirmButton = {
        TextButton(onClick = { modelManagerViewModel.loadModelAllowlist() }) { Text("Retry") }
      },
    )
  }
}

@Composable
private fun AppTitle() {
  val firstLineText = stringResource(R.string.app_name_first_part)
  val secondLineText = stringResource(R.string.app_name_second_part)
  val titleColor = MaterialTheme.customColors.appTitleGradientColors[1]

  // First line text "Google AI" and its animation.
  //
  // The animation starts with the first line of text swiping in from left to right, progressively
  // revealing itself in the title color (blue). Then, after a brief delay, the exact same text, but
  // in the onSurface color (which is black in light mode), begins its own left-to-right swiping
  // animation. This second animation is positioned directly on top of the first, appearing just as
  // the initial reveal is finishing or has just completed, creating a layered and dynamic visual
  // effect.
  Box {
    var delay = ANIMATION_INIT_DELAY
    SwipingText(
      text = firstLineText,
      style = homePageTitleStyle,
      color = titleColor,
      animationDelay = delay,
      animationDurationMs = TITLE_FIRST_LINE_ANIMATION_DURATION,
    )
    delay += (TITLE_FIRST_LINE_ANIMATION_DURATION * 0.3).toLong()
    SwipingText(
      text = firstLineText,
      style = homePageTitleStyle,
      color = MaterialTheme.colorScheme.onSurface,
      animationDelay = delay,
      animationDurationMs = TITLE_FIRST_LINE_ANIMATION_DURATION,
    )
  }
  // Second line text "Edge Gallery" and its animation.
  //
  // The initial animation is the same as the first line text. Right before it is done, the final
  // text with a gradient is revealed.
  Box {
    var delay = TITLE_SECOND_LINE_ANIMATION_START
    SwipingText(
      text = secondLineText,
      style = homePageTitleStyle,
      color = titleColor,
      modifier = Modifier.offset(y = (-16).dp),
      animationDelay = delay,
      animationDurationMs = TITLE_SECOND_LINE_ANIMATION_DURATION,
    )
    delay += (TITLE_SECOND_LINE_ANIMATION_DURATION * 0.3).toInt()
    SwipingText(
      text = secondLineText,
      style = homePageTitleStyle,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.offset(y = (-16).dp),
      animationDelay = delay,
      animationDurationMs = TITLE_SECOND_LINE_ANIMATION_DURATION,
    )
    delay += (TITLE_SECOND_LINE_ANIMATION_DURATION * 0.6).toInt()
    RevealingText(
      text = secondLineText,
      style =
        homePageTitleStyle.copy(
          brush = linearGradient(colors = MaterialTheme.customColors.appTitleGradientColors)
        ),
      modifier = Modifier.offset(y = (-16).dp),
      animationDelay = delay,
      animationDurationMs = TITLE_SECOND_LINE_ANIMATION_DURATION2,
    )
  }
}

/**
 * A composable that animates text appearing to "swipe" into view from left to right.
 *
 * This effect is created by animating a linear gradient brush that colors the text, combined with
 * an alpha animation for fading. The text gradually becomes visible as the gradient moves across
 * it, revealing the full text by the end of the animation.
 */
@Composable
private fun SwipingText(
  text: String,
  style: TextStyle,
  color: Color,
  modifier: Modifier = Modifier,
  animationDelay: Long = 0,
  animationDurationMs: Int = 300,
  edgeGradientRelativeSize: Float = 1.0f,
) {
  val progress =
    rememberDelayedAnimationProgress(
      initialDelay = animationDelay,
      animationDurationMs = animationDurationMs,
      animationLabel = "swiping text",
      easing = LinearEasing,
    )
  Text(
    text,
    style =
      style.copy(
        brush =
          linearGradient(
            colorStops =
              arrayOf(
                (1f + edgeGradientRelativeSize) * progress - edgeGradientRelativeSize to color,
                (1f + edgeGradientRelativeSize) * progress to Color.Transparent,
              )
          )
      ),
    modifier = modifier.graphicsLayer { alpha = progress },
  )
}

/**
 * A composable that animates the revelation of text using a linear gradient mask.
 *
 * The text appears to "wipe" into view from left to right, controlled by an animation progress.
 * This is achieved by drawing a gradient mask over the text that moves horizontally, revealing the
 * content as the animation progresses.
 *
 * The core of the revelation effect relies on `BlendMode.DstOut`. First, the text content
 * (`drawContent()`) is rendered as the "destination." Then, a rectangle filled with a `maskBrush`
 * (our linear gradient) is drawn as the "source." `DstOut` works by taking the destination (the
 * text) and making transparent any parts that overlap with the opaque (non-transparent) regions of
 * the source (the red part of our mask). As the `maskBrush` animates and slides across the text,
 * the transparent portion of the mask "reveals" the text, creating the wipe-in effect.
 */
@Composable
private fun RevealingText(
  text: String,
  style: TextStyle,
  modifier: Modifier = Modifier,
  animationDelay: Long = 0,
  animationDurationMs: Int = 300,
  edgeGradientRelativeSize: Float = 0.5f,
) {
  val progress =
    rememberDelayedAnimationProgress(
      initialDelay = animationDelay,
      animationDurationMs = animationDurationMs,
      animationLabel = "revealing text",
    )
  val maskBrush =
    linearGradient(
      colorStops =
        arrayOf(
          (1f + edgeGradientRelativeSize) * progress - edgeGradientRelativeSize to
            Color.Transparent,
          (1f + edgeGradientRelativeSize) * progress to Color.Red,
        )
    )
  Text(
    text,
    style = style,
    modifier =
      modifier
        .graphicsLayer(alpha = 0.99f, compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
          drawContent()
          drawRect(brush = maskBrush, blendMode = BlendMode.DstOut)
        },
  )
}

@Composable
private fun IntroText() {
  val url = "https://huggingface.co/litert-community"
  val linkColor = MaterialTheme.customColors.linkColor
  val uriHandler = LocalUriHandler.current

  // Intro text animation:
  //
  // fade in + slide up.
  val progress =
    rememberDelayedAnimationProgress(
      initialDelay = TITLE_SECOND_LINE_ANIMATION_START,
      animationDurationMs = CONTENT_COMPOSABLES_ANIMATION_DURATION,
      animationLabel = "intro text animation",
    )

  val introText = buildAnnotatedString {
    append("${stringResource(R.string.app_intro)} ")
    // TODO: Consolidate the link clicking logic into ui/common/ClickableLink.kt.
    withLink(
      link =
        LinkAnnotation.Url(
          url = url,
          styles =
            TextLinkStyles(
              style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            ),
          linkInteractionListener = { _ ->
            firebaseAnalytics?.logEvent("resource_link_click", bundleOf("link_destination" to url))
            uriHandler.openUri(url)
          },
        )
    ) {
      append(stringResource(R.string.litert_community_label))
    }
  }
  Text(
    introText,
    style = MaterialTheme.typography.bodyMedium,
    modifier =
      Modifier.graphicsLayer {
        alpha = progress
        translationY = (CONTENT_COMPOSABLES_OFFSET_Y.dp * (1 - progress)).toPx()
      },
  )
}

@Composable
private fun TaskList(tasks: List<Task>, navigateToTaskScreen: (Task) -> Unit) {
  // Model list animation:
  //
  // 1.  Slide Up: The entire column of task cards translates upwards,
  // 2.  Fade in one by one: The task card fade in one by one. See TaskCard for details.
  val progress =
    rememberDelayedAnimationProgress(
      initialDelay = TASK_LIST_ANIMATION_START,
      animationDurationMs = CONTENT_COMPOSABLES_ANIMATION_DURATION,
      animationLabel = "task card animation",
    )
  Column(
    modifier =
      Modifier.fillMaxWidth().padding(16.dp).graphicsLayer {
        translationY = (CONTENT_COMPOSABLES_OFFSET_Y.dp * (1 - progress)).toPx()
      },
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    var index = 0
    for (task in tasks) {
      TaskCard(
        task = task,
        index = index,
        onClick = { navigateToTaskScreen(task) },
        modifier = Modifier.fillMaxWidth(),
      )
      index++
    }
  }
}

@Composable
private fun TaskCard(task: Task, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
  // Observes the model count and updates the model count label with a fade-in/fade-out animation
  // whenever the count changes.
  val modelCount by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.size
      } else {
        0
      }
    }
  }
  val modelCountLabel by remember {
    derivedStateOf {
      when (modelCount) {
        1 -> "1 Model"
        else -> "%d Models".format(modelCount)
      }
    }
  }
  var curModelCountLabel by remember { mutableStateOf("") }
  var modelCountLabelVisible by remember { mutableStateOf(true) }

  LaunchedEffect(modelCountLabel) {
    if (curModelCountLabel.isEmpty()) {
      curModelCountLabel = modelCountLabel
    } else {
      modelCountLabelVisible = false
      delay(TASK_COUNT_ANIMATION_DURATION.toLong())
      curModelCountLabel = modelCountLabel
      modelCountLabelVisible = true
    }
  }

  // Task card animation:
  //
  // This animation makes the task cards appear with a delayed fade-in effect. Each card will become
  // visible sequentially, starting after an initial delay and then with an additional offset for
  // subsequent cards.
  val progress =
    rememberDelayedAnimationProgress(
      initialDelay = TASK_LIST_ANIMATION_START + index * TASK_CARD_ANIMATION_DELAY_OFFSET,
      animationDurationMs = TASK_CARD_ANIMATION_DURATION,
      animationLabel = "task card animation",
    )

  Card(
    modifier =
      modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).graphicsLayer {
        alpha = progress
      },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.customColors.taskCardBgColor),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      // Title and model count
      Column {
        Text(
          task.type.label,
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          curModelCountLabel,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      // Icon.
      TaskIcon(task = task, width = 40.dp)
    }
  }
}

/**
 * A reusable Composable function that provides an animated float progress value after an initial
 * delay.
 *
 * This function is ideal for creating "enter" animations that start after a specified pause,
 * allowing for staggered or timed visual effects. It uses `animateFloatAsState` to smoothly
 * transition the progress from 0f to 1f.
 */
@Composable
private fun rememberDelayedAnimationProgress(
  initialDelay: Long = 0,
  animationDurationMs: Int,
  animationLabel: String,
  easing: Easing = FastOutSlowInEasing,
): Float {
  var startAnimation by remember { mutableStateOf(false) }
  val progress: Float by
    animateFloatAsState(
      if (startAnimation) 1f else 0f,
      label = animationLabel,
      animationSpec = tween(durationMillis = animationDurationMs, easing = easing),
    )
  LaunchedEffect(Unit) {
    delay(initialDelay)
    startAnimation = true
  }
  return progress
}

// Helper function to get the file name from a URI
fun getFileName(context: Context, uri: Uri): String? {
  if (uri.scheme == "content") {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1) {
          return cursor.getString(nameIndex)
        }
      }
    }
  } else if (uri.scheme == "file") {
    return uri.lastPathSegment
  }
  return null
}

// @Preview
// @Composable
// fun HomeScreenPreview() {
//   GalleryTheme {
//     HomeScreen(
//       modelManagerViewModel = PreviewModelManagerViewModel(context = LocalContext.current),
//       navigateToTaskScreen = {},
//     )
//   }
// }
