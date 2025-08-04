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

package com.google.ai.edge.gallery.ui.common.chat

// import com.google.ai.edge.gallery.ui.preview.PreviewChatModel
// import com.google.ai.edge.gallery.ui.preview.PreviewModelManagerViewModel
// import com.google.ai.edge.gallery.ui.preview.TASK_TEST1
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.modelmanager.PagerScrollState
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AGChatView"

/**
 * A composable that displays a chat interface, allowing users to interact with different models
 * associated with a given task.
 *
 * This composable provides a horizontal pager for switching between models, a model selector for
 * configuring the selected model, and a chat panel for sending and receiving messages. It also
 * manages model initialization, cleanup, and download status, and handles navigation and system
 * back gestures.
 */
@Composable
fun ChatView(
  task: Task,
  viewModel: ChatViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  onSendMessage: (Model, List<ChatMessage>) -> Unit,
  onRunAgainClicked: (Model, ChatMessage) -> Unit,
  onBenchmarkClicked: (Model, ChatMessage, Int, Int) -> Unit,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  onResetSessionClicked: (Model) -> Unit = {},
  onStreamImageMessage: (Model, ChatMessageImage) -> Unit = { _, _ -> },
  onStopButtonClicked: (Model) -> Unit = {},
  chatInputType: ChatInputType = ChatInputType.TEXT,
  showStopButtonInInputWhenInProgress: Boolean = false,
) {
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
  var showImageViewer by remember { mutableStateOf(false) }

  val pagerState =
    rememberPagerState(
      initialPage = task.models.indexOf(selectedModel),
      pageCount = { task.models.size },
    )
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var navigatingUp by remember { mutableStateOf(false) }

  val handleNavigateUp = {
    navigatingUp = true
    navigateUp()

    // clean up all models.
    scope.launch(Dispatchers.Default) {
      for (model in task.models) {
        modelManagerViewModel.cleanupModel(task = task, model = model)
      }
    }
  }

  // Initialize model when model/download state changes.
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp) {
      if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
        Log.d(TAG, "Initializing model '${selectedModel.name}' from ChatView launched effect")
        modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
      }
    }
  }

  // Update selected model and clean up previous model when page is settled on a model page.
  LaunchedEffect(pagerState.settledPage) {
    val curSelectedModel = task.models[pagerState.settledPage]
    Log.d(
      TAG,
      "Pager settled on model '${curSelectedModel.name}' from '${selectedModel.name}'. Updating selected model.",
    )
    if (curSelectedModel.name != selectedModel.name) {
      modelManagerViewModel.cleanupModel(task = task, model = selectedModel)
    }
    modelManagerViewModel.selectModel(curSelectedModel)
  }

  LaunchedEffect(pagerState) {
    // Collect from the a snapshotFlow reading the currentPage
    snapshotFlow { pagerState.currentPage }.collect { page -> Log.d(TAG, "Page changed to $page") }
  }

  // Trigger scroll sync.
  LaunchedEffect(pagerState) {
    snapshotFlow {
        PagerScrollState(
          page = pagerState.currentPage,
          offset = pagerState.currentPageOffsetFraction,
        )
      }
      .collect { scrollState -> modelManagerViewModel.pagerScrollState.value = scrollState }
  }

  // Handle system's edge swipe.
  BackHandler { handleNavigateUp() }

  Scaffold(
    modifier = modifier,
    topBar = {
      ModelPageAppBar(
        task = task,
        model = selectedModel,
        modelManagerViewModel = modelManagerViewModel,
        canShowResetSessionButton = true,
        isResettingSession = uiState.isResettingSession,
        inProgress = uiState.inProgress,
        modelPreparing = uiState.preparing,
        onResetSessionClicked = onResetSessionClicked,
        onConfigChanged = { old, new ->
          viewModel.addConfigChangedMessage(
            oldConfigValues = old,
            newConfigValues = new,
            model = selectedModel,
          )
        },
        onBackClicked = { handleNavigateUp() },
        onModelSelected = { model ->
          scope.launch { pagerState.animateScrollToPage(task.models.indexOf(model)) }
        },
      )
    },
  ) { innerPadding ->
    Box {
      // A horizontal scrollable pager to switch between models.
      HorizontalPager(state = pagerState) { pageIndex ->
        val curSelectedModel = task.models[pageIndex]
        val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[curSelectedModel.name]

        // Calculate the alpha of the current page based on how far they are from the center.
        val pageOffset =
          ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
            .absoluteValue
        val curAlpha = 1f - pageOffset.coerceIn(0f, 1f)

        Column(
          modifier =
            Modifier.padding(innerPadding)
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surface)
        ) {
          ModelDownloadStatusInfoPanel(
            model = curSelectedModel,
            task = task,
            modelManagerViewModel = modelManagerViewModel,
          )

          // The main messages panel.
          if (curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
            ChatPanel(
              modelManagerViewModel = modelManagerViewModel,
              task = task,
              selectedModel = curSelectedModel,
              viewModel = viewModel,
              navigateUp = navigateUp,
              onSendMessage = onSendMessage,
              onRunAgainClicked = onRunAgainClicked,
              onBenchmarkClicked = onBenchmarkClicked,
              onStreamImageMessage = onStreamImageMessage,
              onStreamEnd = { averageFps ->
                viewModel.addMessage(
                  model = curSelectedModel,
                  message =
                    ChatMessageInfo(content = "Live camera session ended. Average FPS: $averageFps"),
                )
              },
              onStopButtonClicked = { onStopButtonClicked(curSelectedModel) },
              onImageSelected = { bitmap ->
                selectedImage = bitmap
                showImageViewer = true
              },
              modifier = Modifier.weight(1f).graphicsLayer { alpha = curAlpha },
              chatInputType = chatInputType,
              showStopButtonInInputWhenInProgress = showStopButtonInInputWhenInProgress,
            )
          }
        }
      }

      // Image viewer.
      AnimatedVisibility(
        visible = showImageViewer,
        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
      ) {
        selectedImage?.let { image ->
          ZoomableBox(
            modifier =
              Modifier.fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(Color.Black.copy(alpha = 0.95f))
          ) {
            Image(
              bitmap = image.asImageBitmap(),
              contentDescription = "",
              modifier =
                modifier
                  .fillMaxSize()
                  .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                  ),
              contentScale = ContentScale.Fit,
            )

            // Close button.
            IconButton(
              onClick = { showImageViewer = false },
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
              modifier = Modifier.offset(x = (-8).dp, y = 8.dp),
            ) {
              Icon(
                Icons.Rounded.Close,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}

// @Preview
// @Composable
// fun ChatScreenPreview() {
//   GalleryTheme {
//     val context = LocalContext.current
//     val task = TASK_TEST1
//     ChatView(
//       task = task,
//       viewModel = PreviewChatModel(context = context),
//       modelManagerViewModel = PreviewModelManagerViewModel(context = context),
//       onSendMessage = { _, _ -> },
//       onRunAgainClicked = { _, _ -> },
//       onBenchmarkClicked = { _, _, _, _ -> },
//       navigateUp = {},
//     )
//   }
// }
