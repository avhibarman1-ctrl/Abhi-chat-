package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AiReplyCategory
import com.example.data.model.AiReplyLanguage
import com.example.data.model.ChatMessage
import com.example.data.model.ReplySuggestion
import com.example.data.model.ReplySuggestionsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbhiAiBottomSheet(
    recentMessages: List<ChatMessage>,
    currentUserId: String,
    selectedCategory: AiReplyCategory,
    selectedLanguage: AiReplyLanguage,
    suggestionsState: ReplySuggestionsUiState,
    onSelectCategory: (AiReplyCategory) -> Unit,
    onSelectLanguage: (AiReplyLanguage) -> Unit,
    onRegenerate: (customInstruction: String) -> Unit,
    onEditSuggestion: (suggestionId: String, newText: String) -> Unit,
    onDeleteSuggestion: (suggestionId: String) -> Unit,
    onCopied: (String) -> Unit,
    onSendReply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("abhi_ai_bottom_sheet")
    ) {
        AbhiAiSheetContent(
            recentMessages = recentMessages,
            currentUserId = currentUserId,
            selectedCategory = selectedCategory,
            selectedLanguage = selectedLanguage,
            suggestionsState = suggestionsState,
            onSelectCategory = onSelectCategory,
            onSelectLanguage = onSelectLanguage,
            onRegenerate = onRegenerate,
            onEditSuggestion = onEditSuggestion,
            onDeleteSuggestion = onDeleteSuggestion,
            onCopied = onCopied,
            onSendReply = onSendReply,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun AbhiAiSheetContent(
    recentMessages: List<ChatMessage>,
    currentUserId: String,
    selectedCategory: AiReplyCategory,
    selectedLanguage: AiReplyLanguage,
    suggestionsState: ReplySuggestionsUiState,
    onSelectCategory: (AiReplyCategory) -> Unit,
    onSelectLanguage: (AiReplyLanguage) -> Unit,
    onRegenerate: (customInstruction: String) -> Unit,
    onEditSuggestion: (suggestionId: String, newText: String) -> Unit,
    onDeleteSuggestion: (suggestionId: String) -> Unit,
    onCopied: (String) -> Unit,
    onSendReply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var customReplyText by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingDraft by remember { mutableStateOf("") }

    val lastIncoming = remember(recentMessages, currentUserId) {
        recentMessages.lastOrNull {
            !it.deletedForEveryone && it.senderId != currentUserId && it.text.isNotBlank()
        } ?: recentMessages.lastOrNull { !it.deletedForEveryone && it.text.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ABHI AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Review & approve before sending • Never auto-sends",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { onRegenerate("") },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("abhi_ai_regenerate_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Regenerate", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("abhi_ai_close_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close ABHI AI")
            }
        }

        if (lastIncoming != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Replying to ${lastIncoming.senderName.ifBlank { "Conversation" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = lastIncoming.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 6 Reply Categories
        Text(
            text = "Reply Tone",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("ai_categories_row")
        ) {
            items(AiReplyCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category.label) },
                    modifier = Modifier.testTag("ai_category_${category.name.lowercase()}")
                )
            }
        }

        // Language Support (Bengali, English, Hindi, All)
        Text(
            text = "Language",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("ai_languages_row")
        ) {
            items(AiReplyLanguage.entries) { lang ->
                FilterChip(
                    selected = selectedLanguage == lang,
                    onClick = { onSelectLanguage(lang) },
                    label = { Text(lang.label) },
                    modifier = Modifier.testTag("ai_language_${lang.name.lowercase()}")
                )
            }
        }

        HorizontalDivider()

        // Suggestions list
        when (suggestionsState) {
            is ReplySuggestionsUiState.Idle -> {
                Text(
                    text = "Choose a tone and language to generate 3–5 tailored reply suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            is ReplySuggestionsUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Crafting ${selectedCategory.label.lowercase()} replies in ${selectedLanguage.label}...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is ReplySuggestionsUiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = suggestionsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onRegenerate("") }) {
                            Text("Retry")
                        }
                    }
                }
            }
            is ReplySuggestionsUiState.Success -> {
                if (suggestionsState.suggestions.isEmpty()) {
                    Text(
                        text = "All suggestions removed. Tap Regenerate or write a custom reply below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .testTag("ai_suggestions_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestionsState.suggestions, key = { it.id }) { suggestion ->
                            SuggestionCard(
                                suggestion = suggestion,
                                isEditing = editingId == suggestion.id,
                                editingDraft = editingDraft,
                                onEditingDraftChange = { editingDraft = it },
                                onStartEdit = {
                                    editingId = suggestion.id
                                    editingDraft = suggestion.text
                                },
                                onSaveEdit = {
                                    if (editingDraft.isNotBlank()) {
                                        onEditSuggestion(suggestion.id, editingDraft.trim())
                                    }
                                    editingId = null
                                },
                                onCancelEdit = { editingId = null },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(suggestion.text))
                                    onCopied(suggestion.text)
                                },
                                onDelete = { onDeleteSuggestion(suggestion.id) },
                                onSend = {
                                    val textToSend = if (editingId == suggestion.id && editingDraft.isNotBlank()) {
                                        editingDraft.trim()
                                    } else {
                                        suggestion.text
                                    }
                                    onSendReply(textToSend)
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Custom Reply Composer inside ABHI AI Sheet (manual Send only)
        Text(
            text = "Write Custom Reply",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customReplyText,
                onValueChange = { customReplyText = it },
                placeholder = { Text("Type or refine your custom reply...") },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("abhi_ai_custom_reply_input")
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = customReplyText.trim()
                    if (trimmed.isNotEmpty()) {
                        customReplyText = ""
                        onSendReply(trimmed)
                    }
                },
                enabled = customReplyText.isNotBlank(),
                modifier = Modifier.testTag("abhi_ai_custom_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Custom Reply", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Send")
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ReplySuggestion,
    isEditing: Boolean,
    editingDraft: String,
    onEditingDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_suggestion_card_${suggestion.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${suggestion.category} • ${suggestion.language}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEditing) {
                        IconButton(
                            onClick = onSaveEdit,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("ai_save_edit_${suggestion.id}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save Edit", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onCancelEdit,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Edit", modifier = Modifier.size(18.dp))
                        }
                    } else {
                        IconButton(
                            onClick = onStartEdit,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("ai_edit_${suggestion.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Suggestion", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("ai_copy_${suggestion.id}")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Suggestion", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("ai_delete_${suggestion.id}")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Suggestion",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = editingDraft,
                    onValueChange = onEditingDraftChange,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_edit_input_${suggestion.id}")
                )
            } else {
                Text(
                    text = suggestion.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onSend,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("ai_send_${suggestion.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Send")
                }
            }
        }
    }
}
