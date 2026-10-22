package com.deepanjanxyz.notepad.ui.feature_editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepanjanxyz.notepad.ui.components.ActionTooltip
import com.deepanjanxyz.notepad.ui.theme.AppColors

private const val MAX_LABEL_LENGTH = 30


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorLabelsSheet(
    allUniqueLabels: List<String>,
    currentTags: List<String>,
    onToggleLabel: (String) -> Unit,
    onCreateLabel: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    textPrimary: Color = AppColors.TextPrimary,
    textPlaceholder: Color = AppColors.TextPlaceholder,
    amberAccent: Color = AppColors.AccentAmber,
    modifier: Modifier = Modifier
) {
    var newLabelInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.SheetSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = AppColors.TextDim,
                    shape = CircleShape,
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "SELECT LABELS",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                items(allUniqueLabels) { label ->
                    val isChecked = currentTags.any { it.equals(label, ignoreCase = true) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleLabel(label) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("label_item_$label")
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleLabel(label) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = amberAccent,
                                uncheckedColor = AppColors.TextMuted,
                                checkmarkColor = Color.Black
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = TextStyle(
                                fontSize = 16.sp,
                                color = textPrimary,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }
            }

            val cleanInput = newLabelInput.trim().replace("#", "")
            val isDuplicate = cleanInput.isNotBlank() &&
                allUniqueLabels.any { it.equals(cleanInput, ignoreCase = true) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = newLabelInput,
                    onValueChange = { newLabelInput = it.take(MAX_LABEL_LENGTH) },
                    placeholder = { Text("Create new label", color = textPlaceholder) },
                    isError = isDuplicate,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isDuplicate) {
                                Text(
                                    text = "Label already exists",
                                    color = AppColors.Danger,
                                    style = TextStyle(fontSize = 12.sp)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Text(
                                text = "${newLabelInput.length}/30",
                                style = TextStyle(fontSize = 12.sp),
                                color = if (isDuplicate) AppColors.Danger else AppColors.TextFaint
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = amberAccent,
                        unfocusedBorderColor = AppColors.InputOutline,
                        errorBorderColor = AppColors.Danger,
                        errorTextColor = textPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (cleanInput.isNotBlank() && !isDuplicate) {
                            onCreateLabel(cleanInput)
                            newLabelInput = ""
                        }
                    }),
                    trailingIcon = {
                        if (newLabelInput.isNotBlank()) {
                            ActionTooltip("Create label") {
                                IconButton(
                                    onClick = {
                                        if (cleanInput.isNotBlank() && !isDuplicate) {
                                            onCreateLabel(cleanInput)
                                            newLabelInput = ""
                                        }
                                    },
                                    enabled = !isDuplicate,
                                    modifier = Modifier.testTag("add_new_label_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create label",
                                        tint = if (!isDuplicate) amberAccent else AppColors.TextDim
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_label_input")
                )
            }
        }
    }
}
