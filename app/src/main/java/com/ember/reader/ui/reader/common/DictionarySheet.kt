package com.ember.reader.ui.reader.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ember.reader.core.dictionary.DictionaryRepository
import com.ember.reader.core.dictionary.DictionaryResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(
    word: String,
    dictionaryRepository: DictionaryRepository,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var result by remember { mutableStateOf<DictionaryResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(word) {
        isLoading = true
        error = null
        dictionaryRepository.lookup(word)
            .onSuccess { result = it; isLoading = false }
            .onFailure { error = it.message ?: "Definition not found"; isLoading = false }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 16.dp),
        ) {
            // Word header
            Text(
                text = word.trim(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            result?.phonetic?.let { phonetic ->
                Text(
                    text = phonetic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                error != null -> {
                    Text(
                        text = "No definition found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }

                result != null -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val defs = result!!.definitions
                        itemsIndexed(defs) { index, definition ->
                            Column {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = "${index + 1}. ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Column {
                                        Text(
                                            text = definition.partOfSpeech,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = definition.meaning,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        definition.example?.let { example ->
                                            Text(
                                                text = "\"$example\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontStyle = FontStyle.Italic,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp),
                                            )
                                        }
                                    }
                                }
                                if (index < defs.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(top = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
